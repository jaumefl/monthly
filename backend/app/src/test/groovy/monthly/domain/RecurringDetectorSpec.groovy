package monthly.domain

import spock.lang.Specification
import java.time.LocalDate

class RecurringDetectorSpec extends Specification {

    static tx(int year, int month, int day, String desc, String amount, BankSource source = BankSource.REVOLUT) {
        new Transaction(LocalDate.of(year, month, day), desc, new BigDecimal(amount), "EUR", source)
    }

    def detector = new RecurringDetector()

    def "flags a subscription that repeats across consecutive months"() {
        given:
        def txs = [
                tx(2026, 5, 3, "NETFLIX 88213", "-9.99"),
                tx(2026, 6, 3, "NETFLIX 88213", "-9.99"),
                tx(2026, 7, 3, "NETFLIX 88213", "-9.99"),
        ]
        when:
        def series = detector.detect(txs)
        then:
        series.size() == 1
        series[0].label() == "netflix"
        series[0].occurrences() == 3
        series[0].source() == BankSource.REVOLUT
    }

    def "ignores a one-off expense"() {
        expect:
        detector.detect([tx(2026, 6, 10, "MERCADONA", "-42.10")]).isEmpty()
    }

    def "ignores income even if it repeats"() {
        given:
        def txs = [
                tx(2026, 5, 1, "SALARY ACME", "2000.00"),
                tx(2026, 6, 1, "SALARY ACME", "2000.00"),
        ]
        expect:
        detector.detect(txs).isEmpty()
    }

    def "does not flag two months that are not consecutive"() {
        given:
        def txs = [
                tx(2026, 5, 15, "GYM PASS", "-30.00"),
                tx(2026, 7, 15, "GYM PASS", "-30.00"),
        ]
        expect:
        detector.detect(txs).isEmpty()
    }

    def "groups similar amounts together via rounding"() {
        given:
        def txs = [
                tx(2026, 5, 2, "SPOTIFY", "-9.99"),
                tx(2026, 6, 2, "SPOTIFY", "-10.01"),   // same rounded bucket (10)
                tx(2026, 7, 2, "SPOTIFY", "-9.99"),
        ]
        when:
        def series = detector.detect(txs)
        then:
        series.size() == 1
        series[0].occurrences() == 3
        series[0].amount() == new BigDecimal("-10.00")   // average, 2dp
    }

    def "matches merchant despite varying reference digits"() {
        given:
        def txs = [
                tx(2026, 5, 28, "COMUNIDAD REF 4471", "-85.00"),
                tx(2026, 6, 28, "COMUNIDAD REF 5522", "-85.00"),
        ]
        when:
        def series = detector.detect(txs)
        then:
        series.size() == 1
        series[0].label() == "comunidad ref"
        series[0].occurrences() == 2
    }
}