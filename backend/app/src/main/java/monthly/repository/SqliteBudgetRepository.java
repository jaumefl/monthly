package monthly.repository;

import monthly.db.Database;
import monthly.domain.Category;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SqliteBudgetRepository implements BudgetRepository {

    private final Database database;

    public SqliteBudgetRepository(Database database) {
        this.database = database;
    }

    @Override
    public void set(Category category, BigDecimal limit) {
        String sql = """
                INSERT INTO budgets (category, limit_amount)
                VALUES (?, ?)
                ON CONFLICT(category) DO UPDATE SET limit_amount = excluded.limit_amount
                """;
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, category.name());
            stmt.setString(2, limit.toPlainString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set budget", e);
        }
    }

    @Override
    public void clear(Category category) {
        String sql = "DELETE FROM budgets WHERE category = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, category.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear budget", e);
        }
    }

    @Override
    public Map<Category, BigDecimal> findAll() {
        String sql = "SELECT category, limit_amount FROM budgets";
        Map<Category, BigDecimal> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.put(Category.valueOf(rs.getString("category")),
                        new BigDecimal(rs.getString("limit_amount")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load budgets", e);
        }
        return result;
    }
}