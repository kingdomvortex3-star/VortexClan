
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class ClanTransferManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final ClanManager clanManager;

    public ClanTransferManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanManager clanManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.clanManager = clanManager;
    }

    public Result transfer(UUID leaderUuid, UUID targetUuid) {
        if (leaderUuid.equals(targetUuid)) {
            return Result.CANNOT_TRANSFER_TO_SELF;
        }

        Clan leaderClan;
        Clan targetClan;

        try {
            leaderClan = clanManager.getClanByPlayer(leaderUuid);
            targetClan = clanManager.getClanByPlayer(targetUuid);
        } catch (SQLException exception) {
            logError("Gagal mengambil data clan", exception);
            return Result.DATABASE_ERROR;
        }

        if (leaderClan == null) {
            return Result.NOT_IN_CLAN;
        }

        if (targetClan == null) {
            return Result.TARGET_NOT_IN_CLAN;
        }

        String clanId = String.valueOf(leaderClan.getId());

        if (!clanId.equals(String.valueOf(targetClan.getId()))) {
            return Result.NOT_SAME_CLAN;
        }

        try {
            if (!clanManager.isLeader(leaderUuid, clanId)) {
                return Result.NO_PERMISSION;
            }
        } catch (SQLException exception) {
            logError("Gagal memeriksa Leader", exception);
            return Result.DATABASE_ERROR;
        }

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                try {
                    String updateClan = """
                            UPDATE clans
                            SET leader_uuid = ?
                            WHERE id = ?
                            AND leader_uuid = ?
                            """;

                    try (PreparedStatement statement =
                                 connection.prepareStatement(updateClan)) {

                        statement.setString(1, targetUuid.toString());
                        statement.setString(2, clanId);
                        statement.setString(3, leaderUuid.toString());

                        if (statement.executeUpdate() == 0) {
                            connection.rollback();
                            return Result.LEADER_CHANGED;
                        }
                    }

                    String updateOldLeader = """
                            UPDATE clan_members
                            SET role = 'MEMBER'
                            WHERE uuid = ?
                            AND clan_id = ?
                            AND role = 'LEADER'
                            """;

                    try (PreparedStatement statement =
                                 connection.prepareStatement(updateOldLeader)) {

                        statement.setString(1, leaderUuid.toString());
                        statement.setString(2, clanId);

                        if (statement.executeUpdate() == 0) {
                            connection.rollback();
                            return Result.DATABASE_ERROR;
                        }
                    }

                    String updateNewLeader = """
                            UPDATE clan_members
                            SET role = 'LEADER'
                            WHERE uuid = ?
                            AND clan_id = ?
                            AND role IN ('MEMBER', 'CO_LEADER')
                            """;

                    try (PreparedStatement statement =
                                 connection.prepareStatement(updateNewLeader)) {

                        statement.setString(1, targetUuid.toString());
                        statement.setString(2, clanId);

                        if (statement.executeUpdate() == 0) {
                            connection.rollback();
                            return Result.TARGET_NOT_IN_CLAN;
                        }
                    }

                    connection.commit();
                    return Result.SUCCESS;

                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;

                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }

            } catch (SQLException exception) {
                logError("Gagal melakukan transfer leadership", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                "[ClanTransfer] " + message + ": "
                        + exception.getMessage()
        );
    }

    public enum Result {
        SUCCESS,
        NOT_IN_CLAN,
        TARGET_NOT_IN_CLAN,
        NOT_SAME_CLAN,
        NO_PERMISSION,
        CANNOT_TRANSFER_TO_SELF,
        LEADER_CHANGED,
        DATABASE_ERROR
    }
}
