package monthly.service;

import monthly.api.CategorizedTransaction;
import monthly.domain.Category;
import monthly.domain.CategoryBreakdown;
import monthly.domain.Transaction;
import monthly.domain.TransactionCategorizer;
import monthly.repository.CategoryOverrideRepository;
import monthly.repository.TransactionRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public class TransactionQueryService {

    private final TransactionRepository transactions;
    private final CategoryOverrideRepository overrides;
    private final TransactionCategorizer categorizer;

    public TransactionQueryService(TransactionRepository transactions,
                                   CategoryOverrideRepository overrides,
                                   TransactionCategorizer categorizer) {
        this.transactions = transactions;
        this.overrides = overrides;
        this.categorizer = categorizer;
    }

    public CategoryBreakdown categoryBreakdown(YearMonth month) {
        Map<String, Category> overrideMap = overrides.findAll();
        return CategoryBreakdown.of(month, transactions.findByMonth(month),
                tx -> overrideMap.getOrDefault(tx.fingerprint(), categorizer.categorize(tx)));
    }

    public List<CategorizedTransaction> categorizedForMonth(YearMonth month) {
        Map<String, Category> overrideMap = overrides.findAll();
        return transactions.findByMonth(month).stream()
                .map(tx -> {
                    String fp = tx.fingerprint();
                    Category effective = overrideMap.getOrDefault(fp, categorizer.categorize(tx));
                    return CategorizedTransaction.of(tx, effective, fp, overrideMap.containsKey(fp));
                })
                .toList();
    }
}