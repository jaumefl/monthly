package monthly.parser

import monthly.domain.BankSource
import spock.lang.Specification

import java.time.LocalDate

class ImaginParserSpec extends Specification {

    def parser = new ImaginParser()

    private InputStream fixture() {
        def stream = getClass().getResourceAsStream('/fixtures/imagin_fixture.csv')
        assert stream != null : "Test fixture not found on classpath"
        stream
    }

    def "parses every transaction row, skipping the header and trailing ';;;' row"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.size() == 4
    }

    def "parses the operation date from d-M-yyyy (day is first, no leading zeros)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].operationDate() == LocalDate.of(2026, 7, 1)
        txns[2].operationDate() == LocalDate.of(2026, 7, 15)   // day 15 proves D-M order
    }

    def "parses an expense (negative) with a thousands separator"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[2].amount() == new BigDecimal("-1200.50")
    }

    def "parses income (positive amount)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[1].amount() == new BigDecimal("1850.00")
    }

    def "preserves description verbatim and ignores the card column"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].description() == 'Compra MERCADONA'
    }

    def "reads the currency from the amount suffix for every transaction"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.currency() == "EUR" }
    }

    def "sets source to IMAGINBANK for every transaction"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.source() == BankSource.IMAGINBANK }
    }

    def "source() returns IMAGINBANK"() {
        expect:
        parser.source() == BankSource.IMAGINBANK
    }
}