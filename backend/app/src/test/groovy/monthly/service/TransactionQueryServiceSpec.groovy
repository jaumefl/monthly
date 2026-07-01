package monthly.service

import monthly.db.Database
import monthly.domain.*
import monthly.parser.BankStatementParser
import monthly.repository.SqliteCategoryOverrideRepository
import monthly.repository.SqliteTransactionRepository
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

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        txRepo = new SqliteTransactionRepository(database)
        overrideRepo = new SqliteCategoryOverrideRepository(database)
        queryService = new TransactionQueryService(txRepo, overrideRepo, new TransactionCategorizer())
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

    private BankStatementParser parserReturning(List<Transaction> txs) {
        Stub(BankStatementParser) {
            source() >> BankSource.SANTANDER
            parse(_) >> txs
        }
    }
}