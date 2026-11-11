
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClanHomeManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;

    private final ConcurrentMap<UUID, Long> cooldowns =
            new ConcurrentHashMap<>();

    public ClanHomeManager(
            VortexClan plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public enum Result {
        SUCCESS,
        TELEPORT_STARTED,
        NOT_IN_CLAN,
        NO_PERMISSION,
        HOME_NOT_SET,
        WORLD_NOT_FOUND,
        COOLDOWN,
        TELEPORT_CANCELLED,
        DISABLED,
        DATABASE_ERROR
    }

    public Result setHome(Player player) {
        if (player == null) {
            return Result.DATABASE_ERROR;
        }

        if (!plugin.getConfig().getBoolean("clan.home.enabled", true)) {
            return Result.DISABLED;
        }

        UUID uuid = player.getUniqueId();

        synchronized (databaseManager.getLock()) {
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

                    statement.setString(1, uuid.toString());

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            return Result.NOT_IN_CLAN;
                        }

                        clanId = rs.getString("clan_id");
                        role = rs.getString("role");
                    }
                }

                if (!canSetHome(role)) {
                    return Result.NO_PERMISSION;
                }

                Location location = player.getLocation();

                if (location.getWorld() == null) {
                    return Result.WORLD_NOT_FOUND;
                }

                String updateSql = """
                        UPDATE clans
                        SET home_world = ?,
                            home_x = ?,
                            home_y = ?,
                            home_z = ?,
                            home_yaw = ?,
                            home_pitch = ?
                        WHERE id = ?
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {

                    statement.setString(
                            1,
                            location.getWorld().getName()
                    );

                    statement.setDouble(2, location.getX());
                    statement.setDouble(3, location.getY());
                    statement.setDouble(4, location.getZ());
                    statement.setFloat(5, location.getYaw());
                    statement.setFloat(6, location.getPitch());
                    statement.setString(7, clanId);

                    if (statement.executeUpdate() <= 0) {
                        return Result.DATABASE_ERROR;
                    }
                }

                player.sendMessage(message(
                        "&aClan Home berhasil diatur!"
                ));

                return Result.SUCCESS;

            } catch (SQLException exception) {
                logError("Gagal menyimpan Clan Home", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result teleportHome(Player player) {
        if (player == null) {
            return Result.DATABASE_ERROR;
        }

        if (!plugin.getConfig().getBoolean("clan.home.enabled", true)) {
            return Result.DISABLED;
        }

        UUID uuid = player.getUniqueId();

        long cooldownSeconds = Math.max(
                0,
                plugin.getConfig().getLong("clan.home.cooldown", 60)
        );

        long now = System.currentTimeMillis();

        Long lastTeleport = cooldowns.get(uuid);

        if (lastTeleport != null) {
            long remaining = cooldownSeconds * 1000L
                    - (now - lastTeleport);

            if (remaining > 0) {
                player.sendMessage(message(
                        "&cKamu harus menunggu &f"
                                + formatTime(remaining)
                                + " &csebelum menggunakan Clan Home lagi!"
                ));

                return Result.COOLDOWN;
            }
        }

        HomeData home;

        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT c.home_world,
                           c.home_x,
                           c.home_y,
                           c.home_z,
                           c.home_yaw,
                           c.home_pitch
                    FROM clan_members cm
                    JOIN clans c ON c.id = cm.clan_id
                    WHERE cm.uuid = ?
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, uuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Result.NOT_IN_CLAN;
                    }

                    String worldName = rs.getString("home_world");

                    if (worldName == null || worldName.isBlank()) {
                        return Result.HOME_NOT_SET;
                    }

                    home = new HomeData(
                            worldName,
                            rs.getDouble("home_x"),
                            rs.getDouble("home_y"),
                            rs.getDouble("home_z"),
                            rs.getFloat("home_yaw"),
                            rs.getFloat("home_pitch")
                    );
                }

            } catch (SQLException exception) {
                logError("Gagal mengambil Clan Home", exception);
                return Result.DATABASE_ERROR;
            }
        }

        World world = Bukkit.getWorld(home.world());

        if (world == null) {
            player.sendMessage(message(
                    "&cWorld Clan Home tidak ditemukan!"
            ));

            return Result.WORLD_NOT_FOUND;
        }

        Location destination = new Location(
                world,
                home.x(),
                home.y(),
                home.z(),
                home.yaw(),
                home.pitch()
        );

        int delay = Math.max(
                0,
                plugin.getConfig().getInt("clan.home.teleport-delay", 3)
        );

        if (delay == 0) {
            return performTeleport(player, destination);
        }

        Location startLocation = player.getLocation().clone();

        player.sendMessage(message(
                "&aTeleport ke Clan Home dalam &f"
                        + delay + " detik&a!"
        ));

        player.sendMessage(message(
                "&7Jangan bergerak selama proses teleport!"
        ));

        new BukkitRunnable() {

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                Location currentLocation = player.getLocation();

                if (hasMoved(startLocation, currentLocation)) {
                    player.sendMessage(message(
                            "&cTeleport Clan Home dibatalkan karena kamu bergerak!"
                    ));

                    cancel();
                    return;
                }

                performTeleport(player, destination);
                cancel();
            }

        }.runTaskLater(plugin, delay * 20L);

        return Result.TELEPORT_STARTED;
    }

    private Result performTeleport(
            Player player,
            Location destination
    ) {
        if (!player.isOnline()) {
            return Result.TELEPORT_CANCELLED;
        }

        boolean success = player.teleport(destination);

        if (!success) {
            player.sendMessage(message(
                    "&cTeleport Clan Home gagal!"
            ));

            return Result.TELEPORT_CANCELLED;
        }

        cooldowns.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );

        player.sendMessage(message(
                "&aBerhasil teleport ke Clan Home!"
        ));

        return Result.SUCCESS;
    }

    private boolean hasMoved(
            Location start,
            Location current
    ) {
        if (start.getWorld() == null || current.getWorld() == null) {
            return true;
        }

        if (!start.getWorld().equals(current.getWorld())) {
            return true;
        }

        return start.distanceSquared(current) > 0.01;
    }

    private boolean canSetHome(String role) {
        if (role == null) {
            return false;
        }

        return switch (role.toUpperCase().replace("-", "_")) {
            case "LEADER", "CO_LEADER", "COLEADER" -> true;
            default -> false;
        };
    }

    private String formatTime(long milliseconds) {
        long seconds = (milliseconds + 999) / 1000;

        if (seconds >= 60) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;

            return minutes + " menit " + remainingSeconds + " detik";
        }

        return seconds + " detik";
    }

    private String message(String text) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        return ChatColor.translateAlternateColorCodes(
                '&',
                prefix + " " + text
        );
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                message + ": " + exception.getMessage()
        );
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    private record HomeData(
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
    }
}
