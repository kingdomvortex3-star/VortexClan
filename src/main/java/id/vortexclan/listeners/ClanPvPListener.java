
package id.vortexclan.listeners;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.sql.SQLException;
import java.util.UUID;

public final class ClanPvPListener implements Listener {

    private final VortexClan plugin;

    public ClanPvPListener(VortexClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClanDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = getAttackingPlayer(event.getDamager());

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        try {
            Clan attackerClan = plugin.getClanManager()
                    .getClanByPlayer(attacker.getUniqueId());

            if (attackerClan == null) {
                return;
            }

            Clan victimClan = plugin.getClanManager()
                    .getClanByPlayer(victim.getUniqueId());

            if (victimClan == null) {
                return;
            }

            if (!attackerClan.getId().equals(victimClan.getId())) {
                return;
            }

            if (attackerClan.isPvpEnabled()) {
                return;
            }

            event.setCancelled(true);

            attacker.sendMessage(color(
                    "&8[&bVortexClan&8] &cClan PvP sedang dinonaktifkan!"
            ));

        } catch (SQLException exception) {
            event.setCancelled(true);

            plugin.getLogger().severe(
                    "Gagal memeriksa Clan PvP: "
                            + exception.getMessage()
            );
        }
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            Object shooter = projectile.getShooter();

            if (shooter instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
