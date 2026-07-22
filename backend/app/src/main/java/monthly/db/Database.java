package monthly.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private final Connection connection;

    private Database(String url) {
        try {
            this.connection = DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open database connection", e);
        }
    }

    public static Database fileDatabase(String path) {
        return new Database("jdbc:sqlite:" + path);
    }

    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    public Connection connection() {
        return connection;
    }

    public void createSchema() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                        operation_date TEXT    NOT NULL,
                        description    TEXT    NOT NULL,
                        amount         TEXT    NOT NULL,
                        currency       TEXT    NOT NULL,
                        source         TEXT    NOT NULL,
                        year_month     TEXT    NOT NULL
                    )
                    """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS category_overrides (
                    fingerprint TEXT PRIMARY KEY,
                    category    TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transfers (
                    fingerprint TEXT PRIMARY KEY
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS budgets (
                    category     TEXT PRIMARY KEY,
                    limit_amount TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recurring_names (
                    series_key TEXT PRIMARY KEY,
                    name       TEXT NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recurring_dismissals (
                    series_key TEXT PRIMARY KEY
                )
                """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create schema", e);
        }
    }
}