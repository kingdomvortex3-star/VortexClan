
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ClanDescriptionManager {

    private static final int MAX_LENGTH = 100;

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final ClanManager clanManager;

    public ClanDescriptionManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanManager clanManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.clanManager = clanManager;
    }

    public Result setDescription(UUID playerUuid, String description) {
        if (description == null) {
            return Result.INVALID_DESCRIPTION;
        }

        String cleaned = description.trim();

        if (cleaned.isEmpty()) {
            return Result.INVALID_DESCRIPTION;
        }

        if (cleaned.length() > MAX_LENGTH) {
            return Result.TOO_LONG;
        }

        Clan clan;

        try {
            clan = clanManager.getClanByPlayer(playerUuid);

            if (clan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

        } catch (SQLException exception) {
            logError("Gagal memeriksa permission deskripsi", exception);
            return Result.DATABASE_ERROR;
        }

        String sql = """
                UPDATE clans
                SET description = ?
                WHERE id = ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, cleaned);
                statement.setString(2, String.valueOf(clan.getId()));

                if (statement.executeUpdate() > 0) {
                    return Result.SUCCESS;
                }

                return Result.CLAN_NOT_FOUND;

            } catch (SQLException exception) {
                logError("Gagal menyimpan deskripsi clan", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result removeDescription(UUID playerUuid) {
        Clan clan;

        try {
            clan = clanManager.getClanByPlayer(playerUuid);

            if (clan == null) {
                return Result.NOT_IN_CLAN;
            }

            String role = clanManager.getMemberRole(playerUuid);

            if (!canManage(role)) {
                return Result.NO_PERMISSION;
            }

        } catch (SQLException exception) {
            logError("Gagal memeriksa permission deskripsi", exception);
            return Result.DATABASE_ERROR;
        }

        String sql = """
                UPDATE clans
                SET description = ''
                WHERE id = ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, String.valueOf(clan.getId()));

                if (statement.executeUpdate() > 0) {
                    return Result.SUCCESS;
                }

                return Result.CLAN_NOT_FOUND;

            } catch (SQLException exception) {
                logError("Gagal menghapus deskripsi clan", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public String getDescription(String clanId) {
        String sql = """
                SELECT description
                FROM clans
                WHERE id = ?
                """;

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        String description = result.getString("description");
                        return description == null ? "" : description;
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal mengambil deskripsi clan", exception);
            }
        }

        return "";
    }

    public String getDescription(UUID playerUuid) {
        try {
            Clan clan = clanManager.getClanByPlayer(playerUuid);

            if (clan == null) {
                return "";
            }

            return getDescription(String.valueOf(clan.getId()));

        } catch (SQLException exception) {
            logError("Gagal mengambil deskripsi player", exception);
            return "";
        }
    }

    public boolean canManage(String role) {
        if (role == null) {
            return false;
        }

        return role.equalsIgnoreCase("LEADER")
                || role.equalsIgnoreCase("CO_LEADER");
    }

    public int getMaximumLength() {
        return MAX_LENGTH;
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                "[ClanDescription] " + message + ": "
                        + exception.getMessage()
        );
    }

    public enum Result {
        SUCCESS,
        NOT_IN_CLAN,
        NO_PERMISSION,
        INVALID_DESCRIPTION,
        TOO_LONG,
        CLAN_NOT_FOUND,
        DATABASE_ERROR
    }
}
