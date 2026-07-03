package monthly.parser

import monthly.domain.BankSource
import spock.lang.Specification

import java.time.LocalDate

class SantanderParserSpec extends Specification {

    def parser = new SantanderParser()

    private InputStream fixture() {
        def stream = getClass().getResourceAsStream('/fixtures/santander_fixture.xlsx')
        assert stream != null : "Test fixture not found on classpath"
        stream
    }

    def "parses all transaction rows, skipping metadata header"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.size() == 7
    }

    def "uses operation date (column A), not value date (column B)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].operationDate() == LocalDate.of(2026, 6, 10)
        txns[1].operationDate() == LocalDate.of(2026, 6, 5)
        txns[2].operationDate() == LocalDate.of(2026, 6, 1)
        txns[3].operationDate() == LocalDate.of(2026, 5, 31)
    }

    def "parses expense (negative amount with Unicode minus U+2212)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[0].amount() == new BigDecimal("-16.16")
    }

    def "parses income (positive amount)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[1].amount() == new BigDecimal("50.00")
    }

    def "parses amounts with thousands separator (period in Spanish locale)"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[2].amount() == new BigDecimal("-3500.00")
    }

    def "parses small decimal-only amount"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[3].amount() == new BigDecimal("-2.50")
    }

    def "preserves description when no noise fragments are present"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[1].description() == 'BIZUM RECEIVED from Friend'
    }

    def "strips TARJETA card-number fragment from description"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[5].description() == 'CARD PAYMENT'
    }

    def "strips COMISION 0,00 fragment from description"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[6].description() == 'RECIBO Something'
    }

    def "strips both TARJETA and COMISION 0,00 when both present"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns[4].description() == 'COMPRA SomeShop, CITY'
    }

    def "sets currency from file for all transactions"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.currency() == "EUR" }
    }

    def "sets source to SANTANDER for all transactions"() {
        when:
        def txns = parser.parse(fixture())

        then:
        txns.every { it.source() == BankSource.SANTANDER }
    }

    def "source() method returns SANTANDER"() {
        expect:
        parser.source() == BankSource.SANTANDER
    }
}
