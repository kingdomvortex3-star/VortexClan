
package id.vortexclan.listeners;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanChatManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClanChatListener implements Listener {

    private final VortexClan plugin;
    private final ClanChatManager chatManager;

    public ClanChatListener(VortexClan plugin, ClanChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!chatManager.isEnabled(player)) {
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

            ClanChatManager.Result result =
                    chatManager.sendMessage(player, message);

            switch (result) {
                case SUCCESS -> {
                }

                case NOT_IN_CLAN -> {
                    chatManager.disable(player);
                    player.sendMessage(
                            "§8[§bVortexClan§8] §cKamu sudah tidak memiliki clan!"
                    );
                }

                case DATABASE_ERROR -> player.sendMessage(
                        "§8[§bVortexClan§8] §cGagal mengirim pesan Clan Chat!"
                );
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chatManager.disable(event.getPlayer());
    }
}
