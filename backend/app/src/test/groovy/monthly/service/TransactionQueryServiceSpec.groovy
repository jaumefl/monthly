package monthly.service

import monthly.db.Database
import monthly.domain.*
import monthly.parser.BankStatementParser
import monthly.repository.SqliteCategoryOverrideRepository
import monthly.repository.SqliteRecurringNameRepository
import monthly.repository.SqliteTransactionRepository
import monthly.repository.SqliteTransferRepository
import monthly.repository.SqliteBudgetRepository
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class TransactionQueryServiceSpec extends Specification {

    Database database
    SqliteTransactionRepository txRepo
    SqliteCategoryOverrideRepository overrideRepo
    TransactionQueryService queryService
    ImportService importService
    SqliteTransferRepository transferRepo
    SqliteBudgetRepository budgetRepo
    SqliteRecurringNameRepository recurringNameRepo

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        txRepo = new SqliteTransactionRepository(database)
        overrideRepo = new SqliteCategoryOverrideRepository(database)
        transferRepo = new SqliteTransferRepository(database)
        budgetRepo = new SqliteBudgetRepository(database)
        recurringNameRepo = new SqliteRecurringNameRepository(database)
        queryService = new TransactionQueryService(txRepo, overrideRepo, new TransactionCategorizer(), transferRepo, budgetRepo, recurringNameRepo)
        importService = new ImportService(txRepo)
    }

    def "a manually set category survives a re-import of the same month"() {
        given: "a transaction the categorizer would leave as OTHER"
        def tx = new Transaction(LocalDate.of(2026, 6, 10), "MYSTERY SHOP 123",
                new BigDecimal("-40.00"), "EUR", BankSource.SANTANDER)
        importService.importStatement(parserReturning([tx]), InputStream.nullInputStream())

        and: "the user overrides it to SHOPPING"
        def fp = queryService.categorizedForMonth(YearMonth.of(2026, 6))[0].fingerprint()
        overrideRepo.set(fp, Category.SHOPPING)

        when: "the same month is imported again (delete + re-insert)"
        importService.importStatement(parserReturning([tx]), InputStream.nullInputStream())

        then: "the override still wins over the auto-categorizer"
        with(queryService.categorizedForMonth(YearMonth.of(2026, 6))[0]) {
            category == Category.SHOPPING
            manual
        }
    }

    def "with no override, the auto-categorizer decides and manual is false"() {
        given:
        def tx = new Transaction(LocalDate.of(2026, 6, 10), "MERCADONA",
                new BigDecimal("-30.00"), "EUR", BankSource.SANTANDER)
        importService.importStatement(parserReturning([tx]), InputStream.nullInputStream())

        expect:
        with(queryService.categorizedForMonth(YearMonth.of(2026, 6))[0]) {
            category == Category.GROCERIES
            !manual
        }
    }

    def "categoryBreakdown honours overrides and excludes income"() {
        given:
        def groceries = new Transaction(LocalDate.of(2026, 6, 5), "MERCADONA", new BigDecimal("-30.00"), "EUR", BankSource.SANTANDER)
        def mystery   = new Transaction(LocalDate.of(2026, 6, 8), "MYSTERY 42", new BigDecimal("-50.00"), "EUR", BankSource.SANTANDER)
        def salary    = new Transaction(LocalDate.of(2026, 6, 1), "NOMINA", new BigDecimal("2000.00"), "EUR", BankSource.SANTANDER)
        importService.importStatement(parserReturning([groceries, mystery, salary]), InputStream.nullInputStream())
        overrideRepo.set(mystery.fingerprint(), Category.SHOPPING)
        when:
        def b = queryService.categoryBreakdown(YearMonth.of(2026, 6))
        then:
        b.byCategory()[Category.GROCERIES] == new BigDecimal("30.00")
        b.byCategory()[Category.SHOPPING]  == new BigDecimal("50.00")
        !b.byCategory().containsKey(Category.INCOME)
        b.total() == new BigDecimal("80.00")
    }
    def "categoryBreakdown excludes transactions flagged as transfers"() {
        given:
        def groceries = new Transaction(LocalDate.of(2026, 6, 5), "MERCADONA", new BigDecimal("-30.00"), "EUR", BankSource.IMAGINBANK)
        def moveOut   = new Transaction(LocalDate.of(2026, 6, 6), "TRASPASO A REVOLUT", new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK)
        importService.importStatement(parserReturning([groceries, moveOut]), InputStream.nullInputStream())
        transferRepo.mark(moveOut.fingerprint())

        when:
        def b = queryService.categoryBreakdown(YearMonth.of(2026, 6))

        then: "the transfer outflow is gone; only real spend remains"
        b.byCategory()[Category.GROCERIES] == new BigDecimal("30.00")
        b.total() == new BigDecimal("30.00")
    }

    def "monthSummary excludes both legs of a flagged transfer pair"() {
        given: "an imagin outflow and the matching Revolut inflow, both tagged"
        def outflow = new Transaction(LocalDate.of(2026, 6, 6), "TRASPASO A REVOLUT", new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK)
        def inflow  = new Transaction(LocalDate.of(2026, 6, 6), "TOP-UP", new BigDecimal("200.00"), "EUR", BankSource.REVOLUT)
        def salary  = new Transaction(LocalDate.of(2026, 6, 1), "NOMINA", new BigDecimal("2000.00"), "EUR", BankSource.IMAGINBANK)
        importService.importStatement(parserReturning([outflow, inflow, salary]), InputStream.nullInputStream())
        transferRepo.mark(outflow.fingerprint())
        transferRepo.mark(inflow.fingerprint())

        when:
        def s = queryService.monthSummary(YearMonth.of(2026, 6))

        then: "neither leg counts; only the salary remains as income"
        s.income()   == new BigDecimal("2000.00")
        s.expenses() == BigDecimal.ZERO
        s.net()      == new BigDecimal("2000.00")
    }
    def "categorizedForMonth flags transactions marked as transfers"() {
        given:
        def spend    = new Transaction(LocalDate.of(2026, 6, 5), "MERCADONA", new BigDecimal("-30.00"), "EUR", BankSource.IMAGINBANK)
        def transfer = new Transaction(LocalDate.of(2026, 6, 6), "TRASPASO A REVOLUT", new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK)
        importService.importStatement(parserReturning([spend, transfer]), InputStream.nullInputStream())
        transferRepo.mark(transfer.fingerprint())

        when:
        def rows = queryService.categorizedForMonth(YearMonth.of(2026, 6))

        then: "both stay in the list, but only the tagged one is flagged"
        rows.size() == 2
        rows.find { it.description() == "MERCADONA" }.transfer() == false
        rows.find { it.description() == "TRASPASO A REVOLUT" }.transfer() == true
    }
    def "budgetReport pairs each configured limit with the month's spend"() {
        given:
        def groceries = new Transaction(LocalDate.of(2026, 6, 5),  "MERCADONA",   new BigDecimal("-120.00"), "EUR", BankSource.SANTANDER)
        def more      = new Transaction(LocalDate.of(2026, 6, 9),  "MERCADONA 2", new BigDecimal("-80.00"),  "EUR", BankSource.SANTANDER)
        def eatingOut = new Transaction(LocalDate.of(2026, 6, 12), "RESTAURANTE", new BigDecimal("-60.00"),  "EUR", BankSource.SANTANDER)
        importService.importStatement(parserReturning([groceries, more, eatingOut]), InputStream.nullInputStream())
        budgetRepo.set(Category.GROCERIES, new BigDecimal("250.00"))
        budgetRepo.set(Category.EATING_OUT, new BigDecimal("50.00"))

        when:
        def report = queryService.budgetReport(YearMonth.of(2026, 6))

        then:
        report.lines().size() == 2
        with(report.lines().find { it.category() == Category.GROCERIES }) {
            spent()     == new BigDecimal("200.00")
            remaining() == new BigDecimal("50.00")
            !overBudget()
        }
        with(report.lines().find { it.category() == Category.EATING_OUT }) {
            spent()     == new BigDecimal("60.00")
            remaining() == new BigDecimal("-10.00")
            overBudget()
        }
    }

    def "monthCsv reflects overrides, transfer flags, and the summary"() {
        given:
        def groceries = new Transaction(LocalDate.of(2026, 6, 5), "MERCADONA", new BigDecimal("-30.00"), "EUR", BankSource.SANTANDER)
        def mystery   = new Transaction(LocalDate.of(2026, 6, 8), "MYSTERY 42", new BigDecimal("-50.00"), "EUR", BankSource.SANTANDER)
        def salary    = new Transaction(LocalDate.of(2026, 6, 1), "NOMINA", new BigDecimal("2000.00"), "EUR", BankSource.SANTANDER)
        importService.importStatement(parserReturning([groceries, mystery, salary]), InputStream.nullInputStream())
        overrideRepo.set(mystery.fingerprint(), Category.SHOPPING)
        transferRepo.mark(salary.fingerprint())

        when:
        def csv = queryService.monthCsv(YearMonth.of(2026, 6))

        then: "override wins, salary is flagged as a transfer, and the summary excludes it"
        csv.startsWith("Date,Description,Category,Source,Amount,Currency,Transfer,Manual")
        csv.contains("MYSTERY 42,SHOPPING,SANTANDER,-50.00,EUR,no,yes")
        csv.contains("NOMINA,INCOME,SANTANDER,2000.00,EUR,yes,no")
        csv.contains("Net,-80.00")
    }

    def "recurring detects a subscription repeating across consecutive months"() {
        given:
        txRepo.saveAll([
                new Transaction(LocalDate.of(2026, 5, 3), "NETFLIX 8821", new BigDecimal("-9.99"), "EUR", BankSource.REVOLUT),
                new Transaction(LocalDate.of(2026, 6, 3), "NETFLIX 8821", new BigDecimal("-9.99"), "EUR", BankSource.REVOLUT),
                new Transaction(LocalDate.of(2026, 7, 3), "NETFLIX 8821", new BigDecimal("-9.99"), "EUR", BankSource.REVOLUT),
                new Transaction(LocalDate.of(2026, 6, 10), "MERCADONA",   new BigDecimal("-42.10"), "EUR", BankSource.SANTANDER),
        ])

        when:
        def series = queryService.recurring()

        then:
        series.size() == 1
        series[0].label() == "netflix"
        series[0].name() == "netflix"          // no custom name → falls back to label
        series[0].months().size() == 3
    }

    def "recurring ignores transactions flagged as transfers"() {
        given:
        def moveMay = new Transaction(LocalDate.of(2026, 5, 1), "TRASPASO A REVOLUT", new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK)
        def moveJun = new Transaction(LocalDate.of(2026, 6, 1), "TRASPASO A REVOLUT", new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK)
        txRepo.saveAll([moveMay, moveJun])
        transferRepo.mark(moveMay.fingerprint())
        transferRepo.mark(moveJun.fingerprint())

        expect:
        queryService.recurring().isEmpty()
    }

    def "recurring applies a saved custom name"() {
        given:
        txRepo.saveAll([
                new Transaction(LocalDate.of(2026, 5, 3), "COMPRA APPLE.COM/BILL CORK", new BigDecimal("-10.99"), "EUR", BankSource.REVOLUT),
                new Transaction(LocalDate.of(2026, 6, 3), "COMPRA APPLE.COM/BILL CORK", new BigDecimal("-10.99"), "EUR", BankSource.REVOLUT),
        ])
        def key = queryService.recurring()[0].key()
        recurringNameRepo.set(key, "Apple Music")

        when:
        def series = queryService.recurring()

        then:
        series.size() == 1
        series[0].label() == "compra apple com bill cork"
        series[0].name() == "Apple Music"
    }
    private BankStatementParser parserReturning(List<Transaction> txs) {
        Stub(BankStatementParser) {
            source() >> BankSource.SANTANDER
            parse(_) >> txs
        }
    }
}