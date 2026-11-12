
package id.vortexclan.commands;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanWarManager;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ClanWarCommand {

    private final VortexClan plugin;
    private final ClanWarManager warManager;

    public ClanWarCommand(
            VortexClan plugin,
            ClanWarManager warManager
    ) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(
                    "&cCommand ini hanya dapat digunakan oleh player."
            ));
            return true;
        }

        if (!plugin.getConfig().getBoolean("war.enabled", true)) {
            send(player, "&cSistem Clan War sedang dinonaktifkan.");
            return true;
        }

        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "challenge" -> {
                if (args.length < 3) {
                    send(player,
                            "&cGunakan: &f/clan war challenge <nama-clan>");
                    return true;
                }

                challenge(player, joinArgs(args, 2));
            }

            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "status" -> status(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void challenge(Player player, String targetClan) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

        } catch (SQLException exception) {
            databaseError(player, exception);
            return;
        }

        if (clan == null) {
            send(player, "&cKamu belum memiliki clan.");
            return;
        }

        ClanWarManager.Result result = warManager.challenge(
                player.getUniqueId(),
                targetClan
        );

        switch (result) {
            case CHALLENGE_SENT ->
                    send(player,
                            "&aTantangan Clan War berhasil dikirim kepada &f"
                                    + targetClan + "&a!");

            case NOT_IN_CLAN ->
                    send(player, "&cKamu belum memiliki clan.");

            case NO_PERMISSION ->
                    send(player,
                            "&cHanya Leader atau Co-Leader yang dapat menantang clan.");

            case CLAN_NOT_FOUND ->
                    send(player,
                            "&cClan &f" + targetClan + " &ctidak ditemukan.");

            case SAME_CLAN ->
                    send(player,
                            "&cKamu tidak bisa menantang clan sendiri.");

            case ALREADY_IN_WAR ->
                    send(player,
                            "&cClan kamu atau clan tujuan sedang memiliki perang/tantangan lain.");

            case DATABASE_ERROR ->
                    send(player, "&cTerjadi kesalahan database.");

            default ->
                    send(player,
                            "&cGagal mengirim tantangan: &f" + result.name());
        }
    }

    private void accept(Player player) {
        ClanWarManager.Result result =
                warManager.accept(player.getUniqueId());

        switch (result) {
            case WAR_ACCEPTED ->
                    send(player,
                            "&aTantangan Clan War berhasil diterima!");

            case NOT_IN_CLAN ->
                    send(player, "&cKamu belum memiliki clan.");

            case NO_PERMISSION ->
                    send(player,
                            "&cHanya Leader atau Co-Leader yang dapat menerima tantangan.");

            case CHALLENGE_NOT_FOUND ->
                    send(player,
                            "&cTidak ada tantangan Clan War yang menunggu.");

            case ALREADY_IN_WAR ->
                    send(player,
                            "&cClan kamu sedang memiliki perang atau tantangan lain.");

            case DATABASE_ERROR ->
                    send(player, "&cTerjadi kesalahan database.");

            default ->
                    send(player,
                            "&cGagal menerima tantangan: &f" + result.name());
        }
    }

    private void deny(Player player) {
        ClanWarManager.Result result =
                warManager.deny(player.getUniqueId());

        switch (result) {
            case WAR_DENIED ->
                    send(player,
                            "&cTantangan Clan War berhasil ditolak.");

            case NOT_IN_CLAN ->
                    send(player, "&cKamu belum memiliki clan.");

            case NO_PERMISSION ->
                    send(player,
                            "&cHanya Leader atau Co-Leader yang dapat menolak tantangan.");

            case CHALLENGE_NOT_FOUND ->
                    send(player,
                            "&cTidak ada tantangan Clan War yang menunggu.");

            case DATABASE_ERROR ->
                    send(player, "&cTerjadi kesalahan database.");

            default ->
                    send(player,
                            "&cGagal menolak tantangan: &f" + result.name());
        }
    }

    private void status(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                send(player, "&cKamu belum memiliki clan.");
                return;
            }

            ClanWarManager.WarData war =
                    warManager.getActiveWar(clan.getId());

            send(player, "&8&m--------------------------------");
            send(player, "&b&lVORTEX CLAN WAR");
            send(player, "");

            if (war == null) {
                send(player, "&7Status: &cTidak ada perang aktif.");
            } else {
                send(player, "&7Status: &aPerang berlangsung");
                send(player, "&7Clan A: &f" + war.clanA());
                send(player, "&7Clan B: &f" + war.clanB());
                send(player, "");
                send(player, "&7Kill Clan A: &a" + war.killsA());
                send(player, "&7Kill Clan B: &c" + war.killsB());
            }

            send(player, "");
            send(player, "&8&m--------------------------------");

        } catch (SQLException exception) {
            databaseError(player, exception);
        }
    }

    private void sendHelp(Player player) {
        send(player, "&8&m--------------------------------");
        send(player, "&b&lVORTEX CLAN WAR");
        send(player, "");
        send(player,
                "&b/clan war challenge <clan> &7- Tantang clan");
        send(player,
                "&b/clan war accept &7- Terima tantangan");
        send(player,
                "&b/clan war deny &7- Tolak tantangan");
        send(player,
                "&b/clan war status &7- Lihat status perang");
        send(player, "");
        send(player, "&8&m--------------------------------");
    }

    public List<String> tabComplete(Player player, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 2) {
            completions.add("challenge");
            completions.add("accept");
            completions.add("deny");
            completions.add("status");

        } else if (args.length == 3
                && args[1].equalsIgnoreCase("challenge")) {

            String input = args[2].toLowerCase();

            try {
                synchronized (plugin.getDatabaseManager().getLock()) {
                    String sql = """
                            SELECT name, tag
                            FROM clans
                            WHERE leader_uuid != ?
                            ORDER BY name ASC
                            """;

                    try (var statement = plugin.getDatabaseManager()
                            .getConnection().prepareStatement(sql)) {

                        statement.setString(
                                1,
                                player.getUniqueId().toString()
                        );

                        try (var result = statement.executeQuery()) {
                            while (result.next()) {
                                String name = result.getString("name");
                                String tag = result.getString("tag");

                                if (name.toLowerCase().startsWith(input)) {
                                    completions.add(name);
                                }

                                if (tag.toLowerCase().startsWith(input)) {
                                    completions.add(tag);
                                }
                            }
                        }
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().warning(
                        "Gagal mengambil tab completion Clan War: "
                                + exception.getMessage()
                );
            }
        }

        return completions;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();

        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(" ");
            }

            builder.append(args[i]);
        }

        return builder.toString();
    }

    private void databaseError(
            Player player,
            SQLException exception
    ) {
        send(player,
                "&cTerjadi kesalahan saat mengambil data clan.");

        plugin.getLogger().severe(
                "ClanWarCommand database error: "
                        + exception.getMessage()
        );
    }

    private void send(Player player, String text) {
        player.sendMessage(color(text));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
