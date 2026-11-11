
package id.vortexclan.listener;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanAllianceChatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ClanAllianceChatListener implements Listener {

    private final VortexClan plugin;
    private final ClanAllianceChatManager chatManager;

    public ClanAllianceChatListener(
            VortexClan plugin,
            ClanAllianceChatManager chatManager
    ) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!chatManager.isEnabled(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            chatManager.sendMessage(player, message);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chatManager.disable(event.getPlayer().getUniqueId());
    }
}
