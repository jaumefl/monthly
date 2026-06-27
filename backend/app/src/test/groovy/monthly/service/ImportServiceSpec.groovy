package monthly.service

import monthly.domain.BankSource
import monthly.domain.Transaction
import monthly.parser.BankStatementParser
import monthly.repository.TransactionRepository
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class ImportServiceSpec extends Specification {

    def repository = Mock(TransactionRepository)
    def parser     = Mock(BankStatementParser)
    def service    = new ImportService(repository)

    def "single-month statement: deletes then saves for that month"() {
        given:
        parser.source() >> BankSource.SANTANDER
        parser.parse(_) >> [
                new Transaction(LocalDate.of(2026, 6, 1), "Supermarket", new BigDecimal("-25.50"), "EUR", BankSource.SANTANDER),
                new Transaction(LocalDate.of(2026, 6, 5), "Salary",      new BigDecimal("1500.00"), "EUR", BankSource.SANTANDER),
        ]

        when:
        service.importStatement(parser, InputStream.nullInputStream())

        then:
        1 * repository.deleteBySourceAndMonth(BankSource.SANTANDER, YearMonth.of(2026, 6))
        1 * repository.saveAll({ it.size() == 2 })
    }

    def "multi-month statement: each month is replaced independently"() {
        given:
        parser.source() >> BankSource.REVOLUT
        parser.parse(_) >> [
                new Transaction(LocalDate.of(2026, 5, 31), "May tx",  new BigDecimal("-10.00"), "EUR", BankSource.REVOLUT),
                new Transaction(LocalDate.of(2026, 6, 1),  "June tx", new BigDecimal("-20.00"), "EUR", BankSource.REVOLUT),
        ]

        when:
        service.importStatement(parser, InputStream.nullInputStream())

        then:
        1 * repository.deleteBySourceAndMonth(BankSource.REVOLUT, YearMonth.of(2026, 5))
        1 * repository.saveAll({ it.size() == 1 && it[0].description() == "May tx" })
        1 * repository.deleteBySourceAndMonth(BankSource.REVOLUT, YearMonth.of(2026, 6))
        1 * repository.saveAll({ it.size() == 1 && it[0].description() == "June tx" })
    }
}