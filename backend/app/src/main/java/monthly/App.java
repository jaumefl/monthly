package monthly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import monthly.db.Database;
import monthly.domain.BankSource;
import monthly.domain.MonthSummary;
import monthly.parser.BankStatementParser;
import monthly.parser.RevolutParser;
import monthly.parser.SantanderParser;
import monthly.repository.SqliteTransactionRepository;
import monthly.repository.TransactionRepository;
import monthly.service.ImportService;

import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import static spark.Spark.*;

public class App {

    public static void main(String[] args) {
        Database database = Database.fileDatabase("data/monthly.db");
        database.createSchema();
        TransactionRepository repository = new SqliteTransactionRepository(database);
        ImportService importService = new ImportService(repository);

        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        port(4567);

        get("/api/months/:yearMonth", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return repository.findByMonth(month);
        }, json::writeValueAsString);

        get("/api/months/:yearMonth/summary", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return MonthSummary.of(month, repository.findByMonth(month));
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