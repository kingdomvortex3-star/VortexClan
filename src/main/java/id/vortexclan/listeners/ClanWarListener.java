
package id.vortexclan.listener;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanWarManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class ClanWarListener implements Listener {

    private final VortexClan plugin;
    private final ClanWarManager warManager;

    public ClanWarListener(VortexClan plugin, ClanWarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }

        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        warManager.recordKill(
                killer.getUniqueId(),
                victim.getUniqueId()
        );
    }
}
