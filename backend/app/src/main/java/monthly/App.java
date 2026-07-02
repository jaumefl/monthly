package monthly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import monthly.api.CategoryRequest;
import monthly.api.MonthComparison;
import monthly.db.Database;
import monthly.domain.BankSource;
import monthly.domain.Category;
import monthly.domain.MonthSummary;
import monthly.domain.TransactionCategorizer;
import monthly.parser.BankStatementParser;
import monthly.parser.RevolutParser;
import monthly.parser.SantanderParser;
import monthly.repository.CategoryOverrideRepository;
import monthly.repository.SqliteCategoryOverrideRepository;
import monthly.repository.SqliteTransactionRepository;
import monthly.repository.TransactionRepository;
import monthly.service.ImportService;
import monthly.service.TransactionQueryService;

import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import static spark.Spark.*;

public class App {

    public static void main(String[] args) {
        Database database = Database.fileDatabase("data/monthly.db");
        database.createSchema();

        TransactionRepository repository = new SqliteTransactionRepository(database);
        CategoryOverrideRepository overrideRepo = new SqliteCategoryOverrideRepository(database);
        ImportService importService = new ImportService(repository);
        TransactionCategorizer categorizer = new TransactionCategorizer();
        TransactionQueryService queryService =
                new TransactionQueryService(repository, overrideRepo, categorizer);

        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        port(4567);
        staticFiles.location("/public");   // serves src/main/resources/public at "/"

        get("/api/months/:yearMonth", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return queryService.categorizedForMonth(month);
        }, json::writeValueAsString);

        get("/api/months/:yearMonth/summary", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return MonthSummary.of(month, repository.findByMonth(month));
        }, json::writeValueAsString);

        get("/api/comparison", (req, res) -> {
            res.type("application/json");
            String monthParam = req.queryParams("month");
            if (monthParam == null) throw new IllegalArgumentException("Query parameter 'month' is required (YYYY-MM)");
            YearMonth month = YearMonth.parse(monthParam);
            String baselineParam = req.queryParams("baseline");
            YearMonth baseline = baselineParam != null ? YearMonth.parse(baselineParam) : month.minusMonths(1);
            return MonthComparison.of(queryService.categoryBreakdown(month), queryService.categoryBreakdown(baseline));
        }, json::writeValueAsString);

        get("/api/categories", (req, res) -> {
            res.type("application/json");
            return Arrays.stream(Category.values())
                    .filter(Category::isAssignable)
                    .toList();
        }, json::writeValueAsString);

        post("/api/imports/:bank", (req, res) -> {
            res.type("application/json");
            BankSource bank = parseBank(req.params("bank"));
            BankStatementParser parser = parserFor(bank);
            try (InputStream in = req.raw().getInputStream()) {
                importService.importStatement(parser, in);
            }
            return "{\"status\":\"imported\",\"bank\":\"" + bank + "\"}";
        });

        put("/api/transactions/:fingerprint/category", (req, res) -> {
            res.type("application/json");
            CategoryRequest body = json.readValue(req.body(), CategoryRequest.class);
            if (!body.category().isAssignable()) {
                throw new IllegalArgumentException("Category cannot be assigned manually: " + body.category());
            }
            overrideRepo.set(req.params("fingerprint"), body.category());
            return "{\"status\":\"ok\"}";
        });

        delete("/api/transactions/:fingerprint/category", (req, res) -> {
            res.type("application/json");
            overrideRepo.clear(req.params("fingerprint"));
            return "{\"status\":\"ok\"}";
        });

        exception(DateTimeParseException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body("{\"error\":\"Invalid month, expected format YYYY-MM\"}");
        });

        exception(IllegalArgumentException.class, (e, req, res) -> {
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

    private static BankStatementParser parserFor(BankSource bank) {
        return switch (bank) {
            case SANTANDER  -> new SantanderParser();
            case REVOLUT    -> new RevolutParser();
            case IMAGINBANK -> throw new IllegalArgumentException("imaginBank parser not implemented yet");
        };
    }
}