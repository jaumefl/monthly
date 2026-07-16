package monthly.domain

import spock.lang.Specification
import java.time.YearMonth

class RecurringSeriesSpec extends Specification {

    static ym(int m) { YearMonth.of(2026, m) }

    static series(List<YearMonth> months) {
        new RecurringSeries("netflix", new BigDecimal("-9.99"), BankSource.REVOLUT, months)
    }

    def "months are stored distinct and in ascending order"() {
        when:
        def s = series([ym(7), ym(5), ym(7), ym(6)])
        then:
        s.months() == [ym(5), ym(6), ym(7)]
        s.occurrences() == 3
    }

    def "hasConsecutiveRun detects a run of consecutive months"() {
        expect:
        series(months).hasConsecutiveRun(min) == expected
        where:
        months                       | min || expected
        [ym(5), ym(6), ym(7)]        | 2   || true
        [ym(5), ym(6), ym(7)]        | 3   || true
        [ym(5), ym(7)]               | 2   || false   // gap: nothing consecutive
        [ym(5), ym(6), ym(8), ym(9)] | 3   || false   // longest run is only 2
        [ym(5), ym(6), ym(8), ym(9)] | 2   || true
        [ym(5)]                      | 1   || true
        [ym(5)]                      | 2   || false
    }
}