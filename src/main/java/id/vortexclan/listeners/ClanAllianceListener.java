
package id.vortexclan.listeners;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanAllianceManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.sql.SQLException;

public final class ClanAllianceListener implements Listener {

    private final VortexClan plugin;
    private final ClanAllianceManager allianceManager;

    public ClanAllianceListener(
            VortexClan plugin,
            ClanAllianceManager allianceManager
    ) {
        this.plugin = plugin;
        this.allianceManager = allianceManager;
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event.getDamager());

        if (attacker == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
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

            String attackerClanId =
                    String.valueOf(attackerClan.getId());

            String victimClanId =
                    String.valueOf(victimClan.getId());

            if (attackerClanId.equals(victimClanId)) {
                return;
            }

            if (!allianceManager.isAllied(
                    attackerClanId,
                    victimClanId
            )) {
                return;
            }

            event.setCancelled(true);

            attacker.sendMessage(color(
                    "&cKamu tidak bisa menyerang anggota clan sekutu!"
            ));

            victim.sendMessage(color(
                    "&aSerangan dari anggota clan sekutu berhasil diblokir!"
            ));

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "[ClanAllianceListener] Gagal memeriksa clan: "
                            + exception.getMessage()
            );
        }
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();

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
