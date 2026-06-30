package monthly.repository;

import monthly.db.Database;
import monthly.domain.BankSource;
import monthly.domain.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class SqliteTransactionRepository implements TransactionRepository {

    private final Database database;

    public SqliteTransactionRepository(Database database) {
        this.database = database;
    }

    @Override
    public void saveAll(List<Transaction> transactions) {
        String sql = """
                INSERT INTO transactions (operation_date, description, amount, currency, source, year_month)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            for (Transaction tx : transactions) {
                stmt.setString(1, tx.operationDate().toString());
                stmt.setString(2, tx.description());
                stmt.setString(3, tx.amount().toPlainString());
                stmt.setString(4, tx.currency());
                stmt.setString(5, tx.source().name());
                stmt.setString(6, YearMonth.from(tx.operationDate()).toString());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save transactions", e);
        }
    }

    @Override
    public void deleteBySourceAndMonth(BankSource source, YearMonth month) {
        String sql = "DELETE FROM transactions WHERE source = ? AND year_month = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, source.name());
            stmt.setString(2, month.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete transactions", e);
        }
    }

    @Override
    public List<Transaction> findByMonth(YearMonth month) {
        String sql = """
            SELECT operation_date, description, amount, currency, source
            FROM transactions
            WHERE year_month = ?
            ORDER BY operation_date DESC
            """;
        List<Transaction> result = new ArrayList<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, month.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new Transaction(
                            LocalDate.parse(rs.getString("operation_date")),
                            rs.getString("description"),
                            new BigDecimal(rs.getString("amount")),
                            rs.getString("currency"),
                            BankSource.valueOf(rs.getString("source"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query transactions", e);
        }
        return result;
    }
}