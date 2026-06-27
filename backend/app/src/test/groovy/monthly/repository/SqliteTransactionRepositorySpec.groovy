package monthly.repository

import monthly.db.Database
import monthly.domain.BankSource
import monthly.domain.Transaction
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SqliteTransactionRepositorySpec extends Specification {

    Database database
    SqliteTransactionRepository repository

    def setup() {
        database = Database.inMemory()
        database.createSchema()
        repository = new SqliteTransactionRepository(database)
    }

    def "saveAll persists all transactions to the database"() {
        given:
        def transactions = [
                new Transaction(LocalDate.of(2026, 6, 1), "Supermarket", new BigDecimal("-25.50"), "EUR", BankSource.SANTANDER),
                new Transaction(LocalDate.of(2026, 6, 5), "Salary", new BigDecimal("1500.00"), "EUR", BankSource.SANTANDER),
        ]

        when:
        repository.saveAll(transactions)

        then:
        rowCount() == 2
    }

    def "deleteBySourceAndMonth removes only transactions matching source and month"() {
        given:
        repository.saveAll([
                new Transaction(LocalDate.of(2026, 6, 1), "June tx", new BigDecimal("-10.00"), "EUR", BankSource.SANTANDER),
                new Transaction(LocalDate.of(2026, 7, 1), "July tx", new BigDecimal("-20.00"), "EUR", BankSource.SANTANDER),
        ])

        when:
        repository.deleteBySourceAndMonth(BankSource.SANTANDER, YearMonth.of(2026, 6))

        then:
        rowCount() == 1
    }

    def "deleteBySourceAndMonth does not affect other banks"() {
        given:
        repository.saveAll([
                new Transaction(LocalDate.of(2026, 6, 1), "Santander tx", new BigDecimal("-10.00"), "EUR", BankSource.SANTANDER),
                new Transaction(LocalDate.of(2026, 6, 1), "Revolut tx",   new BigDecimal("-20.00"), "EUR", BankSource.REVOLUT),
        ])

        when:
        repository.deleteBySourceAndMonth(BankSource.SANTANDER, YearMonth.of(2026, 6))

        then:
        rowCount() == 1
    }

    def "replace strategy: re-importing same month replaces previous data"() {
        given: "an initial import"
        repository.saveAll([
                new Transaction(LocalDate.of(2026, 6, 1), "Old tx", new BigDecimal("-10.00"), "EUR", BankSource.SANTANDER)
        ])

        when: "the same month is re-imported"
        repository.deleteBySourceAndMonth(BankSource.SANTANDER, YearMonth.of(2026, 6))
        repository.saveAll([
                new Transaction(LocalDate.of(2026, 6, 1), "New tx A", new BigDecimal("-20.00"), "EUR", BankSource.SANTANDER),
                new Transaction(LocalDate.of(2026, 6, 2), "New tx B", new BigDecimal("-30.00"), "EUR", BankSource.SANTANDER),
        ])

        then:
        rowCount() == 2
    }

    private int rowCount() {
        def rs = database.connection().createStatement().executeQuery("SELECT COUNT(*) FROM transactions")
        rs.getInt(1)
    }
}