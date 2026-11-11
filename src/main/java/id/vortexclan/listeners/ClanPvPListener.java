
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
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClanPvPListener implements Listener {

    private static final long CACHE_DURATION = 1_000_000_000L;
    private static final long MESSAGE_COOLDOWN = 2_000_000_000L;

    private final VortexClan plugin;

    private final ConcurrentMap<UUID, CachedClan> clanCache =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, Long> messageCooldown =
            new ConcurrentHashMap<>();

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
            CachedClan attackerClan = getCachedClan(
                    attacker.getUniqueId()
            );

            if (attackerClan == null || !attackerClan.hasClan()) {
                return;
            }

            CachedClan victimClan = getCachedClan(
                    victim.getUniqueId()
            );

            if (victimClan == null || !victimClan.hasClan()) {
                return;
            }

            if (!attackerClan.clanId().equals(victimClan.clanId())) {
                return;
            }

            if (attackerClan.pvpEnabled()) {
                return;
            }

            event.setCancelled(true);

            sendCooldownMessage(attacker);

        } catch (SQLException exception) {
            event.setCancelled(true);

            plugin.getLogger().severe(
                    "Gagal memeriksa Clan PvP: "
                            + exception.getMessage()
            );
        }
    }

    private CachedClan getCachedClan(UUID playerUuid)
            throws SQLException {

        long now = System.nanoTime();

        CachedClan cached = clanCache.get(playerUuid);

        if (cached != null && now < cached.expiresAt()) {
            return cached;
        }

        Clan clan = plugin.getClanManager()
                .getClanByPlayer(playerUuid);

        CachedClan updated;

        if (clan == null) {
            updated = new CachedClan(
                    null,
                    false,
                    now + CACHE_DURATION
            );
        } else {
            updated = new CachedClan(
                    clan.getId(),
                    clan.isPvpEnabled(),
                    now + CACHE_DURATION
            );
        }

        clanCache.put(playerUuid, updated);

        return updated;
    }

    private void sendCooldownMessage(Player player) {
        long now = System.nanoTime();

        Long lastMessage = messageCooldown.get(
                player.getUniqueId()
        );

        if (lastMessage != null
                && now - lastMessage < MESSAGE_COOLDOWN) {
            return;
        }

        messageCooldown.put(
                player.getUniqueId(),
                now
        );

        player.sendMessage(color(
                "&8[&bVortexClan&8] &cClan PvP sedang dinonaktifkan!"
        ));
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        clanCache.remove(uuid);
        messageCooldown.remove(uuid);
    }

    public void clearCache() {
        clanCache.clear();
        messageCooldown.clear();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record CachedClan(
            String clanId,
            boolean pvpEnabled,
            long expiresAt
    ) {
        private boolean hasClan() {
            return clanId != null;
        }
    }
}
