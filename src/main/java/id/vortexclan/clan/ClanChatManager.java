
package id.vortexclan.clan;

import id.vortexclan.VortexClan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanChatManager {

    private final VortexClan plugin;

    private final Set<UUID> chatMode =
            ConcurrentHashMap.newKeySet();

    public ClanChatManager(VortexClan plugin) {
        this.plugin = plugin;
    }

    public enum Result {
        SUCCESS,
        NOT_IN_CLAN,
        DATABASE_ERROR
    }

    public Result toggle(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            return Result.DATABASE_ERROR;
        }

        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                chatMode.remove(player.getUniqueId());
                return Result.NOT_IN_CLAN;
            }

            UUID uuid = player.getUniqueId();

            if (chatMode.remove(uuid)) {
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

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Clan Chat toggle error: " + exception.getMessage()
            );

            return Result.DATABASE_ERROR;
        }
    }

    public boolean isEnabled(Player player) {
        return chatMode.contains(player.getUniqueId());
    }

    public boolean isEnabled(UUID uuid) {
        return chatMode.contains(uuid);
    }

    public void disable(Player player) {
        if (player != null) {
            chatMode.remove(player.getUniqueId());
        }
    }

    public void disable(UUID uuid) {
        if (uuid != null) {
            chatMode.remove(uuid);
        }
    }

    public Result sendMessage(Player sender, String message) {
        if (!Bukkit.isPrimaryThread()) {
            return Result.DATABASE_ERROR;
        }

        if (sender == null || !sender.isOnline()) {
            return Result.NOT_IN_CLAN;
        }

        if (message == null || message.isBlank()) {
            return Result.SUCCESS;
        }

        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(sender.getUniqueId());

            if (clan == null) {
                chatMode.remove(sender.getUniqueId());
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
                    "Clan Chat send error: " + exception.getMessage()
            );

            return Result.DATABASE_ERROR;
        }
    }

    public void clear() {
        chatMode.clear();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
