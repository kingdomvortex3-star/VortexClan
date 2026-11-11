
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanBankManager {

    private final VortexClan plugin;
    private final ClanManager clanManager;
    private final Economy economy;

    public ClanBankManager(
            VortexClan plugin,
            ClanManager clanManager,
            Economy economy
    ) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.economy = economy;
    }

    public enum Result {
        SUCCESS,
        NO_CLAN,
        ECONOMY_UNAVAILABLE,
        INVALID_AMOUNT,
        INSUFFICIENT_MONEY,
        INSUFFICIENT_BANK,
        NO_PERMISSION,
        DISABLED,
        DATABASE_ERROR
    }

    public record TransactionEntry(
            String playerUUID,
            String type,
            double amount,
            double balanceAfter,
            long createdAt
    ) {}

    public synchronized Result deposit(
            UUID playerUUID,
            double amount
    ) {
        if (!plugin.getConfig().getBoolean(
                "clan.bank.enabled", true)) {
            return Result.DISABLED;
        }

        if (!plugin.getConfig().getBoolean(
                "clan.bank.allow-deposit", true)) {
            return Result.DISABLED;
        }

        if (economy == null) {
            return Result.ECONOMY_UNAVAILABLE;
        }

        if (!validAmount(amount)) {
            return Result.INVALID_AMOUNT;
        }

        try {
            Clan clan = clanManager.getClanByPlayer(playerUUID);

            if (clan == null) {
                return Result.NO_CLAN;
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);

            if (!economy.has(player, amount)) {
                return Result.INSUFFICIENT_MONEY;
            }

            EconomyResponse payment =
                    economy.withdrawPlayer(player, amount);

            if (!payment.transactionSuccess()) {
                return Result.INSUFFICIENT_MONEY;
            }

            try {
                updateBalance(
                        clan.getId(),
                        playerUUID,
                        amount,
                        "DEPOSIT"
                );

                return Result.SUCCESS;

            } catch (SQLException exception) {
                refund(player, amount);
                logError("Deposit transaction failed", exception);
                return Result.DATABASE_ERROR;
            }

        } catch (SQLException exception) {
            logError("Failed to deposit clan bank", exception);
            return Result.DATABASE_ERROR;
        }
    }

    public synchronized Result withdraw(
            UUID playerUUID,
            double amount
    ) {
        if (!plugin.getConfig().getBoolean(
                "clan.bank.enabled", true)) {
            return Result.DISABLED;
        }

        if (!plugin.getConfig().getBoolean(
                "clan.bank.allow-withdraw", true)) {
            return Result.DISABLED;
        }

        if (economy == null) {
            return Result.ECONOMY_UNAVAILABLE;
        }

        if (!validAmount(amount)) {
            return Result.INVALID_AMOUNT;
        }

        try {
            Clan clan = clanManager.getClanByPlayer(playerUUID);

            if (clan == null) {
                return Result.NO_CLAN;
            }

            String role = clanManager.getMemberRole(playerUUID);

            if (!"LEADER".equalsIgnoreCase(role)
                    && !"CO_LEADER".equalsIgnoreCase(role)) {
                return Result.NO_PERMISSION;
            }

            updateBalance(
                    clan.getId(),
                    playerUUID,
                    -amount,
                    "WITHDRAW"
            );

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);

            EconomyResponse payment =
                    economy.depositPlayer(player, amount);

            if (!payment.transactionSuccess()) {
                try {
                    updateBalance(
                            clan.getId(),
                            playerUUID,
                            amount,
                            "WITHDRAW_REFUND"
                    );
                } catch (SQLException refundException) {
                    logError(
                            "CRITICAL: Failed to restore clan bank balance",
                            refundException
                    );
                }

                return Result.DATABASE_ERROR;
            }

            return Result.SUCCESS;

        } catch (SQLException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("INSUFFICIENT_BANK")) {
                return Result.INSUFFICIENT_BANK;
            }

            logError("Failed to withdraw clan bank", exception);
            return Result.DATABASE_ERROR;
        }
    }

    private void updateBalance(
            String clanId,
            UUID playerUUID,
            double amount,
            String type
    ) throws SQLException {

        Connection connection =
                plugin.getDatabaseManager().getConnection();

        boolean oldAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            String updateSql;

            if (amount >= 0) {
                updateSql = """
                    UPDATE clans
                    SET bank = bank + ?
                    WHERE id = ?
                    """;
            } else {
                updateSql = """
                    UPDATE clans
                    SET bank = bank - ?
                    WHERE id = ? AND bank >= ?
                    """;
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(updateSql)) {

                statement.setDouble(1, Math.abs(amount));
                statement.setString(2, clanId);

                if (amount < 0) {
                    statement.setDouble(3, Math.abs(amount));
                }

                int updated = statement.executeUpdate();

                if (updated == 0) {
                    throw new SQLException(
                            "INSUFFICIENT_BANK or clan not found"
                    );
                }
            }

            double newBalance;

            try (PreparedStatement statement =
                         connection.prepareStatement("""
                    SELECT bank FROM clans WHERE id = ?
                    """)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Clan not found!");
                    }

                    newBalance = result.getDouble("bank");
                }
            }

            try (PreparedStatement statement =
                         connection.prepareStatement("""
                    INSERT INTO clan_transactions (
                        clan_id,
                        player_uuid,
                        transaction_type,
                        amount,
                        balance_after,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {

                statement.setString(1, clanId);
                statement.setString(2, playerUUID.toString());
                statement.setString(3, type);
                statement.setDouble(4, Math.abs(amount));
                statement.setDouble(5, newBalance);
                statement.setLong(6, System.currentTimeMillis());

                statement.executeUpdate();
            }

            connection.commit();

        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }

            throw exception;

        } finally {
            try {
                connection.setAutoCommit(oldAutoCommit);
            } catch (SQLException exception) {
                logError("Failed to restore transaction state", exception);
            }
        }
    }

    public synchronized List<TransactionEntry> getHistory(
            String clanId,
            int limit
    ) throws SQLException {

        List<TransactionEntry> history = new ArrayList<>();

        int safeLimit = Math.max(1, Math.min(limit, 100));

        try (PreparedStatement statement =
                     plugin.getDatabaseManager()
                             .getConnection()
                             .prepareStatement("""
                    SELECT player_uuid,
                           transaction_type,
                           amount,
                           balance_after,
                           created_at
                    FROM clan_transactions
                    WHERE clan_id = ?
                    ORDER BY created_at DESC
                    LIMIT ?
                    """)) {

            statement.setString(1, clanId);
            statement.setInt(2, safeLimit);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    history.add(new TransactionEntry(
                            result.getString("player_uuid"),
                            result.getString("transaction_type"),
                            result.getDouble("amount"),
                            result.getDouble("balance_after"),
                            result.getLong("created_at")
                    ));
                }
            }
        }

        return history;
    }

    public double getBalance(String clanId) throws SQLException {
        try (PreparedStatement statement =
                     plugin.getDatabaseManager()
                             .getConnection()
                             .prepareStatement("""
                    SELECT bank FROM clans WHERE id = ?
                    """)) {

            statement.setString(1, clanId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? result.getDouble("bank")
                        : 0;
            }
        }
    }

    private void refund(OfflinePlayer player, double amount) {
        EconomyResponse response =
                economy.depositPlayer(player, amount);

        if (!response.transactionSuccess()) {
            plugin.getLogger().severe(
                    "CRITICAL: Failed to refund " + amount
                            + " to " + player.getUniqueId()
            );
        }
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    private void logError(String message, Exception exception) {
        plugin.getLogger().severe(
                message + ": " + exception.getMessage()
        );
    }
}
