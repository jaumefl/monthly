package monthly.repository;

import monthly.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class SqliteRecurringDismissalRepository implements RecurringDismissalRepository {

    private final Database database;

    public SqliteRecurringDismissalRepository(Database database) {
        this.database = database;
    }

    @Override
    public void dismiss(String seriesKey) {
        String sql = "INSERT INTO recurring_dismissals (series_key) VALUES (?) "
                + "ON CONFLICT(series_key) DO NOTHING";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, seriesKey);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to dismiss recurring series", e);
        }
    }

    @Override
    public void restore(String seriesKey) {
        String sql = "DELETE FROM recurring_dismissals WHERE series_key = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, seriesKey);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to restore recurring series", e);
        }
    }

    @Override
    public Set<String> findAll() {
        String sql = "SELECT series_key FROM recurring_dismissals";
        Set<String> result = new HashSet<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("series_key"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recurring dismissals", e);
        }
        return result;
    }
}