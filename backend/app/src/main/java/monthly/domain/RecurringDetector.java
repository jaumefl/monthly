package monthly.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-pass recurring-payment detector. Groups expenses that share a merchant
 * label, source and a similar amount, then keeps the groups that repeat across
 * consecutive months. Income is ignored; the focus is subscriptions, rent and
 * utilities. Amount similarity is approximated by rounding to the nearest whole
 * unit, and cadence requires a run of >= MIN_CONSECUTIVE consecutive months.
 */
public final class RecurringDetector {

    /** Minimum run of consecutive months for a group to count as recurring. */
    public static final int MIN_CONSECUTIVE = 2;

    public List<RecurringSeries> detect(List<Transaction> transactions) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            if (tx.amount().signum() >= 0) continue;          // expenses only
            String label = normalize(tx.description());
            if (label.isEmpty()) continue;
            BigDecimal bucket = tx.amount().setScale(0, RoundingMode.HALF_UP);
            String key = tx.source().name() + '|' + label + '|' + bucket.toPlainString();
            groups.computeIfAbsent(key, k -> new Group(label, tx.source()))
                    .add(tx.month(), tx.amount());
        }
        List<RecurringSeries> result = new ArrayList<>();
        for (Group g : groups.values()) {
            RecurringSeries series = g.toSeries();
            if (series.hasConsecutiveRun(MIN_CONSECUTIVE)) {
                result.add(series);
            }
        }
        return result;
    }

    /**
     * Reduces a raw bank description to a stable merchant label: lower-cased,
     * with digits and punctuation stripped and whitespace collapsed, so that
     * "NETFLIX 88213" and "Netflix*" land in the same group.
     */
    static String normalize(String description) {
        if (description == null) return "";
        return description.toLowerCase()
                .replaceAll("[0-9]+", " ")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Mutable accumulator; averages amounts to derive a representative value. */
    private static final class Group {
        final String label;
        final BankSource source;
        final List<YearMonth> months = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        Group(String label, BankSource source) {
            this.label = label;
            this.source = source;
        }

        void add(YearMonth month, BigDecimal amount) {
            months.add(month);
            total = total.add(amount);
            count++;
        }

        RecurringSeries toSeries() {
            BigDecimal avg = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            return new RecurringSeries(label, avg, source, months);
        }
    }
}