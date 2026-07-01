package monthly.domain

import spock.lang.Specification
import spock.lang.Unroll
import java.math.BigDecimal
import java.time.LocalDate

class TransactionSpec extends Specification {

    def "fingerprint is stable for two equal transactions"() {
        expect:
        tx("MERCADONA", "-25.50", BankSource.SANTANDER).fingerprint() ==
                tx("MERCADONA", "-25.50", BankSource.SANTANDER).fingerprint()
    }

    def "fingerprint ignores case and extra whitespace in the description"() {
        expect:
        tx("Albert  Heijn", "-5.00", BankSource.REVOLUT).fingerprint() ==
                tx("albert heijn",  "-5.00", BankSource.REVOLUT).fingerprint()
    }

    @Unroll
    def "fingerprint differs when #field differs"() {
        expect:
        base.fingerprint() != other.fingerprint()
        where:
        field         | other
        "amount"      | new Transaction(LocalDate.of(2026,6,1), "x", new BigDecimal("-2.00"), "EUR", BankSource.SANTANDER)
        "date"        | new Transaction(LocalDate.of(2026,6,2), "x", new BigDecimal("-1.00"), "EUR", BankSource.SANTANDER)
        "description" | new Transaction(LocalDate.of(2026,6,1), "y", new BigDecimal("-1.00"), "EUR", BankSource.SANTANDER)
        "source"      | new Transaction(LocalDate.of(2026,6,1), "x", new BigDecimal("-1.00"), "EUR", BankSource.REVOLUT)
        base = new Transaction(LocalDate.of(2026,6,1), "x", new BigDecimal("-1.00"), "EUR", BankSource.SANTANDER)
    }

    private static Transaction tx(String desc, String amount, BankSource source) {
        new Transaction(LocalDate.of(2026,6,1), desc, new BigDecimal(amount), "EUR", source)
    }
}