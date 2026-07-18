package monthly.api;

import monthly.domain.BankSource;
import monthly.domain.RecurringSeries;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * A recurring series as shown on the dashboard: carries a stable {@code key}
 * and a resolved display {@code name} — the user's custom name if set,
 * otherwise the auto-detected label.
 */
public record RecurringView(String key, String label, String name,
                            BigDecimal amount, BankSource source, List<YearMonth> months) {

    public static RecurringView of(RecurringSeries s, Map<String, String> names) {
        String custom = names.get(s.key());
        return new RecurringView(s.key(), s.label(),
                custom != null ? custom : s.label(),
                s.amount(), s.source(), s.months());
    }
}