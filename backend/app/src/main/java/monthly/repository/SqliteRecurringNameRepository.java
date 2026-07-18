package monthly.repository;

import monthly.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SqliteRecurringNameRepository implements RecurringNameRepository {

    private final Database database;

    public SqliteRecurringNameRepository(Database database) {
        this.database = database;
    }

    @Override
    public void set(String seriesKey, String name) {
        String sql = """
                INSERT INTO recurring_names (series_key, name)
                VALUES (?, ?)
                ON CONFLICT(series_key) DO UPDATE SET name = excluded.name
                """;
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, seriesKey);
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set recurring name", e);
        }
    }

    @Override
    public void clear(String seriesKey) {
        String sql = "DELETE FROM recurring_names WHERE series_key = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, seriesKey);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear recurring name", e);
        }
    }

    @Override
    public Map<String, String> findAll() {
        String sql = "SELECT series_key, name FROM recurring_names";
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("series_key"), rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recurring names", e);
        }
        return result;
    }
}