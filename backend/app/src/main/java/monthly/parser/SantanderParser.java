package monthly.parser;

import monthly.domain.BankSource;
import monthly.domain.Transaction;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Santander Spain account-movements export (.xlsx).
 *
 * <p>Export structure:
 * <ul>
 *   <li>Rows 1–6: account metadata (skipped)</li>
 *   <li>Row 7: column headers — "Transaction date", "Value date", "Description",
 *       "Amount", "Balance", "Currency"</li>
 *   <li>Rows 8+: one transaction per row</li>
 * </ul>
 *
 * <p>Amounts are stored as strings with Spanish conventions:
 * <ul>
 *   <li>U+2212 MINUS SIGN (not ASCII hyphen) for negatives</li>
 *   <li>Period as thousands separator, comma as decimal separator</li>
 * </ul>
 */
public class SantanderParser implements BankStatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String HEADER_MARKER = "Transaction date";

    @Override
    public List<Transaction> parse(InputStream statement) {
        try (Workbook workbook = new XSSFWorkbook(statement)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Transaction> transactions = new ArrayList<>();
            boolean inData = false;

            for (Row row : sheet) {
                String firstCell = stringValue(row.getCell(0));

                if (HEADER_MARKER.equals(firstCell)) {
                    inData = true;
                    continue;
                }

                if (!inData || firstCell.isEmpty()) {
                    continue;
                }

                LocalDate operationDate = LocalDate.parse(firstCell, DATE_FORMAT);
                String description = cleanDescription(stringValue(row.getCell(2)));
                BigDecimal amount = parseAmount(stringValue(row.getCell(3)));
                String currency = stringValue(row.getCell(5));

                transactions.add(new Transaction(operationDate, description, amount, currency, BankSource.SANTANDER));
            }

            return transactions;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Santander statement", e);
        }
    }

    @Override
    public BankSource source() {
        return BankSource.SANTANDER;
    }

    private String cleanDescription(String raw) {
        return raw
                .replaceAll(",\\s*TARJETA\\s+\\d+\\s*", "")
                .replaceAll(",\\s*COMISION 0,00", "")
                .trim();
    }

    private String stringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT)
                    : String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    /**
     * Parses Spanish-formatted amount strings such as {@code "−3.500,00"} or {@code "16,16"}.
     *
     * <ol>
     *   <li>Replaces U+2212 MINUS SIGN with ASCII hyphen</li>
     *   <li>Removes period (thousands separator)</li>
     *   <li>Replaces comma with period (decimal separator)</li>
     * </ol>
     */
    private BigDecimal parseAmount(String raw) {
        String normalized = raw
                .replace('−', '-')   // Unicode minus → ASCII hyphen
                .replace(".", "")          // remove thousands separator
                .replace(',', '.');        // decimal comma → decimal point
        return new BigDecimal(normalized);
    }
}
