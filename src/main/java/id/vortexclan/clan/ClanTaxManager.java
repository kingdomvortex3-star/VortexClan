
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;
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

public final class ClanTaxManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;

    public ClanTaxManager(
            VortexClan plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    private record DueClan(
            String id,
            String name,
            double bank,
            long taxDueAt
    ) {}

    public void processDueTaxes() {
        if (!plugin.getConfig().getBoolean(
                "clan.tax.enabled", true)) {
            return;
        }

        double taxAmount = plugin.getConfig().getDouble(
                "clan.tax.amount", 500
        );

        int intervalDays = plugin.getConfig().getInt(
                "clan.tax.interval-days", 7
        );

        if (!Double.isFinite(taxAmount)
                || taxAmount < 0
                || intervalDays < 1) {

            plugin.getLogger().severe(
                    "Invalid clan tax configuration!"
            );
            return;
        }

        long intervalMillis = intervalDays * 86400000L;

        try {
            List<DueClan> dueClans =
                    getDueClans(System.currentTimeMillis());

            for (DueClan clan : dueClans) {
                processClanTax(clan, taxAmount, intervalMillis);
            }

        } catch (SQLException exception) {
            logError("Failed to process clan taxes", exception);
        }
    }

    private List<DueClan> getDueClans(long now)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            List<DueClan> clans = new ArrayList<>();

            String sql = """
                    SELECT id, name, bank, tax_due_at
                    FROM clans
                    WHERE tax_due_at <= ?
                    ORDER BY tax_due_at ASC
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setLong(1, now);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        clans.add(new DueClan(
                                result.getString("id"),
                                result.getString("name"),
                                result.getDouble("bank"),
                                result.getLong("tax_due_at")
                        ));
                    }
                }
            }

            return clans;
        }
    }

    private void processClanTax(
            DueClan clan,
            double taxAmount,
            long intervalMillis
    ) {
        List<UUID> affectedMembers = new ArrayList<>();

        int payments = 0;
        boolean disbanded = false;
        boolean failed = false;

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            boolean oldAutoCommit;

            try {
                oldAutoCommit = connection.getAutoCommit();
            } catch (SQLException exception) {
                logError("Cannot read transaction state", exception);
                return;
            }

            try {
                connection.setAutoCommit(false);

                long now = System.currentTimeMillis();

                double currentBank;
                long currentDue;

                try (PreparedStatement statement =
                             connection.prepareStatement("""
                        SELECT bank, tax_due_at
                        FROM clans
                        WHERE id = ?
                        """)) {

                    statement.setString(1, clan.id());

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return;
                        }

                        currentBank = result.getDouble("bank");
                        currentDue = result.getLong("tax_due_at");
                    }
                }

                if (currentDue > now) {
                    connection.rollback();
                    return;
                }

                while (currentDue <= now) {

                    if (currentBank + 0.000001 < taxAmount) {

                        if (!plugin.getConfig().getBoolean(
                                "clan.tax.disband-if-unpaid", true)) {

                            currentDue = now + intervalMillis;
                            break;
                        }

                        affectedMembers = getMembers(
                                connection,
                                clan.id()
                        );

                        try (PreparedStatement wars =
                                     connection.prepareStatement("""
                                DELETE FROM clan_wars
                                WHERE clan_a = ? OR clan_b = ?
                                """)) {

                            wars.setString(1, clan.id());
                            wars.setString(2, clan.id());
                            wars.executeUpdate();
                        }

                        try (PreparedStatement delete =
                                     connection.prepareStatement("""
                                DELETE FROM clans
                                WHERE id = ?
                                """)) {

                            delete.setString(1, clan.id());
                            delete.executeUpdate();
                        }

                        disbanded = true;
                        break;
                    }

                    currentBank -= taxAmount;
                    currentDue += intervalMillis;
                    payments++;
                }

                if (!disbanded) {
                    try (PreparedStatement update =
                                 connection.prepareStatement("""
                            UPDATE clans
                            SET bank = ?, tax_due_at = ?
                            WHERE id = ?
                            """)) {

                        update.setDouble(1, currentBank);
                        update.setLong(2, currentDue);
                        update.setString(3, clan.id());

                        update.executeUpdate();
                    }

                    if (payments > 0) {
                        try (PreparedStatement history =
                                     connection.prepareStatement("""
                                INSERT INTO clan_transactions (
                                    clan_id,
                                    player_uuid,
                                    transaction_type,
                                    amount,
                                    balance_after,
                                    created_at
                                )
                                VALUES (?, ?, 'TAX', ?, ?, ?)
                                """)) {

                            double historyBalance = clan.bank();

                            for (int i = 0; i < payments; i++) {
                                historyBalance -= taxAmount;

                                history.setString(1, clan.id());
                                history.setString(2, "SYSTEM");
                                history.setDouble(3, taxAmount);
                                history.setDouble(4, historyBalance);
                                history.setLong(5, now);

                                history.executeUpdate();
                            }
                        }
                    }
                }

                connection.commit();

            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                logError(
                        "Tax processing failed for " + clan.name(),
                        exception
                );

                failed = true;

            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException exception) {
                    logError(
                            "Failed to restore database transaction",
                            exception
                    );
                }
            }
        }

        if (failed) {
            return;
        }

        if (disbanded) {
            notifyMembers(
                    affectedMembers,
                    "&cClan &b" + clan.name()
                            + " &cdibubarkan karena gagal membayar pajak!"
            );

            plugin.getLogger().warning(
                    "Clan " + clan.name()
                            + " was disbanded due to unpaid tax."
            );

        } else if (payments > 0) {
            notifyClan(
                    clan.id(),
                    "&aPajak clan &b" + clan.name()
                            + " &atelah dibayar otomatis sebanyak "
                            + payments + " periode."
            );
        }
    }

    private List<UUID> getMembers(
            Connection connection,
            String clanId
    ) throws SQLException {

        List<UUID> members = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement("""
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

        return members;
    }

    private void notifyClan(String clanId, String message) {
        try {
            List<UUID> members;

            synchronized (databaseManager.getLock()) {
                members = getMembers(
                        databaseManager.getConnection(),
                        clanId
                );
            }

            notifyMembers(members, message);

        } catch (SQLException exception) {
            logError("Failed to notify clan", exception);
        }
    }

    private void notifyMembers(
            List<UUID> members,
            String message
    ) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        String formatted = ChatColor.translateAlternateColorCodes(
                '&',
                prefix + " " + message
        );

        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                player.sendMessage(formatted);
            }
        }
    }

    private void logError(
            String message,
            Exception exception
    ) {
        plugin.getLogger().log(
                Level.SEVERE,
                message,
                exception
        );
    }
}
