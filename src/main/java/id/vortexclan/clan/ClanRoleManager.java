
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ClanRoleManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final ClanManager clanManager;

    public ClanRoleManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanManager clanManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.clanManager = clanManager;
    }

    public Result promote(UUID executorUuid, UUID targetUuid) {
        if (executorUuid.equals(targetUuid)) {
            return Result.CANNOT_MODIFY_SELF;
        }

        Clan executorClan;
        Clan targetClan;

        try {
            executorClan = clanManager.getClanByPlayer(executorUuid);
            targetClan = clanManager.getClanByPlayer(targetUuid);
        } catch (SQLException exception) {
            logError("Gagal mengambil data anggota", exception);
            return Result.DATABASE_ERROR;
        }

        if (executorClan == null) {
            return Result.NOT_IN_CLAN;
        }

        if (targetClan == null) {
            return Result.TARGET_NOT_IN_CLAN;
        }

        String clanId = String.valueOf(executorClan.getId());

        if (!clanId.equals(String.valueOf(targetClan.getId()))) {
            return Result.NOT_SAME_CLAN;
        }

        try {
            if (!clanManager.isLeader(executorUuid, clanId)) {
                return Result.NO_PERMISSION;
            }
        } catch (SQLException exception) {
            logError("Gagal memeriksa jabatan executor", exception);
            return Result.DATABASE_ERROR;
        }

        String sql = """
                UPDATE clan_members
                SET role = 'CO_LEADER'
                WHERE uuid = ?
                AND clan_id = ?
                AND role = 'MEMBER'
                """;

        return updateRole(sql, targetUuid, clanId, Result.SUCCESS_PROMOTED);
    }

    public Result demote(UUID executorUuid, UUID targetUuid) {
        if (executorUuid.equals(targetUuid)) {
            return Result.CANNOT_MODIFY_SELF;
        }

        Clan executorClan;
        Clan targetClan;

        try {
            executorClan = clanManager.getClanByPlayer(executorUuid);
            targetClan = clanManager.getClanByPlayer(targetUuid);
        } catch (SQLException exception) {
            logError("Gagal mengambil data anggota", exception);
            return Result.DATABASE_ERROR;
        }

        if (executorClan == null) {
            return Result.NOT_IN_CLAN;
        }

        if (targetClan == null) {
            return Result.TARGET_NOT_IN_CLAN;
        }

        String clanId = String.valueOf(executorClan.getId());

        if (!clanId.equals(String.valueOf(targetClan.getId()))) {
            return Result.NOT_SAME_CLAN;
        }

        try {
            if (!clanManager.isLeader(executorUuid, clanId)) {
                return Result.NO_PERMISSION;
            }
        } catch (SQLException exception) {
            logError("Gagal memeriksa jabatan executor", exception);
            return Result.DATABASE_ERROR;
        }

        String sql = """
                UPDATE clan_members
                SET role = 'MEMBER'
                WHERE uuid = ?
                AND clan_id = ?
                AND role = 'CO_LEADER'
                """;

        return updateRole(sql, targetUuid, clanId, Result.SUCCESS_DEMOTED);
    }

    private Result updateRole(
            String sql,
            UUID targetUuid,
            String clanId,
            Result successResult
    ) {
        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, targetUuid.toString());
                statement.setString(2, clanId);

                int updated = statement.executeUpdate();

                if (updated > 0) {
                    return successResult;
                }

                String role = getRole(targetUuid, clanId);

                if (role == null) {
                    return Result.TARGET_NOT_IN_CLAN;
                }

                if (role.equalsIgnoreCase("LEADER")) {
                    return Result.CANNOT_MODIFY_LEADER;
                }

                if (successResult == Result.SUCCESS_PROMOTED
                        && role.equalsIgnoreCase("CO_LEADER")) {
                    return Result.ALREADY_CO_LEADER;
                }

                if (successResult == Result.SUCCESS_DEMOTED
                        && role.equalsIgnoreCase("MEMBER")) {
                    return Result.ALREADY_MEMBER;
                }

                return Result.DATABASE_ERROR;

            } catch (SQLException exception) {
                logError("Gagal memperbarui jabatan anggota", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    private String getRole(UUID targetUuid, String clanId)
            throws SQLException {

        String sql = """
                SELECT role
                FROM clan_members
                WHERE uuid = ?
                AND clan_id = ?
                """;

        try (PreparedStatement statement = databaseManager
                .getConnection().prepareStatement(sql)) {

            statement.setString(1, targetUuid.toString());
            statement.setString(2, clanId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("role") : null;
            }
        }
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                "[ClanRole] " + message + ": " + exception.getMessage()
        );
    }

    public enum Result {
        SUCCESS_PROMOTED,
        SUCCESS_DEMOTED,
        NOT_IN_CLAN,
        TARGET_NOT_IN_CLAN,
        NOT_SAME_CLAN,
        NO_PERMISSION,
        CANNOT_MODIFY_SELF,
        CANNOT_MODIFY_LEADER,
        ALREADY_CO_LEADER,
        ALREADY_MEMBER,
        DATABASE_ERROR
    }
}
