
package id.vortexclan.listeners;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanAllianceChatManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClanAllianceChatListener implements Listener {

    private final VortexClan plugin;
    private final ClanAllianceChatManager chatManager;

    public ClanAllianceChatListener(
            VortexClan plugin,
            ClanAllianceChatManager chatManager
    ) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!chatManager.isEnabled(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        if (message.isBlank()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
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
