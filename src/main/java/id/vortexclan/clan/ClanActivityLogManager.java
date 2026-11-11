
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanActivityLogManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;

    public ClanActivityLogManager(
            VortexClan plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        createTable();
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS clan_activity_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_id TEXT NOT NULL,
                    actor_uuid TEXT,
                    action TEXT NOT NULL,
                    details TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """;

        synchronized (databaseManager.getLock()) {
            try (var statement = databaseManager
                    .getConnection()
                    .createStatement()) {

                statement.executeUpdate(sql);

                statement.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS
                        idx_clan_activity_logs
                        ON clan_activity_logs(clan_id, created_at DESC)
                        """);

            } catch (SQLException exception) {
                logError("Gagal membuat tabel activity log", exception);
            }
        }
    }

    public boolean log(
            String clanId,
            UUID actorUuid,
            String action,
            String details
    ) {
        if (clanId == null || clanId.isBlank()
                || action == null || action.isBlank()
                || details == null) {
            return false;
        }

        String sql = """
                INSERT INTO clan_activity_logs
                (clan_id, actor_uuid, action, details, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection()
                    .prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setString(
                        2,
                        actorUuid == null ? null : actorUuid.toString()
                );
                statement.setString(3, action);
                statement.setString(4, details);
                statement.setLong(5, System.currentTimeMillis());

                return statement.executeUpdate() > 0;

            } catch (SQLException exception) {
                logError("Gagal menyimpan activity log", exception);
                return false;
            }
        }
    }

    public List<ActivityLog> getLogs(
            String clanId,
            int limit,
            int offset
    ) {
        List<ActivityLog> logs = new ArrayList<>();

        if (clanId == null || clanId.isBlank()) {
            return logs;
        }

        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        String sql = """
                SELECT id, actor_uuid, action, details, created_at
                FROM clan_activity_logs
                WHERE clan_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection()
                    .prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String actor = result.getString("actor_uuid");
                        UUID actorUuid = null;

                        if (actor != null) {
                            try {
                                actorUuid = UUID.fromString(actor);
                            } catch (IllegalArgumentException ignored) {
                                // UUID tidak valid, log tetap ditampilkan.
                            }
                        }

                        logs.add(new ActivityLog(
                                result.getLong("id"),
                                actorUuid,
                                result.getString("action"),
                                result.getString("details"),
                                result.getLong("created_at")
                        ));
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal mengambil activity log", exception);
            }
        }

        return logs;
    }

    public int getTotalLogs(String clanId) {
        String sql = """
                SELECT COUNT(*)
                FROM clan_activity_logs
                WHERE clan_id = ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection()
                    .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }

            } catch (SQLException exception) {
                logError("Gagal menghitung activity log", exception);
                return 0;
            }
        }
    }

    public boolean clearLogs(String clanId) {
        String sql = """
                DELETE FROM clan_activity_logs
                WHERE clan_id = ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection()
                    .prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.executeUpdate();
                return true;

            } catch (SQLException exception) {
                logError("Gagal menghapus activity log", exception);
                return false;
            }
        }
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                "[ClanActivityLog] " + message + ": "
                        + exception.getMessage()
        );
    }

    public record ActivityLog(
            long id,
            UUID actorUuid,
            String action,
            String details,
            long createdAt
    ) {
    }
}
