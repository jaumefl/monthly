package monthly.api;

import monthly.domain.Category;
import monthly.domain.CategoryBreakdown;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Side-by-side category comparison of a month vs a baseline month. */
public record MonthComparison(YearMonth month, YearMonth baseline,
                              BigDecimal monthTotal, BigDecimal baselineTotal, List<Row> rows) {
    public record Row(Category category, BigDecimal amount, BigDecimal amountPct,
                      BigDecimal baselineAmount, BigDecimal baselinePct, BigDecimal delta) {}

    public static MonthComparison of(CategoryBreakdown month, CategoryBreakdown baseline) {
        Set<Category> categories = new LinkedHashSet<>();
        categories.addAll(month.byCategory().keySet());
        categories.addAll(baseline.byCategory().keySet());
        List<Row> rows = new ArrayList<>();
        for (Category c : categories) {
            BigDecimal amount = month.byCategory().getOrDefault(c, BigDecimal.ZERO);
            BigDecimal base   = baseline.byCategory().getOrDefault(c, BigDecimal.ZERO);
            rows.add(new Row(c, amount, month.share(c), base, baseline.share(c), amount.subtract(base)));
        }
        rows.sort(Comparator.comparing(Row::amount, Comparator.reverseOrder())
                .thenComparing(Row::baselineAmount, Comparator.reverseOrder()));
        return new MonthComparison(month.month(), baseline.month(), month.total(), baseline.total(), rows);
    }
}
