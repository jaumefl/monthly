package monthly.domain;

import java.time.YearMonth;
import java.util.List;

/**
 * A merchant the keyword map keeps dropping into OTHER, together with the
 * category the user assigned by hand and the distinct months in which they
 * had to do it.
 *
 *
 * @param merchant normalized merchant label (see RecurringDetector#normalize)
 * @param category the category the user chose when overriding
 * @param months   distinct months containing a correction, ascending
 */
public record KeywordSuggestion(String merchant, Category category, List<YearMonth> months) {

    public int monthCount() {
        return months.size();
    }
}