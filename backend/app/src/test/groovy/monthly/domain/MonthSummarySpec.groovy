package monthly.domain

import spock.lang.Specification
import java.time.YearMonth

class MonthSummarySpec extends Specification {

    def "of sums positives as income, negatives as expenses, and nets them"() {
        given:
        def month = YearMonth.of(2026, 6)
        def txs = [
                tx(month, "1500.00"),   // income
                tx(month, "-25.50"),    // expense
                tx(month, "-100.00"),   // expense
        ]

        when:
        def summary = MonthSummary.of(month, txs)

        then:
        summary.income()   == new BigDecimal("1500.00")
        summary.expenses() == new BigDecimal("-125.50")
        summary.net()      == new BigDecimal("1374.50")
        summary.month()    == month
    }

    def "of returns zeros for an empty month"() {
        when:
        def summary = MonthSummary.of(YearMonth.of(2026, 6), [])

        then:
        summary.income()   == BigDecimal.ZERO
        summary.expenses() == BigDecimal.ZERO
        summary.net()      == BigDecimal.ZERO
    }

    private static Transaction tx(YearMonth month, String amount) {
        new Transaction(month.atDay(1), "tx", new BigDecimal(amount), "EUR", BankSource.SANTANDER)
    }
}