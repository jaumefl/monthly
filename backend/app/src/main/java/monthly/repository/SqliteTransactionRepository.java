package monthly.repository;

import monthly.db.Database;
import monthly.domain.BankSource;
import monthly.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
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
}