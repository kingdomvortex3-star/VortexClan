
package id.vortexclan.listeners;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanChatManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.SQLException;

@SuppressWarnings("deprecation")
public final class ClanChatListener implements Listener {

    private final VortexClan plugin;
    private final ClanChatManager chatManager;

    public ClanChatListener(
            VortexClan plugin,
            ClanChatManager chatManager
    ) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!chatManager.isEnabled(player)) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                chatManager.disable(player);

                player.sendMessage(color(
                        "&8[&bVortexClan&8] &cKamu sudah tidak memiliki clan!"
                ));

                return;
            }

            chatManager.sendMessage(player, message);

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Clan Chat Listener error: "
                            + exception.getMessage()
            );

            player.sendMessage(color(
                    "&8[&bVortexClan&8] &cGagal mengirim pesan Clan Chat!"
            ));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
