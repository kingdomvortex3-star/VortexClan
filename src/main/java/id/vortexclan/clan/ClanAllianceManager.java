
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanAllianceManager {

    private static final long INVITE_DURATION = 300_000L;

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final ClanManager clanManager;

    public ClanAllianceManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanManager clanManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.clanManager = clanManager;
        createTables();
    }

    private void createTables() {
        String alliances = """
                CREATE TABLE IF NOT EXISTS clan_alliances (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    clan_a TEXT NOT NULL,
                    clan_b TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    UNIQUE(clan_a, clan_b)
                )
                """;

        String invites = """
                CREATE TABLE IF NOT EXISTS clan_alliance_invites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source_clan TEXT NOT NULL,
                    target_clan TEXT NOT NULL,
                    inviter_uuid TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    UNIQUE(source_clan, target_clan)
                )
                """;

        synchronized (databaseManager.getLock()) {
            try (var statement = databaseManager.getConnection().createStatement()) {
                statement.executeUpdate(alliances);
                statement.executeUpdate(invites);
            } catch (SQLException exception) {
                logError("Gagal membuat tabel aliansi", exception);
            }
        }
    }

    public Result invite(UUID playerUuid, String targetClanName) {
        Clan sourceClan;
        Clan targetClan;

        try {
            sourceClan = clanManager.getClanByPlayer(playerUuid);

            if (sourceClan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

            targetClan = clanManager.getClanByName(targetClanName);

            if (targetClan == null) {
                targetClan = clanManager.getClanByTag(targetClanName);
            }

        } catch (SQLException exception) {
            logError("Gagal mengambil data clan", exception);
            return Result.DATABASE_ERROR;
        }

        if (targetClan == null) {
            return Result.CLAN_NOT_FOUND;
        }

        String sourceId = String.valueOf(sourceClan.getId());
        String targetId = String.valueOf(targetClan.getId());

        if (sourceId.equals(targetId)) {
            return Result.SAME_CLAN;
        }

        if (hasAlliance(sourceId, targetId)) {
            return Result.ALREADY_ALLIED;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + INVITE_DURATION;

        String sql = """
                INSERT INTO clan_alliance_invites
                (source_clan, target_clan, inviter_uuid, expires_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(source_clan, target_clan)
                DO UPDATE SET
                    inviter_uuid = excluded.inviter_uuid,
                    expires_at = excluded.expires_at
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, sourceId);
                statement.setString(2, targetId);
                statement.setString(3, playerUuid.toString());
                statement.setLong(4, expiresAt);
                statement.executeUpdate();

                return Result.INVITE_SENT;

            } catch (SQLException exception) {
                logError("Gagal mengirim undangan aliansi", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result accept(UUID playerUuid, String sourceClanName) {
        Clan targetClan;
        Clan sourceClan;

        try {
            targetClan = clanManager.getClanByPlayer(playerUuid);

            if (targetClan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

            sourceClan = clanManager.getClanByName(sourceClanName);

            if (sourceClan == null) {
                sourceClan = clanManager.getClanByTag(sourceClanName);
            }

        } catch (SQLException exception) {
            logError("Gagal mengambil data clan", exception);
            return Result.DATABASE_ERROR;
        }

        if (sourceClan == null) {
            return Result.CLAN_NOT_FOUND;
        }

        String sourceId = String.valueOf(sourceClan.getId());
        String targetId = String.valueOf(targetClan.getId());

        if (sourceId.equals(targetId)) {
            return Result.SAME_CLAN;
        }

        synchronized (databaseManager.getLock()) {
            try {
                long now = System.currentTimeMillis();

                String checkSql = """
                        SELECT expires_at
                        FROM clan_alliance_invites
                        WHERE source_clan = ?
                        AND target_clan = ?
                        """;

                try (PreparedStatement statement = databaseManager
                        .getConnection().prepareStatement(checkSql)) {

                    statement.setString(1, sourceId);
                    statement.setString(2, targetId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            return Result.INVITE_NOT_FOUND;
                        }

                        if (result.getLong("expires_at") < now) {
                            deleteInvite(sourceId, targetId);
                            return Result.INVITE_EXPIRED;
                        }
                    }
                }

                String insertSql = """
                        INSERT INTO clan_alliances
                        (clan_a, clan_b, created_at)
                        VALUES (?, ?, ?)
                        """;

                try (PreparedStatement statement = databaseManager
                        .getConnection().prepareStatement(insertSql)) {

                    statement.setString(1, sourceId);
                    statement.setString(2, targetId);
                    statement.setLong(3, now);
                    statement.executeUpdate();
                }

                deleteInvite(sourceId, targetId);

                return Result.SUCCESS;

            } catch (SQLException exception) {
                logError("Gagal menerima undangan aliansi", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result deny(UUID playerUuid, String sourceClanName) {
        Clan targetClan;
        Clan sourceClan;

        try {
            targetClan = clanManager.getClanByPlayer(playerUuid);

            if (targetClan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

            sourceClan = clanManager.getClanByName(sourceClanName);

            if (sourceClan == null) {
                sourceClan = clanManager.getClanByTag(sourceClanName);
            }

        } catch (SQLException exception) {
            logError("Gagal mengambil data clan", exception);
            return Result.DATABASE_ERROR;
        }

        if (sourceClan == null) {
            return Result.CLAN_NOT_FOUND;
        }

        String sourceId = String.valueOf(sourceClan.getId());
        String targetId = String.valueOf(targetClan.getId());

        synchronized (databaseManager.getLock()) {
            try {
                String sql = """
                        DELETE FROM clan_alliance_invites
                        WHERE source_clan = ?
                        AND target_clan = ?
                        """;

                try (PreparedStatement statement = databaseManager
                        .getConnection().prepareStatement(sql)) {

                    statement.setString(1, sourceId);
                    statement.setString(2, targetId);

                    if (statement.executeUpdate() > 0) {
                        return Result.INVITE_DENIED;
                    }

                    return Result.INVITE_NOT_FOUND;
                }

            } catch (SQLException exception) {
                logError("Gagal menolak undangan aliansi", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result removeAlliance(UUID playerUuid, String targetClanName) {
        Clan ownClan;
        Clan targetClan;

        try {
            ownClan = clanManager.getClanByPlayer(playerUuid);

            if (ownClan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

            targetClan = clanManager.getClanByName(targetClanName);

            if (targetClan == null) {
                targetClan = clanManager.getClanByTag(targetClanName);
            }

        } catch (SQLException exception) {
            logError("Gagal mengambil data clan", exception);
            return Result.DATABASE_ERROR;
        }

        if (targetClan == null) {
            return Result.CLAN_NOT_FOUND;
        }

        String ownId = String.valueOf(ownClan.getId());
        String targetId = String.valueOf(targetClan.getId());

        if (ownId.equals(targetId)) {
            return Result.SAME_CLAN;
        }

        String sql = """
                DELETE FROM clan_alliances
                WHERE (clan_a = ? AND clan_b = ?)
                   OR (clan_a = ? AND clan_b = ?)
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, ownId);
                statement.setString(2, targetId);
                statement.setString(3, targetId);
                statement.setString(4, ownId);

                if (statement.executeUpdate() > 0) {
                    return Result.ALLIANCE_REMOVED;
                }

                return Result.NOT_ALLIED;

            } catch (SQLException exception) {
                logError("Gagal menghapus aliansi", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public List<AllianceData> getAlliances(String clanId) {
        List<AllianceData> alliances = new ArrayList<>();

        String sql = """
                SELECT clan_a, clan_b, created_at
                FROM clan_alliances
                WHERE clan_a = ? OR clan_b = ?
                ORDER BY created_at DESC
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setString(2, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String clanA = result.getString("clan_a");
                        String clanB = result.getString("clan_b");

                        String otherClan = clanA.equals(clanId)
                                ? clanB
                                : clanA;

                        alliances.add(new AllianceData(
                                otherClan,
                                result.getLong("created_at")
                        ));
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal mengambil daftar aliansi", exception);
            }
        }

        return alliances;
    }

    public List<AllianceInvite> getPendingInvites(String clanId) {
        List<AllianceInvite> invites = new ArrayList<>();

        String sql = """
                SELECT source_clan, target_clan, inviter_uuid, expires_at
                FROM clan_alliance_invites
                WHERE target_clan = ?
                AND expires_at > ?
                ORDER BY expires_at ASC
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setLong(2, System.currentTimeMillis());

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        invites.add(new AllianceInvite(
                                result.getString("source_clan"),
                                result.getString("target_clan"),
                                result.getString("inviter_uuid"),
                                result.getLong("expires_at")
                        ));
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal mengambil undangan aliansi", exception);
            }
        }

        return invites;
    }

    public boolean isAllied(String clanA, String clanB) {
        return hasAlliance(clanA, clanB);
    }

    private boolean hasAlliance(String clanA, String clanB) {
        String sql = """
                SELECT 1
                FROM clan_alliances
                WHERE (clan_a = ? AND clan_b = ?)
                   OR (clan_a = ? AND clan_b = ?)
                LIMIT 1
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, clanA);
                statement.setString(2, clanB);
                statement.setString(3, clanB);
                statement.setString(4, clanA);

                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }

            } catch (SQLException exception) {
                logError("Gagal memeriksa aliansi", exception);
                return false;
            }
        }
    }

    private void deleteInvite(String sourceId, String targetId)
            throws SQLException {

        String sql = """
                DELETE FROM clan_alliance_invites
                WHERE source_clan = ?
                AND target_clan = ?
                """;

        try (PreparedStatement statement = databaseManager
                .getConnection().prepareStatement(sql)) {

            statement.setString(1, sourceId);
            statement.setString(2, targetId);
            statement.executeUpdate();
        }
    }

    private boolean canManage(String role) {
        return role != null
                && (role.equalsIgnoreCase("LEADER")
                || role.equalsIgnoreCase("CO_LEADER"));
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                "[ClanAlliance] " + message + ": "
                        + exception.getMessage()
        );
    }

    public record AllianceData(String clanId, long createdAt) {
    }

    public record AllianceInvite(
            String sourceClan,
            String targetClan,
            String inviterUuid,
            long expiresAt
    ) {
    }

    public enum Result {
        INVITE_SENT,
        SUCCESS,
        INVITE_DENIED,
        ALLIANCE_REMOVED,
        NOT_IN_CLAN,
        NO_PERMISSION,
        CLAN_NOT_FOUND,
        SAME_CLAN,
        ALREADY_ALLIED,
        NOT_ALLIED,
        INVITE_NOT_FOUND,
        INVITE_EXPIRED,
        DATABASE_ERROR
    }
}
