package monthly.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mines the manual category overrides for gaps in the keyword map.
 *
 * <p>An override row is a recorded disagreement: the user looked at a
 * transaction and corrected whatever {@link TransactionCategorizer} decided.
 * The subset that matters here is OTHER.
 */
public final class KeywordSuggester {

    /** A merchant must be corrected this many times before it is worth a rule. */
    public static final int MIN_OCCURRENCES = 2;

    private final TransactionCategorizer categorizer = new TransactionCategorizer();

    public List<KeywordSuggestion> suggest(List<Transaction> transactions,
                                           Map<String, Category> overrides) {
        Map<String, Tally> tallies = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            if (tx.amount().signum() >= 0) continue;               // expenses only

            Category assigned = overrides.get(tx.fingerprint());
            if (assigned == null || assigned == Category.OTHER) continue;
            if (categorizer.categorize(tx) != Category.OTHER) continue;

            String merchant = RecurringDetector.normalize(tx.description());
            if (merchant.isEmpty()) continue;

            String key = merchant + '|' + assigned.name();
            tallies.computeIfAbsent(key, k -> new Tally(merchant, assigned)).count++;
        }

        List<KeywordSuggestion> result = new ArrayList<>();
        for (Tally tally : tallies.values()) {
            if (tally.count >= MIN_OCCURRENCES) {
                result.add(new KeywordSuggestion(tally.merchant, tally.category, tally.count));
            }
        }
        result.sort(Comparator
                .comparingInt(KeywordSuggestion::occurrences).reversed()
                .thenComparing(KeywordSuggestion::merchant));
        return result;
    }

    /** Mutable accumulator for one (merchant, category) pair. */
    private static final class Tally {
        final String merchant;
        final Category category;
        int count;

        Tally(String merchant, Category category) {
            this.merchant = merchant;
            this.category = category;
        }
    }
}