package monthly.domain;

/**
 * A merchant the keyword map keeps dropping into OTHER, together with the
 * category the user assigned by hand and how often it happened. Each one is a
 * candidate keyword rule for {@link TransactionCategorizer}.
 *
 * @param merchant    normalized merchant label (see RecurringDetector#normalize)
 * @param category    the category the user chose when overriding
 * @param occurrences how many overridden transactions back this suggestion
 */
public record KeywordSuggestion(String merchant, Category category, int occurrences) {
}