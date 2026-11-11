
package id.vortexclan.clan;

import id.vortexclan.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ClanPvPManager {

    private final DatabaseManager databaseManager;

    public ClanPvPManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public enum Result {
        SUCCESS_ENABLED,
        SUCCESS_DISABLED,
        NOT_IN_CLAN,
        NO_PERMISSION,
        DATABASE_ERROR
    }

    public synchronized Result toggle(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return Result.NOT_IN_CLAN;
        }

        Connection connection = databaseManager.getConnection();

        try {
            String clanId;
            String role;

            String memberSql = """
                    SELECT clan_id, role
                    FROM clan_members
                    WHERE uuid = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(memberSql)) {

                statement.setString(1, playerUuid);

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Result.NOT_IN_CLAN;
                    }

                    clanId = rs.getString("clan_id");
                    role = rs.getString("role");
                }
            }

            if (!canManagePvP(role)) {
                return Result.NO_PERMISSION;
            }

            boolean currentStatus;

            String clanSql = """
                    SELECT pvp_enabled
                    FROM clans
                    WHERE id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(clanSql)) {

                statement.setString(1, clanId);

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Result.NOT_IN_CLAN;
                    }

                    currentStatus = rs.getInt("pvp_enabled") == 1;
                }
            }

            boolean newStatus = !currentStatus;

            String updateSql = """
                    UPDATE clans
                    SET pvp_enabled = ?
                    WHERE id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(updateSql)) {

                statement.setInt(1, newStatus ? 1 : 0);
                statement.setString(2, clanId);

                if (statement.executeUpdate() <= 0) {
                    return Result.DATABASE_ERROR;
                }
            }

            return newStatus
                    ? Result.SUCCESS_ENABLED
                    : Result.SUCCESS_DISABLED;

        } catch (SQLException exception) {
            exception.printStackTrace();
            return Result.DATABASE_ERROR;
        }
    }

    public boolean canManagePvP(String role) {
        if (role == null) {
            return false;
        }

        return switch (role.toUpperCase().replace("-", "_")) {
            case "LEADER", "CO_LEADER", "COLEADER" -> true;
            default -> false;
        };
    }

    public boolean isEnabled(String clanId) throws SQLException {
        String sql = """
                SELECT pvp_enabled
                FROM clans
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, clanId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt("pvp_enabled") == 1;
            }
        }
    }
}
