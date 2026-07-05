package monthly.service;

import monthly.api.CategorizedTransaction;
import monthly.domain.*;
import monthly.repository.CategoryOverrideRepository;
import monthly.repository.TransactionRepository;
import monthly.repository.TransferRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransactionQueryService {

    private final TransactionRepository transactions;
    private final CategoryOverrideRepository overrides;
    private final TransactionCategorizer categorizer;
    private final TransferRepository transfers;

    public TransactionQueryService(TransactionRepository transactions,
                                   CategoryOverrideRepository overrides,
                                   TransactionCategorizer categorizer,
                                   TransferRepository transfers) {
        this.transactions = transactions;
        this.overrides = overrides;
        this.transfers = transfers;
        this.categorizer = categorizer;
    }

    public CategoryBreakdown categoryBreakdown(YearMonth month) {
        Map<String, Category> overrideMap = overrides.findAll();
        return CategoryBreakdown.of(month, visibleTransactions(month),
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

    /** All transactions for the month except those manually flagged as transfers. */
    private List<Transaction> visibleTransactions(YearMonth month) {
        Set<String> transferFps = transfers.findAll();
        return transactions.findByMonth(month).stream()
                .filter(tx -> !transferFps.contains(tx.fingerprint()))
                .toList();
    }



    public MonthSummary monthSummary(YearMonth month) {
        return MonthSummary.of(month, visibleTransactions(month));
    }
}