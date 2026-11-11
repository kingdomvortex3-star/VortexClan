
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ClanManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final Economy economy;

    private static final long DAY_MILLIS = 86400000L;

    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final Pattern TAG_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]+$");

    public enum CreateResult {
        SUCCESS,
        DISABLED,
        ALREADY_IN_CLAN,
        NAME_TAKEN,
        TAG_TAKEN,
        INVALID_NAME,
        INVALID_TAG,
        INSUFFICIENT_MONEY,
        ECONOMY_UNAVAILABLE,
        DATABASE_ERROR
    }

    public ClanManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            Economy economy
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economy = economy;
    }

    public CreateResult createClan(
            UUID playerUUID,
            String name,
            String tag
    ) {
        if (!plugin.getConfig().getBoolean(
                "clan.creation.enabled", true)) {
            return CreateResult.DISABLED;
        }

        if (economy == null) {
            return CreateResult.ECONOMY_UNAVAILABLE;
        }

        if (playerUUID == null || name == null || tag == null) {
            return CreateResult.INVALID_NAME;
        }

        name = name.trim();
        tag = tag.trim().toUpperCase();

        int minName = plugin.getConfig().getInt(
                "clan.creation.minimum-name-length", 3
        );

        int maxName = plugin.getConfig().getInt(
                "clan.creation.maximum-name-length", 16
        );

        int minTag = plugin.getConfig().getInt(
                "clan.creation.minimum-tag-length", 2
        );

        int maxTag = plugin.getConfig().getInt(
                "clan.creation.maximum-tag-length", 5
        );

        if (name.length() < minName
                || name.length() > maxName
                || !NAME_PATTERN.matcher(name).matches()) {
            return CreateResult.INVALID_NAME;
        }

        if (tag.length() < minTag
                || tag.length() > maxTag
                || !TAG_PATTERN.matcher(tag).matches()) {
            return CreateResult.INVALID_TAG;
        }

        double cost = plugin.getConfig().getDouble(
                "clan.creation.cost", 1000
        );

        double startingBalance = plugin.getConfig().getDouble(
                "clan.bank.starting-balance", 0
        );

        if (!Double.isFinite(cost) || cost < 0
                || !Double.isFinite(startingBalance)
                || startingBalance < 0) {
            return CreateResult.DATABASE_ERROR;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                if (getClanByPlayer(playerUUID) != null) {
                    return CreateResult.ALREADY_IN_CLAN;
                }

                if (getClanByName(name) != null) {
                    return CreateResult.NAME_TAKEN;
                }

                if (getClanByTag(tag) != null) {
                    return CreateResult.TAG_TAKEN;
                }

            } catch (SQLException exception) {
                logError("Failed to check clan information", exception);
                return CreateResult.DATABASE_ERROR;
            }

            if (!economy.has(player, cost)) {
                return CreateResult.INSUFFICIENT_MONEY;
            }

            EconomyResponse payment =
                    economy.withdrawPlayer(player, cost);

            if (!payment.transactionSuccess()) {
                return CreateResult.INSUFFICIENT_MONEY;
            }

            UUID clanUUID = UUID.randomUUID();

            long now = System.currentTimeMillis();

            int taxDays = Math.max(
                    1,
                    plugin.getConfig().getInt(
                            "clan.tax.interval-days", 7
                    )
            );

            long taxDue = now + (taxDays * DAY_MILLIS);

            boolean oldAutoCommit;

            try {
                oldAutoCommit = connection.getAutoCommit();
            } catch (SQLException exception) {
                refund(player, cost);
                logError("Failed to access database transaction", exception);
                return CreateResult.DATABASE_ERROR;
            }

            boolean committed = false;

            try {
                connection.setAutoCommit(false);

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        INSERT INTO clans (
                            id,
                            name,
                            tag,
                            leader_uuid,
                            description,
                            level,
                            exp,
                            bank,
                            pvp_enabled,
                            created_at,
                            tax_due_at
                        )
                        VALUES (?, ?, ?, ?, '', 1, 0, ?, ?, ?, ?)
                        """)) {

                    statement.setString(1, clanUUID.toString());
                    statement.setString(2, name);
                    statement.setString(3, tag);
                    statement.setString(4, playerUUID.toString());
                    statement.setDouble(5, startingBalance);

                    statement.setInt(
                            6,
                            plugin.getConfig().getBoolean(
                                    "clan.pvp.default", false
                            ) ? 1 : 0
                    );

                    statement.setLong(7, now);
                    statement.setLong(8, taxDue);

                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        INSERT INTO clan_members (
                            uuid,
                            clan_id,
                            role,
                            joined_at
                        )
                        VALUES (?, ?, 'LEADER', ?)
                        """)) {

                    statement.setString(1, playerUUID.toString());
                    statement.setString(2, clanUUID.toString());
                    statement.setLong(3, now);

                    statement.executeUpdate();
                }

                connection.commit();
                committed = true;

                return CreateResult.SUCCESS;

            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                logError("Failed to create clan", exception);

                return CreateResult.DATABASE_ERROR;

            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException exception) {
                    logError("Failed to restore database state", exception);
                }

                if (!committed) {
                    refund(player, cost);
                }
            }
        }
    }

    private void refund(OfflinePlayer player, double amount) {
        EconomyResponse response =
                economy.depositPlayer(player, amount);

        if (!response.transactionSuccess()) {
            plugin.getLogger().severe(
                    "CRITICAL: Failed to refund clan creation payment "
                            + "for " + player.getUniqueId()
            );
        }
    }

    public Clan getClanByPlayer(UUID playerUUID)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT c.*
                    FROM clans c
                    INNER JOIN clan_members m
                    ON c.id = m.clan_id
                    WHERE m.uuid = ?
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, playerUUID.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return mapClan(result);
                    }
                }
            }

            return null;
        }
    }

    public Clan getClanById(String clanId)
            throws SQLException {

        return findClan(
                "SELECT * FROM clans WHERE id = ?",
                clanId
        );
    }

    public Clan getClanByName(String name)
            throws SQLException {

        return findClan(
                "SELECT * FROM clans WHERE name = ? COLLATE NOCASE",
                name
        );
    }

    public Clan getClanByTag(String tag)
            throws SQLException {

        return findClan(
                "SELECT * FROM clans WHERE tag = ? COLLATE NOCASE",
                tag
        );
    }

    private Clan findClan(String sql, String value)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, value);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return mapClan(result);
                    }
                }
            }

            return null;
        }
    }

    private Clan mapClan(ResultSet result)
            throws SQLException {

        return new Clan(
                result.getString("id"),
                result.getString("name"),
                result.getString("tag"),
                UUID.fromString(result.getString("leader_uuid")),
                result.getString("description"),
                result.getInt("level"),
                result.getLong("exp"),
                result.getDouble("bank"),
                result.getInt("pvp_enabled") == 1,
                result.getInt("wins"),
                result.getInt("losses"),
                result.getInt("kills"),
                result.getLong("created_at"),
                result.getLong("tax_due_at")
        );
    }

    public int getMemberCount(String clanId)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT COUNT(*)
                    FROM clan_members
                    WHERE clan_id = ?
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        }
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

        return (int) Math.min(maximum, Math.max(0, capacity));
    }

    public boolean isLeader(UUID playerUUID, String clanId)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            Clan clan = getClanById(clanId);

            return clan != null
                    && clan.getLeader().equals(playerUUID);
        }
    }

    public String getMemberRole(UUID playerUUID)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT role
                    FROM clan_members
                    WHERE uuid = ?
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, playerUUID.toString());

                try (ResultSet result = statement.executeQuery()) {
                    return result.next()
                            ? result.getString("role")
                            : null;
                }
            }
        }
    }

    private void logError(String message, Exception exception) {
        plugin.getLogger().severe(
                message + ": " + exception.getMessage()
        );
    }
}
