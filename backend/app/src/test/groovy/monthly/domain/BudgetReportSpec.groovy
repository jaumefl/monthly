package monthly.domain

import spock.lang.Specification
import java.time.YearMonth

class BudgetReportSpec extends Specification {

    def ym = YearMonth.of(2026, 7)

    def "produces a line per budgeted category with spent, remaining and percent used"() {
        given:
        def breakdown = new CategoryBreakdown(ym,
                [(Category.GROCERIES): new BigDecimal("320.00"),
                 (Category.TRANSPORT): new BigDecimal("40.00")] as Map,
                new BigDecimal("360.00"))
        def limits = [(Category.GROCERIES): new BigDecimal("400.00"),
                      (Category.TRANSPORT): new BigDecimal("50.00")]

        when:
        def report = BudgetReport.of(breakdown, limits)

        then:
        report.month() == ym
        report.lines().size() == 2
        with(report.lines().find { it.category() == Category.GROCERIES }) {
            limit()       == new BigDecimal("400.00")
            spent()       == new BigDecimal("320.00")
            remaining()   == new BigDecimal("80.00")
            percentUsed() == new BigDecimal("80.0")
            !overBudget()
        }
    }

    def "spent is zero for a budgeted category with no spending"() {
        given:
        def breakdown = new CategoryBreakdown(ym, [:] as Map, BigDecimal.ZERO)
        def limits = [(Category.HEALTH): new BigDecimal("100.00")]

        when:
        def line = BudgetReport.of(breakdown, limits).lines().first()

        then:
        line.spent()       == BigDecimal.ZERO
        line.remaining()   == new BigDecimal("100.00")
        line.percentUsed() == new BigDecimal("0.0")
        !line.overBudget()
    }

    def "flags a category as over budget with negative remaining"() {
        given:
        def breakdown = new CategoryBreakdown(ym,
                [(Category.EATING_OUT): new BigDecimal("260.00")] as Map,
                new BigDecimal("260.00"))
        def limits = [(Category.EATING_OUT): new BigDecimal("200.00")]

        when:
        def line = BudgetReport.of(breakdown, limits).lines().first()

        then:
        line.overBudget()
        line.remaining()   == new BigDecimal("-60.00")
        line.percentUsed() == new BigDecimal("130.0")
    }

    def "ignores spend in categories that have no budget"() {
        given:
        def breakdown = new CategoryBreakdown(ym,
                [(Category.GROCERIES): new BigDecimal("320.00"),
                 (Category.SHOPPING):  new BigDecimal("500.00")] as Map,
                new BigDecimal("820.00"))
        def limits = [(Category.GROCERIES): new BigDecimal("400.00")]

        when:
        def report = BudgetReport.of(breakdown, limits)

        then:
        report.lines().size() == 1
        report.lines().first().category() == Category.GROCERIES
    }
    def "spend exactly at the limit is not over budget"() {
        given:
        def breakdown = new CategoryBreakdown(ym,
                [(Category.GROCERIES): new BigDecimal("400.00")] as Map,
                new BigDecimal("400.00"))
        def limits = [(Category.GROCERIES): new BigDecimal("400.00")]

        when:
        def line = BudgetReport.of(breakdown, limits).lines().first()

        then:
        !line.overBudget()
        line.remaining()   == new BigDecimal("0.00")
        line.percentUsed() == new BigDecimal("100.0")
    }

    def "a zero limit with spend is over budget without dividing by zero"() {
        given:
        def breakdown = new CategoryBreakdown(ym,
                [(Category.SHOPPING): new BigDecimal("25.00")] as Map,
                new BigDecimal("25.00"))
        def limits = [(Category.SHOPPING): new BigDecimal("0.00")]

        when:
        def line = BudgetReport.of(breakdown, limits).lines().first()

        then:
        line.overBudget()
        line.remaining()   == new BigDecimal("-25.00")
        line.percentUsed() == BigDecimal.ZERO
    }
}