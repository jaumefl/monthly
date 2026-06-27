package monthly.parser;

import monthly.domain.BankSource;
import monthly.domain.Transaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Revolut account-statement export (.csv).
 *
 * <p>Export structure (header row followed by one transaction per row):
 * <pre>
 * Type, Product, Started Date, Completed Date, Description, Amount, Fee, Currency, State, Balance
 *  [0]     [1]         [2]           [3]            [4]       [5]   [6]    [7]     [8]     [9]
 * </pre>
 *
 * <p>Date semantics: {@code Started Date} (column 2) is used as the operation date.
 * {@code Completed Date} may be empty for PENDING transactions — those rows are still included.
 *
 * <p>Amounts use dot as decimal separator with no thousands separator, so no
 * locale-specific normalization is required.
 *
 * <p>Assumption: description values do not contain commas (consistent with
 * observed Revolut export format).
 */
public class RevolutParser implements BankStatementParser {

    /** {@code Started Date} column format: {@code yyyy-MM-dd HH:mm:ss} */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int COL_STARTED_DATE = 2;
    private static final int COL_DESCRIPTION  = 4;
    private static final int COL_AMOUNT       = 5;
    private static final int COL_CURRENCY     = 7;

    @Override
    public List<Transaction> parse(InputStream statement) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(statement, StandardCharsets.UTF_8))) {

            reader.readLine(); // skip header

            List<Transaction> transactions = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);

                LocalDate operationDate = LocalDate.parse(
                        cols[COL_STARTED_DATE].trim(), DATE_TIME_FORMAT);
                String description = cols[COL_DESCRIPTION].trim();
                BigDecimal amount  = new BigDecimal(cols[COL_AMOUNT].trim());
                String currency    = cols[COL_CURRENCY].trim();

                transactions.add(new Transaction(
                        operationDate, description, amount, currency, BankSource.REVOLUT));
            }

            return transactions;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Revolut statement", e);
        }
    }

    @Override
    public BankSource source() {
        return BankSource.REVOLUT;
    }
}
