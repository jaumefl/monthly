package monthly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import monthly.db.Database;
import monthly.repository.SqliteTransactionRepository;
import monthly.repository.TransactionRepository;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import static spark.Spark.*;

public class App {

    public static void main(String[] args) {
        Database database = Database.fileDatabase("data/monthly.db");
        database.createSchema();
        TransactionRepository repository = new SqliteTransactionRepository(database);

        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        port(4567);

        get("/api/months/:yearMonth", (req, res) -> {
            res.type("application/json");
            YearMonth month = YearMonth.parse(req.params("yearMonth"));
            return repository.findByMonth(month);
        }, json::writeValueAsString);

        exception(DateTimeParseException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body("{\"error\":\"Invalid month, expected format YYYY-MM\"}");
        });
    }
}