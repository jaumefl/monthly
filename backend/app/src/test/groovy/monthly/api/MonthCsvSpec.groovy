package monthly.api

import monthly.domain.BankSource
import monthly.domain.Category
import monthly.domain.MonthSummary
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MonthCsvSpec extends Specification {

    def "renders a header, one row per transaction, and a summary footer"() {
        given:
        def rows = [
                new CategorizedTransaction(LocalDate.of(2026, 6, 5), "MERCADONA",
                        new BigDecimal("-30.00"), "EUR", BankSource.SANTANDER,
                        Category.GROCERIES, "fp1", false, false),
        ]
        def summary = new MonthSummary(YearMonth.of(2026, 6),
                new BigDecimal("2000.00"), new BigDecimal("-30.00"), new BigDecimal("1970.00"))

        when:
        def csv = MonthCsv.render(rows, summary)
        def lines = csv.split("\r\n", -1)

        then:
        lines[0] == "Date,Description,Category,Source,Amount,Currency,Transfer,Manual"
        lines[1] == "2026-06-05,MERCADONA,GROCERIES,SANTANDER,-30.00,EUR,no,no"
        csv.contains("Income,2000.00")
        csv.contains("Expenses,-30.00")
        csv.contains("Net,1970.00")
    }

    def "quotes fields that contain commas or quotes"() {
        given:
        def rows = [
                new CategorizedTransaction(LocalDate.of(2026, 6, 7), 'AMAZON, ES "PRIME"',
                        new BigDecimal("-12.99"), "EUR", BankSource.REVOLUT,
                        Category.SHOPPING, "fp2", false, false),
        ]
        def summary = new MonthSummary(YearMonth.of(2026, 6),
                BigDecimal.ZERO, new BigDecimal("-12.99"), new BigDecimal("-12.99"))

        expect:
        MonthCsv.render(rows, summary).contains('"AMAZON, ES ""PRIME"""')
    }

    def "surfaces the transfer and manual flags per row"() {
        given:
        def rows = [
                new CategorizedTransaction(LocalDate.of(2026, 6, 6), "TRASPASO A REVOLUT",
                        new BigDecimal("-200.00"), "EUR", BankSource.IMAGINBANK,
                        Category.OTHER, "fp3", true, true),
        ]
        def summary = new MonthSummary(YearMonth.of(2026, 6),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

        when:
        def line = MonthCsv.render(rows, summary).split("\r\n")[1]

        then:
        line.endsWith("OTHER,IMAGINBANK,-200.00,EUR,yes,yes")
    }
}