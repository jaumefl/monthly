package monthly.service;

import monthly.api.CategorizedTransaction;
import monthly.api.MonthCsv;
import monthly.api.RecurringView;
import monthly.domain.*;
import monthly.domain.BudgetReport;
import monthly.repository.BudgetRepository;
import monthly.repository.CategoryOverrideRepository;
import monthly.repository.TransactionRepository;
import monthly.repository.TransferRepository;
import monthly.repository.RecurringNameRepository;


import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransactionQueryService {

    private final TransactionRepository transactions;
    private final CategoryOverrideRepository overrides;
    private final TransactionCategorizer categorizer;
    private final TransferRepository transfers;
    private final BudgetRepository budgets;
    private final RecurringNameRepository recurringNames;
    private final RecurringDetector recurringDetector = new RecurringDetector();
    private final KeywordSuggester keywordSuggester = new KeywordSuggester();

    public TransactionQueryService(TransactionRepository transactions,
                                   CategoryOverrideRepository overrides,
                                   TransactionCategorizer categorizer,
                                   TransferRepository transfers,
                                   BudgetRepository budgets,
                                   RecurringNameRepository recurringNames) {
        this.transactions = transactions;
        this.overrides = overrides;
        this.transfers = transfers;
        this.categorizer = categorizer;
        this.budgets = budgets;
        this.recurringNames = recurringNames;
    }

    public CategoryBreakdown categoryBreakdown(YearMonth month) {
        Map<String, Category> overrideMap = overrides.findAll();
        return CategoryBreakdown.of(month, visibleTransactions(month),
                tx -> overrideMap.getOrDefault(tx.fingerprint(), categorizer.categorize(tx)));
    }

    public BudgetReport budgetReport(YearMonth month) {
        return BudgetReport.of(categoryBreakdown(month), budgets.findAll());
    }

    public List<CategorizedTransaction> categorizedForMonth(YearMonth month) {
        Map<String, Category> overrideMap = overrides.findAll();
        Set<String> transferFps = transfers.findAll();
        return transactions.findByMonth(month).stream()
                .map(tx -> {
                    String fp = tx.fingerprint();
                    Category effective = overrideMap.getOrDefault(fp, categorizer.categorize(tx));
                    return CategorizedTransaction.of(tx, effective, fp, overrideMap.containsKey(fp),
                            transferFps.contains(fp));
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

    /** Every transaction on record except those manually flagged as transfers. */
    private List<Transaction> visibleHistory() {
        Set<String> transferFps = transfers.findAll();
        return transactions.findAll().stream()
                .filter(tx -> !transferFps.contains(tx.fingerprint()))
                .toList();
    }



    public MonthSummary monthSummary(YearMonth month) {
        return MonthSummary.of(month, visibleTransactions(month));
    }

    public String monthCsv(YearMonth month) {
        return MonthCsv.render(categorizedForMonth(month), monthSummary(month));
    }

    /** Every recurring payment series detected across the full history,
     *  excluding transactions the user flagged as transfers, with any saved
     *  custom name resolved. */
    public List<RecurringView> recurring() {
        Map<String, String> names = recurringNames.findAll();
        return recurringDetector.detect(visibleHistory()).stream()
                .map(s -> RecurringView.of(s, names))
                .toList();
    }

    /** Merchants the keyword map keeps missing, ranked by how often you have
     *  corrected them by hand. Transfers are excluded — moving money between
     *  your own accounts is not a merchant worth a keyword rule. */
    public List<KeywordSuggestion> keywordSuggestions() {
        return keywordSuggester.suggest(visibleHistory(), overrides.findAll());
    }


}