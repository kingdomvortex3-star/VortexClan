
package id.vortexclan.commands;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanManager;
import id.vortexclan.clan.ClanMemberManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
                    send(sender, "&cPlayer only!");
                    return true;
                }

                if (!player.hasPermission("vortexclan.create")) {
                    message(player, "no-permission");
                    return true;
                }

                if (args.length < 3) {
                    send(player, "&cGunakan: /clan create <nama> <tag>");
                    return true;
                }

                createClan(player, args[1], args[2]);
            }

            case "info" -> {
                if (sender instanceof Player player) {
                    showClanInfo(player);
                } else {
                    send(sender, "&cPlayer only!");
                }
            }

            case "invite" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cPlayer only!");
                    return true;
                }

                if (args.length < 2) {
                    send(player, "&cGunakan: /clan invite <player>");
                    return true;
                }

                invitePlayer(player, args[1]);
            }

            case "accept" -> {
                if (sender instanceof Player player) {
                    acceptInvite(player);
                } else {
                    send(sender, "&cPlayer only!");
                }
            }

            case "deny" -> {
                if (sender instanceof Player player) {
                    denyInvite(player);
                } else {
                    send(sender, "&cPlayer only!");
                }
            }

            case "kick" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "&cPlayer only!");
                    return true;
                }

                if (args.length < 2) {
                    send(player, "&cGunakan: /clan kick <player>");
                    return true;
                }

                kickPlayer(player, args[1]);
            }

            case "leave" -> {
                if (sender instanceof Player player) {
                    leaveClan(player);
                } else {
                    send(sender, "&cPlayer only!");
                }
            }

            case "reload" -> {
                if (!sender.hasPermission("vortexclan.admin.reload")) {
                    message(sender, "no-permission");
                    return true;
                }

                plugin.reloadConfig();
                message(sender, "reload-success");
            }

            default -> showHelp(sender);
        }

        return true;
    }

    private void createClan(Player player, String name, String tag) {
        ClanManager.CreateResult result =
                plugin.getClanManager().createClan(
                        player.getUniqueId(),
                        name,
                        tag
                );

        switch (result) {
            case SUCCESS -> {
                message(player, "clan-created");
                send(player, "&7Nama: &b" + name);
                send(player, "&7Tag: &b" + tag.toUpperCase());
            }

            case DISABLED ->
                    send(player, "&cPembuatan clan dinonaktifkan!");

            case ALREADY_IN_CLAN ->
                    message(player, "already-in-clan");

            case NAME_TAKEN ->
                    message(player, "name-taken");

            case TAG_TAKEN ->
                    message(player, "tag-taken");

            case INVALID_NAME ->
                    message(player, "invalid-name");

            case INVALID_TAG ->
                    message(player, "invalid-tag");

            case INSUFFICIENT_MONEY ->
                    message(player, "insufficient-money");

            case ECONOMY_UNAVAILABLE ->
                    send(player, "&cVault Economy tidak tersedia!");

            case DATABASE_ERROR ->
                    send(player, "&cGagal menyimpan data clan!");
        }
    }

    private void invitePlayer(Player inviter, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            message(inviter, "player-not-found");
            return;
        }

        ClanMemberManager.Result result =
                plugin.getClanMemberManager().invite(
                        inviter.getUniqueId(),
                        target.getUniqueId()
                );

        switch (result) {
            case SUCCESS -> {
                send(inviter, "&aUndangan berhasil dikirim kepada &b"
                        + target.getName() + "&a!");

                send(target, "&aKamu menerima undangan clan!");
                send(target, "&7Gunakan &b/clan accept &7untuk menerima.");
                send(target, "&7Gunakan &c/clan deny &7untuk menolak.");
            }

            case NOT_IN_CLAN ->
                    message(inviter, "no-clan");

            case ALREADY_IN_CLAN ->
                    send(inviter, "&cPemain tersebut sudah memiliki clan!");

            case ALREADY_INVITED ->
                    send(inviter, "&cPemain tersebut sudah memiliki undangan!");

            case CLAN_FULL ->
                    message(inviter, "clan-full");

            case NO_PERMISSION ->
                    message(inviter, "no-permission");

            case PLAYER_NOT_FOUND ->
                    message(inviter, "player-not-found");

            default ->
                    send(inviter, "&cGagal mengirim undangan!");
        }
    }

    private void acceptInvite(Player player) {
        ClanMemberManager.Result result =
                plugin.getClanMemberManager().accept(
                        player.getUniqueId()
                );

        switch (result) {
            case SUCCESS -> {
                send(player, "&aKamu berhasil bergabung dengan clan!");

                try {
                    Clan clan = plugin.getClanManager()
                            .getClanByPlayer(player.getUniqueId());

                    if (clan != null) {
                        notifyClan(clan, "&b" + player.getName()
                                + " &atelah bergabung dengan clan!");
                    }
                } catch (SQLException exception) {
                    plugin.getLogger().severe(exception.getMessage());
                }
            }

            case NOT_IN_CLAN -> message(player, "no-clan");

            case ALREADY_IN_CLAN ->
                    message(player, "already-in-clan");

            case INVITE_NOT_FOUND ->
                    send(player, "&cKamu tidak memiliki undangan clan!");

            case INVITE_EXPIRED ->
                    send(player, "&cUndangan clan sudah kedaluwarsa!");

            case CLAN_FULL ->
                    message(player, "clan-full");

            default ->
                    send(player, "&cGagal menerima undangan!");
        }
    }

    private void denyInvite(Player player) {
        ClanMemberManager.Result result =
                plugin.getClanMemberManager().deny(
                        player.getUniqueId()
                );

        if (result == ClanMemberManager.Result.SUCCESS) {
            send(player, "&cUndangan clan berhasil ditolak!");
        } else {
            send(player, "&cTidak ada undangan clan yang tersedia!");
        }
    }

    private void kickPlayer(Player executor, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            message(executor, "player-not-found");
            return;
        }

        ClanMemberManager.Result result =
                plugin.getClanMemberManager().kick(
                        executor.getUniqueId(),
                        target.getUniqueId()
                );

        switch (result) {
            case SUCCESS -> {
                send(executor, "&aBerhasil mengeluarkan &b"
                        + target.getName() + " &adari clan!");

                send(target, "&cKamu telah dikeluarkan dari clan!");
            }

            case NOT_IN_CLAN ->
                    message(executor, "no-clan");

            case NO_PERMISSION ->
                    message(executor, "no-permission");

            case CANNOT_KICK_LEADER ->
                    send(executor, "&cLeader tidak dapat dikeluarkan!");

            case CANNOT_KICK_HIGHER_ROLE ->
                    send(executor, "&cKamu tidak dapat mengeluarkan anggota dengan role tersebut!");

            default ->
                    send(executor, "&cGagal mengeluarkan anggota!");
        }
    }

    private void leaveClan(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (SQLException exception) {
            send(player, "&cGagal mengambil data clan!");
            return;
        }

        if (clan == null) {
            message(player, "no-clan");
            return;
        }

        ClanMemberManager.Result result =
                plugin.getClanMemberManager().leave(
                        player.getUniqueId()
                );

        if (result == ClanMemberManager.Result.SUCCESS) {
            send(player, "&aKamu berhasil keluar dari clan!");
            notifyClan(clan, "&c" + player.getName()
                    + " &7telah keluar dari clan!");
        } else if (result ==
                ClanMemberManager.Result.LEADER_CANNOT_LEAVE) {
            send(player, "&cLeader tidak bisa keluar begitu saja!");
            send(player, "&7Transfer kepemimpinan atau bubarkan clan.");
        } else {
            send(player, "&cGagal keluar dari clan!");
        }
    }

    private void showMainInfo(Player player) {
        send(player, "&8&m------------------------------");
        send(player, "&b&lVORTEX CLAN");
        send(player, "&7Gunakan &b/clan info &7untuk informasi clan.");
        send(player, "&7Gunakan &b/clan create <nama> <tag>");
        send(player, "&8&m------------------------------");
    }

    private void showClanInfo(Player player) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                message(player, "no-clan");
                return;
            }

            int members = plugin.getClanManager()
                    .getMemberCount(clan.getId());

            int capacity = plugin.getClanManager()
                    .getCapacity(clan.getLevel());

            OfflinePlayer leader =
                    Bukkit.getOfflinePlayer(clan.getLeader());

            String leaderName = leader.getName() != null
                    ? leader.getName()
                    : "Unknown";

            send(player, "&8&m------------------------------");
            send(player, "&b&lCLAN INFORMATION");
            send(player, "&7Nama: &b" + clan.getName());
            send(player, "&7Tag: &b" + clan.getTag());
            send(player, "&7Leader: &f" + leaderName);
            send(player, "&7Level: &b" + clan.getLevel());
            send(player, "&7EXP: &f" + clan.getExp());
            send(player, "&7Anggota: &b" + members
                    + "&7/&b" + capacity);
            send(player, "&7Bank: &a"
                    + plugin.getVaultManager().format(clan.getBank()));
            send(player, "&7PvP: "
                    + (clan.isPvpEnabled() ? "&aON" : "&cOFF"));
            send(player, "&8&m------------------------------");

        } catch (SQLException exception) {
            plugin.getLogger().severe(exception.getMessage());
            send(player, "&cGagal mengambil informasi clan!");
        }
    }

    private void notifyClan(Clan clan, String text) {
        try {
            for (Player online : Bukkit.getOnlinePlayers()) {
                Clan memberClan = plugin.getClanManager()
                        .getClanByPlayer(online.getUniqueId());

                if (memberClan != null
                        && memberClan.getId().equals(clan.getId())) {
                    send(online, text);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe(exception.getMessage());
        }
    }

    private void showHelp(CommandSender sender) {
        send(sender, "&8&m------------------------------");
        send(sender, "&b&lVORTEX CLAN COMMANDS");
        send(sender, "&b/clan &7- Main menu");
        send(sender, "&b/clan create <nama> <tag> &7- Create clan");
        send(sender, "&b/clan info &7- Clan information");
        send(sender, "&b/clan invite <player> &7- Invite player");
        send(sender, "&b/clan accept &7- Accept invitation");
        send(sender, "&b/clan deny &7- Deny invitation");
        send(sender, "&b/clan kick <player> &7- Kick member");
        send(sender, "&b/clan leave &7- Leave clan");
        send(sender, "&b/clan reload &7- Reload config");
        send(sender, "&8&m------------------------------");
    }

    private void message(CommandSender sender, String key) {
        String text = plugin.getConfig().getString(
                "messages." + key,
                "&cMessage not configured: " + key
        );

        send(sender, text);
    }

    private void send(CommandSender sender, String text) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        sender.sendMessage(color(prefix + " " + text));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
