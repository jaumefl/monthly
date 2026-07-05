package monthly.service

import monthly.db.Database
import monthly.domain.*
import monthly.parser.BankStatementParser
import monthly.repository.SqliteCategoryOverrideRepository
import monthly.repository.SqliteTransactionRepository
import monthly.repository.SqliteTransferRepository
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

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        txRepo = new SqliteTransactionRepository(database)
        overrideRepo = new SqliteCategoryOverrideRepository(database)
        transferRepo = new SqliteTransferRepository(database);
        queryService = new TransactionQueryService(txRepo, overrideRepo, new TransactionCategorizer(), transferRepo)
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

    private BankStatementParser parserReturning(List<Transaction> txs) {
        Stub(BankStatementParser) {
            source() >> BankSource.SANTANDER
            parse(_) >> txs
        }
    }
}