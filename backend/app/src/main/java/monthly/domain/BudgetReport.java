package monthly.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual spend vs budgeted limit, per category, for one month.
 *  Only categories that have a budget set produce a line. */
public record BudgetReport(YearMonth month, List<Line> lines) {

    public record Line(Category category, BigDecimal limit, BigDecimal spent,
                       BigDecimal remaining, BigDecimal percentUsed, boolean overBudget) {}

    public static BudgetReport of(CategoryBreakdown breakdown, Map<Category, BigDecimal> limits) {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Category, BigDecimal> entry : limits.entrySet()) {
            Category category = entry.getKey();
            BigDecimal limit = entry.getValue();
            BigDecimal spent = breakdown.byCategory().getOrDefault(category, BigDecimal.ZERO);
            BigDecimal remaining = limit.subtract(spent);
            BigDecimal percentUsed = limit.signum() == 0
                    ? BigDecimal.ZERO
                    : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
            boolean overBudget = spent.compareTo(limit) > 0;
            lines.add(new Line(category, limit, spent, remaining, percentUsed, overBudget));
        }
        return new BudgetReport(breakdown.month(), lines);
    }
}