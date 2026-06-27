package monthly.parser

import monthly.domain.BankSource
import spock.lang.Specification

import java.time.LocalDate

class RevolutParserSpec extends Specification {

    def parser = new RevolutParser()

    private InputStream fixture() {
        def stream = getClass().getResourceAsStream('/fixtures/revolut_fixture.csv')
        assert stream != null : "Test fixture not found on classpath"
        stream
    }

    def "parses all rows including PENDING"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.size() == 4
    }

    def "uses Started Date (operation date), not Completed Date"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].operationDate() == LocalDate.of(2026, 6, 1)
        txns[1].operationDate() == LocalDate.of(2026, 6, 5)
        txns[2].operationDate() == LocalDate.of(2026, 6, 10)
        txns[3].operationDate() == LocalDate.of(2026, 6, 15)
    }

    def "parses expense (negative amount)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].amount() == new BigDecimal("-25.50")
    }

    def "parses income (positive amount)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[1].amount() == new BigDecimal("50.00")
    }

    def "parses PENDING transaction"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[3].amount() == new BigDecimal("-3.50")
        txns[3].operationDate() == LocalDate.of(2026, 6, 15)
    }

    def "preserves description verbatim"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[1].description() == 'Transfer from Friend A'
    }

    def "sets currency from file for all transactions"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.currency() == "EUR" }
    }

    def "sets source to REVOLUT for all transactions"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.source() == BankSource.REVOLUT }
    }

    def "source() method returns REVOLUT"() {
        expect:
        parser.source() == BankSource.REVOLUT
    }
}
