
package id.vortexclan.database;

import id.vortexclan.VortexClan;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private final VortexClan plugin;
    private final Object databaseLock = new Object();

    private Connection connection;

    public DatabaseManager(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        synchronized (databaseLock) {
            try {
                File folder = plugin.getDataFolder();

                if (!folder.exists() && !folder.mkdirs()) {
                    throw new IllegalStateException(
                            "Tidak dapat membuat folder plugin!"
                    );
                }

                String fileName = plugin.getConfig().getString(
                        "database.file",
                        "clans.db"
                );

                File databaseFile = new File(folder, fileName);

                Class.forName("org.sqlite.JDBC");

                connection = DriverManager.getConnection(
                        "jdbc:sqlite:" + databaseFile.getAbsolutePath()
                );

                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                    statement.execute("PRAGMA journal_mode = WAL");
                }

                createTables();

                plugin.getLogger().info(
                        "Database SQLite berhasil diinisialisasi!"
                );

            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Gagal menginisialisasi database VortexClan!",
                        exception
                );
            }
        }
    }

    private void createTables() throws SQLException {
        synchronized (databaseLock) {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Database belum terhubung!");
            }

            try (Statement statement = connection.createStatement()) {

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS clans (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL UNIQUE,
                            tag TEXT NOT NULL UNIQUE,
                            leader_uuid TEXT NOT NULL,
                            description TEXT DEFAULT '',
                            level INTEGER NOT NULL DEFAULT 1,
                            exp INTEGER NOT NULL DEFAULT 0,
                            bank REAL NOT NULL DEFAULT 0,
                            pvp_enabled INTEGER NOT NULL DEFAULT 0,
                            home_world TEXT,
                            home_x DOUBLE,
                            home_y DOUBLE,
                            home_z DOUBLE,
                            home_yaw FLOAT,
                            home_pitch FLOAT,
                            wins INTEGER NOT NULL DEFAULT 0,
                            losses INTEGER NOT NULL DEFAULT 0,
                            kills INTEGER NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL,
                            tax_due_at INTEGER NOT NULL
                        )
                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS clan_members (
                            uuid TEXT PRIMARY KEY,
                            clan_id TEXT NOT NULL,
                            role TEXT NOT NULL DEFAULT 'MEMBER',
                            joined_at INTEGER NOT NULL,
                            FOREIGN KEY (clan_id)
                                REFERENCES clans(id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS clan_invites (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            clan_id TEXT NOT NULL,
                            inviter_uuid TEXT NOT NULL,
                            invited_uuid TEXT NOT NULL,
                            expires_at INTEGER NOT NULL,
                            UNIQUE (clan_id, invited_uuid),
                            FOREIGN KEY (clan_id)
                                REFERENCES clans(id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS clan_transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            clan_id TEXT NOT NULL,
                            player_uuid TEXT,
                            transaction_type TEXT NOT NULL,
                            amount REAL NOT NULL,
                            balance_after REAL NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY (clan_id)
                                REFERENCES clans(id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS clan_wars (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            clan_a TEXT NOT NULL,
                            clan_b TEXT NOT NULL,
                            status TEXT NOT NULL,
                            arena TEXT,
                            kills_a INTEGER NOT NULL DEFAULT 0,
                            kills_b INTEGER NOT NULL DEFAULT 0,
                            winner TEXT,
                            created_at INTEGER NOT NULL,
                            started_at INTEGER,
                            ended_at INTEGER,
                            FOREIGN KEY (clan_a)
                                REFERENCES clans(id)
                                ON DELETE CASCADE,
                            FOREIGN KEY (clan_b)
                                REFERENCES clans(id)
                                ON DELETE CASCADE
                        )
                        """);

                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clan_members_clan
                        ON clan_members(clan_id)
                        """);

                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clan_invites_player
                        ON clan_invites(invited_uuid)
                        """);

                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clan_transactions_clan
                        ON clan_transactions(clan_id)
                        """);

                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clan_wars_status
                        ON clan_wars(status)
                        """);
            }
        }
    }

    public Connection getConnection() {
        synchronized (databaseLock) {
            if (connection == null) {
                throw new IllegalStateException(
                        "Database belum diinisialisasi!"
                );
            }

            try {
                if (connection.isClosed()) {
                    throw new IllegalStateException(
                            "Koneksi database sudah ditutup!"
                    );
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Tidak dapat memeriksa koneksi database!",
                        exception
                );
            }

            return connection;
        }
    }

    /**
     * Semua manager wajib menggunakan lock ini
     * ketika melakukan operasi database.
     */
    public Object getLock() {
        return databaseLock;
    }

    public boolean isConnected() {
        synchronized (databaseLock) {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    public void close() {
        synchronized (databaseLock) {
            if (connection == null) {
                return;
            }

            try {
                if (!connection.isClosed()) {
                    connection.close();
                }

                plugin.getLogger().info(
                        "Koneksi database berhasil ditutup!"
                );

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "Gagal menutup koneksi database: "
                                + exception.getMessage()
                );
            } finally {
                connection = null;
            }
        }
    }
}
