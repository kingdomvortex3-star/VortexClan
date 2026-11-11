
package id.vortexclan.clan;

import id.vortexclan.VortexClan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClanChatManager {

    private final VortexClan plugin;

    private final Set<UUID> chatMode = new HashSet<>();

    public ClanChatManager(VortexClan plugin) {
        this.plugin = plugin;
    }

    public enum Result {
        SUCCESS,
        NOT_IN_CLAN,
        DATABASE_ERROR
    }

    public Result toggle(Player player) {
        if (!isClanMember(player)) {
            return Result.NOT_IN_CLAN;
        }

        UUID uuid = player.getUniqueId();

        if (chatMode.contains(uuid)) {
            chatMode.remove(uuid);
            player.sendMessage(color(
                    "&8[&bVortexClan&8] &cClan Chat dinonaktifkan!"
            ));
        } else {
            chatMode.add(uuid);
            player.sendMessage(color(
                    "&8[&bVortexClan&8] &aClan Chat diaktifkan!"
            ));
            player.sendMessage(color(
                    "&7Pesan kamu sekarang dikirim ke anggota clan."
            ));
        }

        return Result.SUCCESS;
    }

    public boolean isEnabled(Player player) {
        return chatMode.contains(player.getUniqueId());
    }

    public void disable(Player player) {
        chatMode.remove(player.getUniqueId());
    }

    public Result sendMessage(Player sender, String message) {
        if (message == null || message.isBlank()) {
            return Result.SUCCESS;
        }

        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(sender.getUniqueId());

            if (clan == null) {
                return Result.NOT_IN_CLAN;
            }

            String format = plugin.getConfig().getString(
                    "clan.chat.format",
                    "&8[&b%clan_tag%&8] &b%player%&7: &f%message%"
            );

            String formatted = format
                    .replace("%clan_name%", clan.getName())
                    .replace("%clan_tag%", clan.getTag())
                    .replace("%player%", sender.getName())
                    .replace("%message%", message);

            formatted = color(formatted);

            for (Player online : Bukkit.getOnlinePlayers()) {
                Clan memberClan = plugin.getClanManager()
                        .getClanByPlayer(online.getUniqueId());

                if (memberClan != null
                        && memberClan.getId().equals(clan.getId())) {
                    online.sendMessage(formatted);
                }
            }

            return Result.SUCCESS;

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Clan Chat error: " + exception.getMessage()
            );

            return Result.DATABASE_ERROR;
        }
    }

    private boolean isClanMember(Player player) {
        try {
            return plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId()) != null;
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Gagal memeriksa anggota Clan Chat: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    public void clear() {
        chatMode.clear();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
