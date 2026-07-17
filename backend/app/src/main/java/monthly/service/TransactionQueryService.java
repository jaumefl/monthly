package monthly.service;

import monthly.api.CategorizedTransaction;
import monthly.api.MonthCsv;
import monthly.domain.*;
import monthly.domain.BudgetReport;
import monthly.repository.BudgetRepository;
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
    private final BudgetRepository budgets;
    private final RecurringDetector recurringDetector = new RecurringDetector();

    public TransactionQueryService(TransactionRepository transactions,
                                   CategoryOverrideRepository overrides,
                                   TransactionCategorizer categorizer,
                                   TransferRepository transfers,
                                   BudgetRepository budgets) {
        this.transactions = transactions;
        this.overrides = overrides;
        this.transfers = transfers;
        this.categorizer = categorizer;
        this.budgets = budgets;
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



    public MonthSummary monthSummary(YearMonth month) {
        return MonthSummary.of(month, visibleTransactions(month));
    }

    public String monthCsv(YearMonth month) {
        return MonthCsv.render(categorizedForMonth(month), monthSummary(month));
    }

    /** Every recurring payment series detected across the full history,
     *  excluding transactions the user flagged as transfers. */
    public List<RecurringSeries> recurring() {
        Set<String> transferFps = transfers.findAll();
        List<Transaction> visible = transactions.findAll().stream()
                .filter(tx -> !transferFps.contains(tx.fingerprint()))
                .toList();
        return recurringDetector.detect(visible);
    }
}