package monthly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import monthly.api.CategoryRequest;
import monthly.api.MonthComparison;
import monthly.api.BudgetRequest;
import monthly.api.RecurringNameRequest;
import monthly.db.Database;
import monthly.domain.BankSource;
import monthly.domain.Category;
import monthly.domain.TransactionCategorizer;
import monthly.parser.BankStatementParser;
import monthly.parser.RevolutParser;
import monthly.parser.SantanderParser;
import monthly.parser.ImaginParser;
import monthly.repository.CategoryOverrideRepository;
import monthly.repository.SqliteCategoryOverrideRepository;
import monthly.repository.SqliteTransactionRepository;
import monthly.repository.TransactionRepository;
import monthly.repository.SqliteTransferRepository;
import monthly.repository.TransferRepository;
import monthly.repository.SqliteBudgetRepository;
import monthly.repository.BudgetRepository;
import monthly.repository.RecurringNameRepository;
import monthly.repository.RecurringDismissalRepository;
import monthly.repository.SqliteRecurringDismissalRepository;
import monthly.repository.SqliteRecurringNameRepository;
import monthly.service.ImportService;
import monthly.service.TransactionQueryService;
import spark.Service;

import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

public class App {

    private final Service http;
    private final int requestedPort;

    private final ImportService importService;
    private final TransactionQueryService queryService;
    private final CategoryOverrideRepository overrideRepo;
    private final TransferRepository transferRepo;
    private final BudgetRepository budgetRepo;
    private final ObjectMapper json;
    private final RecurringNameRepository recurringNameRepo;
    private final RecurringDismissalRepository recurringDismissalRepo;

    public App(Database database, int port) {
        database.createSchema();

        TransactionRepository repository = new SqliteTransactionRepository(database);
        this.overrideRepo = new SqliteCategoryOverrideRepository(database);
        this.transferRepo = new SqliteTransferRepository(database);
        this.recurringDismissalRepo = new SqliteRecurringDismissalRepository(database);
        this.importService = new ImportService(repository);
        TransactionCategorizer categorizer = new TransactionCategorizer();
        this.budgetRepo = new SqliteBudgetRepository(database);
        this.recurringNameRepo = new SqliteRecurringNameRepository(database);
        this.queryService = new TransactionQueryService(repository, overrideRepo, categorizer, transferRepo, budgetRepo, recurringNameRepo, recurringDismissalRepo);
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.requestedPort = port;
        this.http = Service.ignite();

    }

    /** Starts the server and blocks until it is ready to accept requests. */
    public App start() {
        http.port(requestedPort);
        http.staticFiles.location("/public");   // serves src/main/resources/public at "/"
        registerRoutes();
        http.awaitInitialization();
        return this;
    }

    /** The actual bound port — meaningful when constructed with port 0 (tests). */
    public int port() {
        return http.port();
    }

    public void stop() {
        http.stop();
        http.awaitStop();
    }

    private void registerRoutes() {
        http.get("/api/months/:yearMonth", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return queryService.categorizedForMonth(month);
        }, json::writeValueAsString);

