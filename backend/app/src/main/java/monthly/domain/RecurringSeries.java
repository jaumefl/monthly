package monthly.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * A payment that repeats at a similar amount across multiple months
 * (subscriptions, rent, utilities). Identified by a normalized merchant
 * label + representative amount + source, and carries the distinct months
 * in which it was seen (sorted ascending, de-duplicated).
 */
public record RecurringSeries(
        String label,           // normalized merchant/description key
        BigDecimal amount,      // representative (typical) amount; negative for expenses
        BankSource source,
        List<YearMonth> months  // distinct, ascending
) {
    public RecurringSeries {
        months = months.stream().distinct().sorted().toList();
    }

    /** How many distinct months this series appears in. */
    public int occurrences() {
        return months.size();
    }

    /**
     * True if the series contains a run of at least {@code minRun} consecutive
     * calendar months (e.g. May, Jun, Jul). A gap resets the run, so an
     * every-other-month pattern of the same length does not qualify.
     */
    public boolean hasConsecutiveRun(int minRun) {
        if (minRun <= 0) return true;
        if (months.size() < minRun) return false;
        int run = 1;
        if (run >= minRun) return true;
        for (int i = 1; i < months.size(); i++) {
            run = months.get(i - 1).plusMonths(1).equals(months.get(i)) ? run + 1 : 1;
            if (run >= minRun) return true;
        }
        return false;
    }
}