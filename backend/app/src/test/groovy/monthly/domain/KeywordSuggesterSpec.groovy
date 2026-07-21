package monthly.domain

import spock.lang.Specification

import java.time.LocalDate
import java.time.YearMonth

class KeywordSuggesterSpec extends Specification {

    def suggester = new KeywordSuggester()

    private static Transaction tx(String description, String date = "2026-06-10",
                                  String amount = "-12.99") {
        new Transaction(LocalDate.parse(date), description,
                new BigDecimal(amount), "EUR", BankSource.SANTANDER)
    }

    def "suggests a merchant that was manually re-categorized out of OTHER"() {
        given:
        def june = tx("MOLLIE FITNESS 4412", "2026-06-03")
        def july = tx("MOLLIE FITNESS 5518", "2026-07-03")
        def overrides = [(june.fingerprint()): Category.HEALTH,
                         (july.fingerprint()): Category.HEALTH]

        when:
        def suggestions = suggester.suggest([june, july], overrides)

        then:
        suggestions.size() == 1
        suggestions[0].merchant() == "mollie fitness"
        suggestions[0].category() == Category.HEALTH
        suggestions[0].monthCount() == 2
        suggestions[0].months() == [YearMonth.of(2026, 6), YearMonth.of(2026, 7)]
    }

    def "ignores a merchant the keyword map already classifies"() {
        given:
        def one = tx("Compra en MERCADONA 22", "2026-06-03")
        def two = tx("Compra en MERCADONA 31", "2026-07-03")
        def overrides = [(one.fingerprint()): Category.EATING_OUT,
                         (two.fingerprint()): Category.EATING_OUT]

        expect: "already GROCERIES by keyword, so it is not a gap in the map"
        suggester.suggest([one, two], overrides).isEmpty()
    }

    def "ignores transactions with no override at all"() {
        expect:
        suggester.suggest([tx("CARGO DESCONOCIDO XYZ")], [:]).isEmpty()
    }

    def "requires corrections in at least #MIN distinct months"() {
        given:
        def once = tx("ODD MERCHANT SL")
        def overrides = [(once.fingerprint()): Category.SHOPPING]

        expect:
        suggester.suggest([once], overrides).isEmpty()

        where:
        MIN = KeywordSuggester.MIN_MONTHS
    }

    def "splits one merchant into separate suggestions per overridden category"() {
        given:
        def a = tx("PAYPAL *SOMESHOP", "2026-05-03")
        def b = tx("PAYPAL *SOMESHOP", "2026-06-03")
        def c = tx("PAYPAL *SOMESHOP", "2026-07-03")
        def overrides = [(a.fingerprint()): Category.SHOPPING,
                         (b.fingerprint()): Category.SHOPPING,
                         (c.fingerprint()): Category.SUBSCRIPTION]

        when:
        def suggestions = suggester.suggest([a, b, c], overrides)

        then: "only the category that clears the threshold survives"
        suggestions.size() == 1
        suggestions[0].category() == Category.SHOPPING
        suggestions[0].monthCount() == 2
    }

    def "orders suggestions by month count, most frequent first"() {
        given:
        def gym = [tx("GYMBOX", "2026-05-03"), tx("GYMBOX", "2026-06-03")]
        def toll = [tx("TOLL ROAD AB", "2026-05-04"), tx("TOLL ROAD AB", "2026-06-04"),
                    tx("TOLL ROAD AB", "2026-07-04")]
        def overrides = [:]
        gym.each { overrides[it.fingerprint()] = Category.HEALTH }
        toll.each { overrides[it.fingerprint()] = Category.TRANSPORT }

        when:
        def suggestions = suggester.suggest(gym + toll, overrides)

        then:
        suggestions*.merchant() == ["toll road ab", "gymbox"]
    }

    def "ignores income, which is never keyword-categorized"() {
        given:
        def a = tx("NOMINA EMPRESA SL", "2026-06-25", "2100.00")
        def b = tx("NOMINA EMPRESA SL", "2026-07-25", "2100.00")
        def overrides = [(a.fingerprint()): Category.OTHER,
                         (b.fingerprint()): Category.OTHER]

        expect:
        suggester.suggest([a, b], overrides).isEmpty()
    }
    def "two corrections in the same month are not a recurring merchant"() {
        given: "one trip, two visits, same month"
        def first  = tx("COMPRA ALIPAY SHAKE SHACK", "2026-06-14")
        def second = tx("COMPRA ALIPAY SHAKE SHACK", "2026-06-17")
        def overrides = [(first.fingerprint()): Category.EATING_OUT,
                         (second.fingerprint()): Category.EATING_OUT]

        expect:
        suggester.suggest([first, second], overrides).isEmpty()
    }
}