        http.get("/api/months/:yearMonth/summary", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return queryService.monthSummary(month);
        }, json::writeValueAsString);

        http.get("/api/months/:yearMonth/export.csv", (req, res) -> {
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            String csv = queryService.monthCsv(month);
            res.type("text/csv");
            res.header("Content-Disposition", "attachment; filename=\"monthly-" + month + ".csv\"");
            return csv;
        });

        http.get("/api/comparison", (req, res) -> {
            res.type("application/json");
            String monthParam = req.queryParams("month");
            if (monthParam == null) throw new IllegalArgumentException("Query parameter 'month' is required (YYYY-MM)");
            YearMonth month = YearMonth.parse(monthParam);
            String baselineParam = req.queryParams("baseline");
            YearMonth baseline = baselineParam != null ? YearMonth.parse(baselineParam) : month.minusMonths(1);
            return MonthComparison.of(queryService.categoryBreakdown(month), queryService.categoryBreakdown(baseline));
        }, json::writeValueAsString);

        http.get("/api/recurring", (req, res) -> {
            res.type("application/json");
            return queryService.recurring();
        }, json::writeValueAsString);

        http.put("/api/recurring/name", (req, res) -> {
            res.type("application/json");
            RecurringNameRequest body = json.readValue(req.body(), RecurringNameRequest.class);
            if (body.key() == null || body.key().isBlank()) {
                throw new IllegalArgumentException("key is required");
            }
            if (body.name() == null || body.name().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            recurringNameRepo.set(body.key(), body.name().trim());
            return "{\"status\":\"ok\"}";
        });

        http.delete("/api/recurring/name", (req, res) -> {
            res.type("application/json");
            RecurringNameRequest body = json.readValue(req.body(), RecurringNameRequest.class);
            if (body.key() == null || body.key().isBlank()) {
                throw new IllegalArgumentException("key is required");
            }
            recurringNameRepo.clear(body.key());
            return "{\"status\":\"ok\"}";
        });

        http.get("/api/categorization/suggestions", (req, res) -> {
            res.type("application/json");
            return queryService.keywordSuggestions();
        }, json::writeValueAsString);


        http.get("/api/categories", (req, res) -> {
            res.type("application/json");
            return Arrays.stream(Category.values())
                    .filter(Category::isAssignable)
                    .toList();
        }, json::writeValueAsString);

        http.get("/api/budgets", (req, res) -> {
            res.type("application/json");
            return budgetRepo.findAll();
        }, json::writeValueAsString);

        http.get("/api/budgets/report", (req, res) -> {
            res.type("application/json");
            String monthParam = req.queryParams("month");
            if (monthParam == null) throw new IllegalArgumentException("Query parameter 'month' is required (YYYY-MM)");
            return queryService.budgetReport(YearMonth.parse(monthParam));
        }, json::writeValueAsString);

        http.post("/api/imports/:bank", (req, res) -> {
            res.type("application/json");
            BankSource bank = parseBank(req.params("bank"));
            BankStatementParser parser = parserFor(bank);
            try (InputStream in = req.raw().getInputStream()) {
                importService.importStatement(parser, in);
            }
            return "{\"status\":\"imported\",\"bank\":\"" + bank + "\"}";
        });

        http.put("/api/transactions/:fingerprint/category", (req, res) -> {
            res.type("application/json");
            CategoryRequest body = json.readValue(req.body(), CategoryRequest.class);
            if (!body.category().isAssignable()) {
                throw new IllegalArgumentException("Category cannot be assigned manually: " + body.category());
            }
            overrideRepo.set(req.params("fingerprint"), body.category());
            return "{\"status\":\"ok\"}";
        });

        http.delete("/api/transactions/:fingerprint/category", (req, res) -> {
            res.type("application/json");
            overrideRepo.clear(req.params("fingerprint"));
            return "{\"status\":\"ok\"}";
        });

        http.put("/api/budgets/:category", (req, res) -> {
            res.type("application/json");
            Category category = parseCategory(req.params("category"));
            if (!category.isAssignable()) {
                throw new IllegalArgumentException("Cannot budget category: " + category);
            }
            BudgetRequest body = json.readValue(req.body(), BudgetRequest.class);
            if (body.amount() == null || body.amount().signum() <= 0) {
                throw new IllegalArgumentException("Budget amount must be positive");
            }
            budgetRepo.set(category, body.amount());
            return "{\"status\":\"ok\"}";
        });

        http.delete("/api/budgets/:category", (req, res) -> {
            res.type("application/json");
            budgetRepo.clear(parseCategory(req.params("category")));
            return "{\"status\":\"ok\"}";
        });

        http.put("/api/transactions/:fingerprint/transfer", (req, res) -> {
            res.type("application/json");
            transferRepo.mark(req.params("fingerprint"));
            return "{\"status\":\"ok\"}";
        });

        http.delete("/api/transactions/:fingerprint/transfer", (req, res) -> {
            res.type("application/json");
            transferRepo.unmark(req.params("fingerprint"));
            return "{\"status\":\"ok\"}";
        });

        http.exception(DateTimeParseException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body("{\"error\":\"Invalid month, expected format YYYY-MM\"}");
        });

        http.exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body("{\"error\":\"" + e.getMessage() + "\"}");
        });
    }

    private static BankSource parseBank(String raw) {
        try {
            return BankSource.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown bank: " + raw);
        }
    }
    private static Category parseCategory(String raw) {
        try {
            return Category.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + raw);
        }
    }

    private static BankStatementParser parserFor(BankSource bank) {
        return switch (bank) {
            case SANTANDER  -> new SantanderParser();
            case REVOLUT    -> new RevolutParser();
            case IMAGINBANK -> new ImaginParser();
        };
    }

    public static void main(String[] args) {
        new App(Database.fileDatabase("data/monthly.db"), 4567).start();
    }
}