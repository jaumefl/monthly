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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an imaginBank account-statement export (.csv).
 *
 * <p>Semicolon-delimited; a header row, one transaction per row, and a trailing
 * empty {@code ;;;} row:
 * <pre>
 * Concepto;Tarjeta;Fecha;Importe
 *   [0]      [1]    [2]     [3]
 * </pre>
 *
 * <ul>
 *   <li>{@code Concepto} — description</li>
 *   <li>{@code Tarjeta}  — masked card number (ignored; not part of the domain)</li>
 *   <li>{@code Fecha}    — operation date, {@code d-M-yyyy} (no leading zeros)</li>
 *   <li>{@code Importe}  — Spanish-formatted amount with a 3-letter currency
 *       suffix, e.g. {@code -200,00EUR} or {@code -1.200,50EUR}</li>
 * </ul>
 */
public class ImaginParser implements BankStatementParser {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d-M-yyyy");

    /** Splits an Importe cell into its numeric part and trailing 3-letter currency. */
    private static final Pattern AMOUNT_FORMAT =
            Pattern.compile("^(?<num>.*?)(?<currency>[A-Za-z]{3})$");

    private static final int COL_DESCRIPTION = 0;
    private static final int COL_DATE        = 2;
    private static final int COL_AMOUNT      = 3;

    @Override
    public List<Transaction> parse(InputStream statement) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(statement, StandardCharsets.UTF_8))) {

            reader.readLine(); // skip header

            List<Transaction> transactions = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(";", -1);

                // skip blank lines and the trailing ";;;" row (no date present)
                if (cols.length <= COL_AMOUNT || cols[COL_DATE].trim().isEmpty()) {
                    continue;
                }

                LocalDate operationDate = LocalDate.parse(cols[COL_DATE].trim(), DATE_FORMAT);
                String description = cols[COL_DESCRIPTION].trim();

                Matcher m = AMOUNT_FORMAT.matcher(cols[COL_AMOUNT].trim());
                if (!m.matches()) {
                    throw new IllegalArgumentException(
                            "Unexpected imaginBank amount format: " + cols[COL_AMOUNT]);
                }
                BigDecimal amount = parseAmount(m.group("num"));
                String currency   = m.group("currency");

                transactions.add(new Transaction(
                        operationDate, description, amount, currency, BankSource.IMAGINBANK));
            }

            return transactions;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse imaginBank statement", e);
        }
    }

    @Override
    public BankSource source() {
        return BankSource.IMAGINBANK;
    }

    /**
     * Normalizes a Spanish-formatted number such as {@code "-1.200,50"} or {@code "-200,00"}:
     * strips the period thousands separator and turns the decimal comma into a point.
     */
    private BigDecimal parseAmount(String raw) {
        String normalized = raw
                .replace('\u2212', '-')  // Unicode minus → ASCII hyphen (defensive)
                .replace(".", "")        // remove thousands separator
                .replace(',', '.');      // decimal comma → decimal point
        return new BigDecimal(normalized);
    }
}