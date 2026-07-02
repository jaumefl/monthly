package monthly.domain

import spock.lang.Specification
import java.time.LocalDate
import java.time.YearMonth

class CategoryBreakdownSpec extends Specification {
    static tx(String desc, String amount) {
        new Transaction(LocalDate.of(2026, 6, 10), desc, new BigDecimal(amount), "EUR", BankSource.SANTANDER)
    }
    def "tallies absolute expense spend per category and ignores income"() {
        given:
        def txs = [tx("MERCADONA", "-30.00"), tx("MERCADONA 2", "-20.00"),
                   tx("RESTAURANT", "-15.00"), tx("SALARY", "2000.00")]
        def categoryOf = { Transaction t ->
            t.amount().signum() > 0 ? Category.INCOME
                    : t.description().startsWith("MERCA") ? Category.GROCERIES : Category.EATING_OUT }
        when:
        def b = CategoryBreakdown.of(YearMonth.of(2026, 6), txs, categoryOf)
        then:
        b.byCategory()[Category.GROCERIES]  == new BigDecimal("50.00")
        b.byCategory()[Category.EATING_OUT] == new BigDecimal("15.00")
        !b.byCategory().containsKey(Category.INCOME)
        b.total() == new BigDecimal("65.00")
    }
    def "share is the category's percentage of total spend, one decimal"() {
        given:
        def txs = [tx("A", "-25.00"), tx("B", "-75.00")]
        def categoryOf = { Transaction t -> t.description() == "A" ? Category.TRANSPORT : Category.HOUSING }
        when:
        def b = CategoryBreakdown.of(YearMonth.of(2026, 6), txs, categoryOf)
        then:
        b.share(Category.TRANSPORT) == new BigDecimal("25.0")
        b.share(Category.HOUSING)   == new BigDecimal("75.0")
        b.share(Category.GROCERIES) == BigDecimal.ZERO
    }
    def "an empty month has zero total and no divide-by-zero"() {
        when:
        def b = CategoryBreakdown.of(YearMonth.of(2026, 6), [], { Category.OTHER })
        then:
        b.total() == BigDecimal.ZERO
        b.share(Category.GROCERIES) == BigDecimal.ZERO
    }
}
