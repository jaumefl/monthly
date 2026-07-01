package monthly.repository;

import monthly.db.Database;
import monthly.domain.Category;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SqliteCategoryOverrideRepository implements CategoryOverrideRepository {

    private final Database database;

    public SqliteCategoryOverrideRepository(Database database) {
        this.database = database;
    }

    @Override
    public void set(String fingerprint, Category category) {
        String sql = """
                INSERT INTO category_overrides (fingerprint, category)
                VALUES (?, ?)
                ON CONFLICT(fingerprint) DO UPDATE SET category = excluded.category
                """;
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            stmt.setString(2, category.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set category override", e);
        }
    }

    @Override
    public void clear(String fingerprint) {
        String sql = "DELETE FROM category_overrides WHERE fingerprint = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear category override", e);
        }
    }

    @Override
    public Map<String, Category> findAll() {
        String sql = "SELECT fingerprint, category FROM category_overrides";
        Map<String, Category> result = new HashMap<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("fingerprint"),
                        Category.valueOf(rs.getString("category")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load category overrides", e);
        }
        return result;
    }
}