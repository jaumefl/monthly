package monthly.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record Transaction(
        LocalDate operationDate,
        String description,
        BigDecimal amount,      // negative = expense, positive = income
        String currency,        // "EUR"
        BankSource source
) {
    public YearMonth month() {
        return YearMonth.from(operationDate);
    }
}