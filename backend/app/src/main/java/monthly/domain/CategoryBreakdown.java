package monthly.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Absolute expense spend per category for one month (income ignored). */
public record CategoryBreakdown(YearMonth month, Map<Category, BigDecimal> byCategory, BigDecimal total) {
    public static CategoryBreakdown of(YearMonth month, List<Transaction> transactions,
                                       Function<Transaction, Category> categoryOf) {
        Map<Category, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if (tx.amount().signum() >= 0) continue;
            BigDecimal abs = tx.amount().abs();
            totals.merge(categoryOf.apply(tx), abs, BigDecimal::add);
            total = total.add(abs);
        }
        return new CategoryBreakdown(month, totals, total);
    }
    public BigDecimal share(Category category) {
        BigDecimal amount = byCategory.getOrDefault(category, BigDecimal.ZERO);
        if (total.signum() == 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
