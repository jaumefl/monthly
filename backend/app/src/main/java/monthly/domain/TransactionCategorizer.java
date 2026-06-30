package monthly.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TransactionCategorizer {

    // Keyword -> Category. Each keyword is compiled to a word-boundary regex,
    // so it matches only as a whole word — "ns" matches "NS GROEP" but NOT the
    // "ns" inside "traNSferencia". First match wins; order matters (LinkedHashMap),
    // so list more specific keywords before more generic ones.
    private static final Map<Pattern, Category> RULES = new LinkedHashMap<>();
    static {
        addAll(Category.GROCERIES, "mercadona", "carrefour", "jumbo", "spar","food", "maas", "albert heijn", "albertheijn", "kruidvat", "lidl", "aldi", "supermercado", "grocery");
        addAll(Category.EATING_OUT, "restaurante", "restaurant", "cafe", "mcdonald", "burger","kfc", "glovo", "uber eats", "just eat");
        addAll(Category.TRANSPORT, "uber", "cabify", "renfe", "metro", "gasolina", "repsol", "ovpay", "cepsa", "parking", "ns", "ov-chipkaart");
        addAll(Category.HOUSING, "alquiler", "rent", "hipoteca", "mortgage", "comunidad");
        addAll(Category.UTILITIES, "iberdrola", "endesa", "naturgy", "vodafone", "movistar", "orange", "internet");
        addAll(Category.HEALTH, "farmacia", "pharmacy", "clinica", "hospital", "dentista");
        addAll(Category.INVESTMENT, "trading 212", "trading212", "trade republic", "degiro", "indexa", "myinvestor", "etoro", "scalable");
        addAll(Category.SHOPPING, "amazon", "zara", "corte ingles", "decathlon", "aliexpress");
        addAll(Category.SUBSCRIPTION, "apple.com/bill", "anthropic", "claude", "openai", "chatgpt", "netflix", "spotify", "disney", "hbo", "adobe", "notion", "github", "dropbox", "icloud", "microsoft", "youtube premium", "patreon", "audible");
    }

    private static void addAll(Category category, String... keywords) {
        for (String keyword : keywords) {
            // \b...\b = word boundary on each side; Pattern.quote escapes any regex
            // metacharacters in the keyword (e.g. the "-" in "ov-chipkaart").
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword.toLowerCase()) + "\\b");
            RULES.put(pattern, category);
        }
    }

    public Category categorize(Transaction tx) {
        if (tx.amount().signum() >= 0) {
            return Category.INCOME;
        }
        String description = tx.description() == null ? "" : tx.description().toLowerCase();
        for (Map.Entry<Pattern, Category> rule : RULES.entrySet()) {
            if (rule.getKey().matcher(description).find()) {
                return rule.getValue();
            }
        }
        return Category.OTHER;
    }
}