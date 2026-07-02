package monthly.api

import monthly.domain.Category
import monthly.domain.CategoryBreakdown
import spock.lang.Specification
import java.time.YearMonth

class MonthComparisonSpec extends Specification {
    static breakdown(YearMonth ym, Map<Category, String> spend) {
        def map = new LinkedHashMap<Category, BigDecimal>()
        def total = BigDecimal.ZERO
        spend.each { k, v -> map[k] = new BigDecimal(v); total += new BigDecimal(v) }
        new CategoryBreakdown(ym, map, total)
    }
    def "pairs categories across both months with a delta, sorted by this month's spend"() {
        given:
        def month = breakdown(YearMonth.of(2026, 7), [(Category.GROCERIES): "300.00", (Category.TRANSPORT): "100.00"])
        def base  = breakdown(YearMonth.of(2026, 6), [(Category.GROCERIES): "200.00", (Category.HOUSING): "800.00"])
        when:
        def c = MonthComparison.of(month, base)
        then:
        c.rows()*.category() == [Category.GROCERIES, Category.TRANSPORT, Category.HOUSING]
        with(c.rows().find { it.category() == Category.GROCERIES }) {
            amount == new BigDecimal("300.00"); baselineAmount == new BigDecimal("200.00"); delta == new BigDecimal("100.00")
        }
        with(c.rows().find { it.category() == Category.HOUSING }) {
            amount == BigDecimal.ZERO; baselineAmount == new BigDecimal("800.00"); delta == new BigDecimal("-800.00")
        }
    }
}
