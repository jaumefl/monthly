package monthly.domain

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class TransactionCategorizerSpec extends Specification {

    def categorizer = new TransactionCategorizer()

    @Unroll
    def "categorizes '#description' as #expected"() {
        given:
        def tx = new Transaction(LocalDate.of(2026, 6, 10), description,
                new BigDecimal("-20.00"), "EUR", BankSource.SANTANDER)

        expect:
        categorizer.categorize(tx) == expected

        where:
        description              | expected
        "Compra en MERCADONA"    | Category.GROCERIES
        "RESTAURANTE La Plaza"   | Category.EATING_OUT
        "UBER trip to airport"   | Category.TRANSPORT
        "Pago alquiler junio"    | Category.HOUSING
        "IBERDROLA factura luz"  | Category.UTILITIES
        "AMAZON.ES compra"       | Category.SHOPPING
        "FARMACIA Centro"        | Category.HEALTH
        "Cargo desconocido xyz"  | Category.OTHER
        "Trading 212 deposit"    | Category.INVESTMENT
        "APPLE.COM/BILL"         | Category.SUBSCRIPTION
        "ANTHROPIC monthly fee"  | Category.SUBSCRIPTION
    }

    def "any positive amount is income regardless of description"() {
        given:
        def tx = new Transaction(LocalDate.of(2026, 6, 10), "MERCADONA refund",
                new BigDecimal("15.00"), "EUR", BankSource.SANTANDER)

        expect:
        categorizer.categorize(tx) == Category.INCOME
    }

    def "does not match a keyword buried inside another word"() {
        given:
        def tx = new Transaction(LocalDate.of(2026, 6, 10), "TRANSFERENCIA a Juan",
                new BigDecimal("-50.00"), "EUR", BankSource.SANTANDER)

        expect:
        categorizer.categorize(tx) == Category.OTHER
    }

    def "matches NS as a standalone word for transport"() {
        given:
        def tx = new Transaction(LocalDate.of(2026, 6, 10), "NS GROEP OV-CHIPKAART",
                new BigDecimal("-15.00"), "EUR", BankSource.SANTANDER)

        expect:
        categorizer.categorize(tx) == Category.TRANSPORT
    }
}