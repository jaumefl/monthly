package monthly.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthSummary(
        YearMonth month,
        BigDecimal income,    // sum of positive amounts
        BigDecimal expenses,  // sum of negative amounts (stays negative)
        BigDecimal net        // income + expenses
) {
    public static MonthSummary of(YearMonth month, List<Transaction> transactions) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if (tx.amount().signum() < 0) {
                expenses = expenses.add(tx.amount());
            } else {
                income = income.add(tx.amount());
            }
        }
        return new MonthSummary(month, income, expenses, income.add(expenses));
    }
}