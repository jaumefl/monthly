package monthly.api;

import monthly.domain.MonthSummary;

import java.util.List;

/**
 * Renders a month's categorized transactions and summary as RFC 4180 CSV,
 * so the data opens cleanly in a spreadsheet. Category already reflects any
 * manual override (applied upstream) and each row carries its transfer flag.
 */
public final class MonthCsv {

    private MonthCsv() {}

    private static final String[] HEADER =
            {"Date", "Description", "Category", "Source", "Amount", "Currency", "Transfer", "Manual"};

    public static String render(List<CategorizedTransaction> rows, MonthSummary summary) {
        StringBuilder sb = new StringBuilder();
        writeRow(sb, HEADER);
        for (CategorizedTransaction tx : rows) {
            writeRow(sb,
                    tx.operationDate().toString(),
                    tx.description(),
                    tx.category().name(),
                    tx.source().name(),
                    tx.amount().toPlainString(),
                    tx.currency(),
                    tx.transfer() ? "yes" : "no",
                    tx.manual() ? "yes" : "no");
        }
        // Blank separator, then the month summary (transfers already excluded upstream).
        sb.append("\r\n");
        writeRow(sb, "Summary", "");
        writeRow(sb, "Income", summary.income().toPlainString());
        writeRow(sb, "Expenses", summary.expenses().toPlainString());
        writeRow(sb, "Net", summary.net().toPlainString());
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        sb.append("\r\n");
    }

    private static String escape(String field) {
        String f = field == null ? "" : field;
        if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
            return '"' + f.replace("\"", "\"\"") + '"';
        }
        return f;
    }
}