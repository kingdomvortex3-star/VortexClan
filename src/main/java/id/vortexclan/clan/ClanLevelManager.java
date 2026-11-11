
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class ClanLevelManager {

    private final VortexClan plugin;

    public ClanLevelManager(VortexClan plugin) {
        this.plugin = plugin;
    }

    public enum Result {
        SUCCESS,
        CLAN_NOT_FOUND,
        INVALID_EXP,
        MAX_LEVEL,
        DATABASE_ERROR
    }

    public record LevelResult(
            Result result,
            int oldLevel,
            int newLevel,
            long remainingExp,
            long totalExp
    ) {}

    public synchronized LevelResult addExp(
            String clanId,
            long amount
    ) {

        if (amount <= 0) {
            return new LevelResult(
                    Result.INVALID_EXP, 0, 0, 0, 0
            );
        }

        Connection connection =
                plugin.getDatabaseManager().getConnection();

        boolean oldAutoCommit;

        try {
            oldAutoCommit = connection.getAutoCommit();
        } catch (SQLException exception) {
            logError("Cannot read transaction state", exception);
            return failure(Result.DATABASE_ERROR);
        }

        int oldLevel;
        int newLevel;
        long totalExp;
        long remainingExp;

        try {
            connection.setAutoCommit(false);

            try {
                int maxLevel = Math.max(
                        1,
                        plugin.getConfig().getInt(
                                "clan.level.maximum-level", 100
                        )
                );

                int expPerLevel = Math.max(
                        1,
                        plugin.getConfig().getInt(
                                "clan.level.exp-per-level", 1000
                        )
                );

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        SELECT level, exp
                        FROM clans
                        WHERE id = ?
                        """)) {

                    statement.setString(1, clanId);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();

                            return failure(
                                    Result.CLAN_NOT_FOUND
                            );
                        }

                        oldLevel = result.getInt("level");
                        totalExp = result.getLong("exp");
                    }
                }

                if (oldLevel >= maxLevel) {
                    connection.rollback();

                    return new LevelResult(
                            Result.MAX_LEVEL,
                            oldLevel,
                            oldLevel,
                            totalExp,
                            totalExp
                    );
                }

                if (totalExp > Long.MAX_VALUE - amount) {
                    connection.rollback();
                    return failure(Result.INVALID_EXP);
                }

                totalExp += amount;

                newLevel = oldLevel;

                remainingExp = totalExp;

                while (newLevel < maxLevel
                        && remainingExp >= expPerLevel) {

                    remainingExp -= expPerLevel;
                    newLevel++;
                }

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        UPDATE clans
                        SET level = ?, exp = ?
                        WHERE id = ?
                        """)) {

                    statement.setInt(1, newLevel);
                    statement.setLong(2, remainingExp);
                    statement.setString(3, clanId);

                    statement.executeUpdate();
                }

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }

        } catch (SQLException exception) {
            logError("Failed to add clan EXP", exception);
            return failure(Result.DATABASE_ERROR);

        } finally {
            try {
                connection.setAutoCommit(oldAutoCommit);
            } catch (SQLException exception) {
                logError("Failed to restore transaction state", exception);
            }
        }

        if (newLevel > oldLevel) {
            notifyLevelUp(clanId, oldLevel, newLevel);
        }

        return new LevelResult(
                Result.SUCCESS,
                oldLevel,
                newLevel,
                remainingExp,
                totalExp
        );
    }

    public synchronized long getExp(String clanId)
            throws SQLException {

        try (PreparedStatement statement =
                     plugin.getDatabaseManager()
                             .getConnection()
                             .prepareStatement("""
                    SELECT exp FROM clans WHERE id = ?
                    """)) {

            statement.setString(1, clanId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? result.getLong("exp")
                        : 0;
            }
        }
    }

    public synchronized int getLevel(String clanId)
            throws SQLException {

        try (PreparedStatement statement =
                     plugin.getDatabaseManager()
                             .getConnection()
                             .prepareStatement("""
                    SELECT level FROM clans WHERE id = ?
                    """)) {

            statement.setString(1, clanId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? result.getInt("level")
                        : 0;
            }
        }
    }

    public long getRequiredExp(int currentLevel) {
        return Math.max(
                1,
                plugin.getConfig().getLong(
                        "clan.level.exp-per-level", 1000
                )
        );
    }

    public int getCapacity(int level) {
        int base = plugin.getConfig().getInt(
                "clan.capacity.default", 5
        );

        int maximum = plugin.getConfig().getInt(
                "clan.capacity.maximum", 100
        );

        int bonus = plugin.getConfig().getInt(
                "clan.level.capacity-per-level", 1
        );

        int startingLevel = plugin.getConfig().getInt(
                "clan.level.starting-level", 1
        );

        long capacity = base
                + (long) Math.max(0, level - startingLevel) * bonus;

        return (int) Math.min(
                maximum,
                Math.max(0, capacity)
        );
    }

    public double getProgress(String clanId)
            throws SQLException {

        long exp = getExp(clanId);

        long required = getRequiredExp(
                getLevel(clanId)
        );

        return Math.min(
                100.0,
                Math.max(0.0, exp * 100.0 / required)
        );
    }

    private void notifyLevelUp(
            String clanId,
            int oldLevel,
            int newLevel
    ) {

        try {
            List<UUID> members = new ArrayList<>();

            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement("""
                        SELECT uuid
                        FROM clan_members
                        WHERE clan_id = ?
                        """)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        members.add(UUID.fromString(
                                result.getString("uuid")
                        ));
                    }
                }
            }

            String prefix = plugin.getConfig().getString(
                    "settings.prefix",
                    "&8[&bVortexClan&8]"
            );

            String message = color(
                    prefix + " &aClan naik level! &b"
                            + oldLevel + " &7➜ &b"
                            + newLevel
            );

            for (UUID uuid : members) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            }

        } catch (SQLException exception) {
            logError("Failed to notify clan level up", exception);
        }
    }

    private LevelResult failure(Result result) {
        return new LevelResult(
                result, 0, 0, 0, 0
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void logError(String message, Exception exception) {
        plugin.getLogger().log(
                Level.SEVERE,
                message,
                exception
        );
    }
}
