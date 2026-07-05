package monthly.repository;

import monthly.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class SqliteTransferRepository implements TransferRepository {

    private final Database database;

    public SqliteTransferRepository(Database database) {
        this.database = database;
    }

    @Override
    public void mark(String fingerprint) {
        String sql = "INSERT INTO transfers (fingerprint) VALUES (?) "
                + "ON CONFLICT(fingerprint) DO NOTHING";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark transfer", e);
        }
    }

    @Override
    public void unmark(String fingerprint) {
        String sql = "DELETE FROM transfers WHERE fingerprint = ?";
        try (PreparedStatement stmt = database.connection().prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unmark transfer", e);
        }
    }

    @Override
    public Set<String> findAll() {
        String sql = "SELECT fingerprint FROM transfers";
        Set<String> result = new HashSet<>();
        try (PreparedStatement stmt = database.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("fingerprint"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load transfers", e);
        }
        return result;
    }
}