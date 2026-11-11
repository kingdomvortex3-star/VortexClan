
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class ClanMemberManager {

    private final VortexClan plugin;
    private final ClanManager clanManager;
    private final DatabaseManager databaseManager;

    public enum Result {
        SUCCESS,
        NOT_IN_CLAN,
        ALREADY_IN_CLAN,
        PLAYER_NOT_FOUND,
        INVITE_NOT_FOUND,
        INVITE_EXPIRED,
        ALREADY_INVITED,
        CLAN_FULL,
        NO_PERMISSION,
        LEADER_CANNOT_LEAVE,
        CANNOT_KICK_LEADER,
        CANNOT_KICK_HIGHER_ROLE,
        DATABASE_ERROR
    }

    public ClanMemberManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanManager clanManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.clanManager = clanManager;
    }

    public synchronized Result invite(
            UUID inviterUUID,
            UUID targetUUID
    ) {
        if (inviterUUID == null || targetUUID == null
                || inviterUUID.equals(targetUUID)) {
            return Result.PLAYER_NOT_FOUND;
        }

        synchronized (databaseManager.getLock()) {
            try {
                Clan clan = clanManager.getClanByPlayer(inviterUUID);

                if (clan == null) {
                    return Result.NOT_IN_CLAN;
                }

                String inviterRole =
                        clanManager.getMemberRole(inviterUUID);

                if (!hasManagementPermission(inviterRole)) {
                    return Result.NO_PERMISSION;
                }

                if (clanManager.getClanByPlayer(targetUUID) != null) {
                    return Result.ALREADY_IN_CLAN;
                }

                if (Bukkit.getPlayer(targetUUID) == null) {
                    return Result.PLAYER_NOT_FOUND;
                }

                int memberCount =
                        clanManager.getMemberCount(clan.getId());

                if (memberCount >= clanManager.getCapacity(
                        clan.getLevel())) {
                    return Result.CLAN_FULL;
                }

                Connection connection = databaseManager.getConnection();
                long now = System.currentTimeMillis();

                try (PreparedStatement delete =
                             connection.prepareStatement("""
                        DELETE FROM clan_invites
                        WHERE invited_uuid = ?
                        AND expires_at <= ?
                        """)) {

                    delete.setString(1, targetUUID.toString());
                    delete.setLong(2, now);
                    delete.executeUpdate();
                }

                long duration = Math.max(
                        1,
                        plugin.getConfig().getLong(
                                "clan.invite.expiration-seconds", 300
                        )
                );

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        INSERT INTO clan_invites (
                            clan_id,
                            inviter_uuid,
                            invited_uuid,
                            expires_at
                        )
                        VALUES (?, ?, ?, ?)
                        """)) {

                    statement.setString(1, clan.getId());
                    statement.setString(2, inviterUUID.toString());
                    statement.setString(3, targetUUID.toString());
                    statement.setLong(4, now + duration * 1000L);
                    statement.executeUpdate();
                }

                return Result.SUCCESS;

            } catch (SQLException exception) {
                logError("Failed to invite player", exception);

                if (exception.getMessage() != null
                        && exception.getMessage().contains("UNIQUE")) {
                    return Result.ALREADY_INVITED;
                }

                return Result.DATABASE_ERROR;
            }
        }
    }

    public synchronized Result accept(UUID playerUUID) {
        if (playerUUID == null) {
            return Result.INVITE_NOT_FOUND;
        }

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            boolean oldAutoCommit;

            try {
                oldAutoCommit = connection.getAutoCommit();
            } catch (SQLException exception) {
                logError("Failed to read transaction state", exception);
                return Result.DATABASE_ERROR;
            }

            try {
                connection.setAutoCommit(false);

                try {
                    if (clanManager.getClanByPlayer(playerUUID) != null) {
                        connection.rollback();
                        return Result.ALREADY_IN_CLAN;
                    }

                    String clanId;
                    long expiresAt;

                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            SELECT clan_id, expires_at
                            FROM clan_invites
                            WHERE invited_uuid = ?
                            ORDER BY expires_at DESC
                            LIMIT 1
                            """)) {

                        statement.setString(1, playerUUID.toString());

                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                return Result.INVITE_NOT_FOUND;
                            }

                            clanId = result.getString("clan_id");
                            expiresAt = result.getLong("expires_at");
                        }
                    }

                    if (expiresAt <= System.currentTimeMillis()) {
                        deleteInvites(connection, playerUUID);
                        connection.commit();
                        return Result.INVITE_EXPIRED;
                    }

                    Clan clan = clanManager.getClanById(clanId);

                    if (clan == null) {
                        deleteInvites(connection, playerUUID);
                        connection.commit();
                        return Result.INVITE_NOT_FOUND;
                    }

                    int memberCount =
                            clanManager.getMemberCount(clanId);

                    if (memberCount >= clanManager.getCapacity(
                            clan.getLevel())) {
                        connection.rollback();
                        return Result.CLAN_FULL;
                    }

                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            INSERT INTO clan_members (
                                uuid,
                                clan_id,
                                role,
                                joined_at
                            )
                            VALUES (?, ?, 'MEMBER', ?)
                            """)) {

                        statement.setString(1, playerUUID.toString());
                        statement.setString(2, clanId);
                        statement.setLong(
                                3,
                                System.currentTimeMillis()
                        );

                        statement.executeUpdate();
                    }

                    deleteInvites(connection, playerUUID);

                    connection.commit();
                    return Result.SUCCESS;

                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }

            } catch (SQLException exception) {
                logError("Failed to accept clan invitation", exception);
                return Result.DATABASE_ERROR;

            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException exception) {
                    logError(
                            "Failed to restore transaction state",
                            exception
                    );
                }
            }
        }
    }

    public synchronized Result deny(UUID playerUUID) {
        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement("""
                        DELETE FROM clan_invites
                        WHERE invited_uuid = ?
                        """)) {

                statement.setString(1, playerUUID.toString());

                int deleted = statement.executeUpdate();

                return deleted > 0
                        ? Result.SUCCESS
                        : Result.INVITE_NOT_FOUND;

            } catch (SQLException exception) {
                logError("Failed to deny invitation", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public synchronized Result kick(
            UUID executorUUID,
            UUID targetUUID
    ) {
        synchronized (databaseManager.getLock()) {
            try {
                Clan clan = clanManager.getClanByPlayer(executorUUID);

                if (clan == null) {
                    return Result.NOT_IN_CLAN;
                }

                Clan targetClan =
                        clanManager.getClanByPlayer(targetUUID);

                if (targetClan == null
                        || !targetClan.getId().equals(clan.getId())) {
                    return Result.NOT_IN_CLAN;
                }

                if (executorUUID.equals(targetUUID)) {
                    return Result.CANNOT_KICK_HIGHER_ROLE;
                }

                String executorRole =
                        clanManager.getMemberRole(executorUUID);

                String targetRole =
                        clanManager.getMemberRole(targetUUID);

                if (!hasManagementPermission(executorRole)) {
                    return Result.NO_PERMISSION;
                }

                if ("LEADER".equalsIgnoreCase(targetRole)) {
                    return Result.CANNOT_KICK_LEADER;
                }

                if ("CO_LEADER".equalsIgnoreCase(targetRole)
                        && !"LEADER".equalsIgnoreCase(executorRole)) {
                    return Result.CANNOT_KICK_HIGHER_ROLE;
                }

                try (PreparedStatement statement =
                             databaseManager.getConnection()
                                     .prepareStatement("""
                            DELETE FROM clan_members
                            WHERE uuid = ? AND clan_id = ?
                            """)) {

                    statement.setString(1, targetUUID.toString());
                    statement.setString(2, clan.getId());

                    return statement.executeUpdate() > 0
                            ? Result.SUCCESS
                            : Result.DATABASE_ERROR;
                }

            } catch (SQLException exception) {
                logError("Failed to kick clan member", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public synchronized Result leave(UUID playerUUID) {
        synchronized (databaseManager.getLock()) {
            try {
                Clan clan = clanManager.getClanByPlayer(playerUUID);

                if (clan == null) {
                    return Result.NOT_IN_CLAN;
                }

                if (clan.getLeader().equals(playerUUID)) {
                    return Result.LEADER_CANNOT_LEAVE;
                }

                try (PreparedStatement statement =
                             databaseManager.getConnection()
                                     .prepareStatement("""
                            DELETE FROM clan_members
                            WHERE uuid = ? AND clan_id = ?
                            """)) {

                    statement.setString(1, playerUUID.toString());
                    statement.setString(2, clan.getId());

                    return statement.executeUpdate() > 0
                            ? Result.SUCCESS
                            : Result.DATABASE_ERROR;
                }

            } catch (SQLException exception) {
                logError("Failed to leave clan", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public int getPendingInvites(UUID playerUUID)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement("""
                        SELECT COUNT(*)
                        FROM clan_invites
                        WHERE invited_uuid = ?
                        AND expires_at > ?
                        """)) {

                statement.setString(1, playerUUID.toString());
                statement.setLong(2, System.currentTimeMillis());

                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        }
    }

    private void deleteInvites(
            Connection connection,
            UUID playerUUID
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement("""
                DELETE FROM clan_invites
                WHERE invited_uuid = ?
                """)) {

            statement.setString(1, playerUUID.toString());
            statement.executeUpdate();
        }
    }

    private boolean hasManagementPermission(String role) {
        return "LEADER".equalsIgnoreCase(role)
                || "CO_LEADER".equalsIgnoreCase(role);
    }

    private void logError(String message, Exception exception) {
        plugin.getLogger().severe(
                message + ": " + exception.getMessage()
        );
    }
}
