
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanLevelManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ClanLevelGUI implements Listener {

    private final VortexClan plugin;
    private final ClanLevelManager levelManager;

    public ClanLevelGUI(VortexClan plugin) {
        this.plugin = plugin;
        this.levelManager = plugin.getClanLevelManager();
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Gagal mengambil data clan: " + exception.getMessage()
            );
            player.sendMessage(color("&cGagal membuka menu level clan."));
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan!"));
            return;
        }

        String clanId = String.valueOf(clan.getId());

        int level = clan.getLevel();
        long exp = clan.getExp();
        long requiredExp = levelManager.getRequiredExp(level);

        int capacity;

        try {
            capacity = plugin.getClanManager().getCapacity(clanId);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Gagal mengambil kapasitas clan: "
                            + exception.getMessage()
            );
            capacity = 0;
        }

        int memberCount;

        try {
            memberCount = plugin.getClanManager().getMemberCount(clanId);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Gagal mengambil jumlah anggota clan: "
                            + exception.getMessage()
            );
            memberCount = 0;
        }

        Inventory inventory = Bukkit.createInventory(
                new LevelHolder(),
                27,
                color("&8Clan Level")
        );

        inventory.setItem(10, createItem(
                Material.EXPERIENCE_BOTTLE,
                "&b&lClan Level",
                "&7Nama: &f" + clan.getName(),
                "&7Level: &b" + level,
                "&7EXP: &f" + formatNumber(exp),
                "",
                "&7EXP berikutnya: &e"
                        + formatNumber(requiredExp),
                "",
                "&8Terus tingkatkan EXP clan!"
        ));

        inventory.setItem(12, createItem(
                Material.BOOK,
                "&a&lProgress Level",
                "",
                createProgressBar(exp, requiredExp),
                "",
                "&7Progress: &f" + calculatePercent(exp, requiredExp)
                        + "%",
                "&7Sisa EXP: &e"
                        + formatNumber(Math.max(0, requiredExp - exp))
        ));

        inventory.setItem(14, createItem(
                Material.PLAYER_HEAD,
                "&d&lKapasitas Anggota",
                "",
                "&7Anggota: &f" + memberCount + "&7/&f" + capacity,
                "&7Level saat ini: &b" + level,
                "",
                "&a+1 kapasitas setiap kenaikan level"
        ));

        inventory.setItem(16, createItem(
                Material.NETHER_STAR,
                "&6&lInformasi Upgrade",
                "",
                "&7Setiap kenaikan level memberikan:",
                "&a+1 kapasitas anggota",
                "",
                "&7Level maksimum ditentukan config."
        ));

        inventory.setItem(22, createItem(
                Material.ARROW,
                "&c&lKembali",
                "&7Klik untuk menutup menu."
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof LevelHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() == 22) {
            player.closeInventory();
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            String... lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> lines = new ArrayList<>();

            for (String line : lore) {
                lines.add(color(line));
            }

            meta.setLore(lines);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String createProgressBar(long current, long required) {
        int totalBars = 20;

        double percentage = required <= 0
                ? 1.0
                : Math.min(1.0, (double) current / required);

        int filledBars = (int) Math.floor(percentage * totalBars);

        StringBuilder bar = new StringBuilder("&a");

        for (int i = 0; i < totalBars; i++) {
            if (i == filledBars) {
                bar.append("&7");
            }

            bar.append("|");
        }

        return bar.toString();
    }

    private double calculatePercent(long current, long required) {
        if (required <= 0) {
            return 100.0;
        }

        double percentage = (double) current / required * 100.0;

        return Math.round(Math.min(100.0, percentage) * 10.0) / 10.0;
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class LevelHolder implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
