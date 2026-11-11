
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanAllianceChatManager {

    private final VortexClan plugin;
    private final ClanAllianceManager allianceManager;

    private final Set<UUID> enabledPlayers =
            ConcurrentHashMap.newKeySet();

    public ClanAllianceChatManager(
            VortexClan plugin,
            ClanAllianceManager allianceManager
    ) {
        this.plugin = plugin;
        this.allianceManager = allianceManager;
    }

    public boolean toggle(UUID playerUuid) {
        if (!enabledPlayers.remove(playerUuid)) {
            enabledPlayers.add(playerUuid);
            return true;
        }

        return false;
    }

    public boolean isEnabled(UUID playerUuid) {
        return enabledPlayers.contains(playerUuid);
    }

    public void disable(UUID playerUuid) {
        enabledPlayers.remove(playerUuid);
    }

    public void sendMessage(Player sender, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }

        Clan senderClan;

        try {
            senderClan = plugin.getClanManager()
                    .getClanByPlayer(sender.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "[AllianceChat] Gagal mengambil clan pengirim: "
                            + exception.getMessage()
            );
            sender.sendMessage(color("&cGagal mengirim pesan aliansi."));
            return;
        }

        if (senderClan == null) {
            sender.sendMessage(color("&cKamu belum memiliki clan!"));
            disable(sender.getUniqueId());
            return;
        }

        String senderClanId = String.valueOf(senderClan.getId());

        List<ClanAllianceManager.AllianceData> alliances =
                allianceManager.getAlliances(senderClanId);

        Set<String> recipientClanIds = new HashSet<>();
        recipientClanIds.add(senderClanId);

        for (ClanAllianceManager.AllianceData alliance : alliances) {
            recipientClanIds.add(alliance.clanId());
        }

        String formatted = color(
                "&8[&bALLY&8] &7[&f"
                        + senderClan.getTag()
                        + "&7] &b"
                        + sender.getName()
                        + "&8: &f"
                        + message
        );

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            try {
                Clan recipientClan = plugin.getClanManager()
                        .getClanByPlayer(onlinePlayer.getUniqueId());

                if (recipientClan == null) {
                    continue;
                }

                String recipientClanId =
                        String.valueOf(recipientClan.getId());

                if (recipientClanIds.contains(recipientClanId)) {
                    onlinePlayer.sendMessage(formatted);
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "[AllianceChat] Gagal memeriksa penerima: "
                                + exception.getMessage()
                );
            }
        }
    }

    public void clear() {
        enabledPlayers.clear();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
