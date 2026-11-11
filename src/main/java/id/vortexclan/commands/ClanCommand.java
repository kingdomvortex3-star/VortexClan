
package id.vortexclan.commands;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public final class ClanCommand implements CommandExecutor {

    private final VortexClan plugin;

    public ClanCommand(VortexClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "&cCommand ini hanya bisa digunakan oleh player!");
                return true;
            }

            showMainInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cCommand ini hanya bisa digunakan oleh player!");
                    return true;
                }

                if (!player.hasPermission("vortexclan.create")) {
                    sendMessage(player, "no-permission");
                    return true;
                }

                if (args.length < 3) {
                    send(player, "&cGunakan: /clan create <nama> <tag>");
                    return true;
                }

                createClan(player, args[1], args[2]);
                return true;
            }

            case "info" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cCommand ini hanya bisa digunakan oleh player!");
                    return true;
                }

                showClanInfo(player);
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("vortexclan.admin.reload")) {
                    sendMessage(sender, "no-permission");
                    return true;
                }

                plugin.reloadConfig();
                sendMessage(sender, "reload-success");
                return true;
            }

            default -> {
                send(sender, "&b&lVortexClan &7- &fCommands");
                send(sender, "&b/clan &7- Informasi clan");
                send(sender, "&b/clan create <nama> <tag> &7- Membuat clan");
                send(sender, "&b/clan info &7- Informasi clan kamu");
                send(sender, "&b/clan reload &7- Reload konfigurasi");
                return true;
            }
        }
    }

    private void createClan(
            Player player,
            String name,
            String tag
    ) {

        ClanManager.CreateResult result;

        try {
            result = plugin.getClanManager().createClan(
                    player.getUniqueId(),
                    name,
                    tag
            );

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Clan creation command failed: "
                            + exception.getMessage()
            );

            send(player, "&cTerjadi kesalahan saat membuat clan!");
            return;
        }

        switch (result) {

            case SUCCESS -> {
                sendMessage(player, "clan-created");
                send(player, "&7Nama: &b" + name);
                send(player, "&7Tag: &b" + tag.toUpperCase());
            }

            case DISABLED ->
                    send(player, "&cPembuatan clan sedang dinonaktifkan!");

            case ALREADY_IN_CLAN ->
                    sendMessage(player, "already-in-clan");

            case NAME_TAKEN ->
                    sendMessage(player, "name-taken");

            case TAG_TAKEN ->
                    sendMessage(player, "tag-taken");

            case INVALID_NAME ->
                    sendMessage(player, "invalid-name");

            case INVALID_TAG ->
                    sendMessage(player, "invalid-tag");

            case INSUFFICIENT_MONEY ->
                    sendMessage(player, "insufficient-money");

            case ECONOMY_UNAVAILABLE ->
                    send(player, "&cEconomy Vault tidak tersedia!");

            case DATABASE_ERROR ->
                    send(player, "&cGagal menyimpan data clan!");

        }
    }

    private void showMainInfo(Player player) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            send(player, "&b&lVortexClan");
            send(player, "&7Gunakan &b/clan create <nama> <tag>");
            send(player, "&7Untuk melihat informasi: &b/clan info");

            if (clan != null) {
                send(player, "&7Clan kamu: &b" + clan.getName());
            } else {
                send(player, "&7Status: &cBelum memiliki clan");
            }

        } catch (SQLException exception) {
            send(player, "&cGagal mengambil data clan!");
            plugin.getLogger().severe(exception.getMessage());
        }
    }

    private void showClanInfo(Player player) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                sendMessage(player, "no-clan");
                return;
            }

            int members = plugin.getClanManager()
                    .getMemberCount(clan.getId());

            int capacity = plugin.getClanManager()
                    .getCapacity(clan.getLevel());

            send(player, "&8&m-----------------------------");
            send(player, "&b&lCLAN INFORMATION");
            send(player, "&7Nama: &b" + clan.getName());
            send(player, "&7Tag: &b" + clan.getTag());
            send(player, "&7Leader: &f"
                    + playerName(clan.getLeader()));
            send(player, "&7Level: &b" + clan.getLevel());
            send(player, "&7EXP: &f" + clan.getExp());
            send(player, "&7Anggota: &b" + members
                    + "&7/&b" + capacity);
            send(player, "&7Bank: &a"
                    + plugin.getVaultManager().format(clan.getBank()));
            send(player, "&7Clan PvP: "
                    + (clan.isPvpEnabled()
                    ? "&aON" : "&cOFF"));
            send(player, "&8&m-----------------------------");

        } catch (SQLException exception) {
            send(player, "&cGagal mengambil informasi clan!");
            plugin.getLogger().severe(exception.getMessage());
        }
    }

    private String playerName(java.util.UUID uuid) {
        return org.bukkit.Bukkit.getOfflinePlayer(uuid).getName() != null
                ? org.bukkit.Bukkit.getOfflinePlayer(uuid).getName()
                : uuid.toString();
    }

    private void sendMessage(CommandSender sender, String key) {
        String message = plugin.getConfig().getString(
                "messages." + key,
                "&cMessage not configured: " + key
        );

        send(sender, message);
    }

    private void send(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        sender.sendMessage(color(prefix + " " + message));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
