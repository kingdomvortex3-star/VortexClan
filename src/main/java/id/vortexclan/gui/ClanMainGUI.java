
package id.vortexclan.gui;

import id.vortexclan.VortexClan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ClanMainGUI implements Listener {

    private static final int INVENTORY_SIZE = 54;

    private final VortexClan plugin;

    public ClanMainGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        MainHolder holder = new MainHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                color("&8&lVORTEX CLAN")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(
                4,
                createItem(
                        Material.NETHER_STAR,
                        "&b&lVORTEX CLAN",
                        List.of(
                                "&7Welcome to VortexClan!",
                                "",
                                "&fPilih menu yang ingin kamu buka.",
                                "&7Kelola clan kamu dengan mudah."
                        )
                )
        );

        inventory.setItem(
                20,
                createItem(
                        Material.BOOK,
                        "&b&lCLAN INFORMATION",
                        List.of(
                                "&7Lihat informasi clan kamu.",
                                "",
                                "&eKlik untuk membuka!"
                        )
                )
        );

        inventory.setItem(
                22,
                createItem(
                        Material.DIAMOND,
                        "&6&lCLAN LEADERBOARD",
                        List.of(
                                "&7Lihat ranking seluruh clan.",
                                "",
                                "&eKlik untuk membuka!"
                        )
                )
        );

        inventory.setItem(
                24,
                createItem(
                        Material.COMPASS,
                        "&a&lCLAN HOME",
                        List.of(
                                "&7Teleport menuju markas clan.",
                                "",
                                "&eKlik untuk teleport!"
                        )
                )
        );

        inventory.setItem(
                29,
                createItem(
                        Material.DIAMOND_SWORD,
                        "&c&lCLAN PVP",
                        List.of(
                                "&7Aktifkan atau nonaktifkan",
                                "&7PvP antar anggota clan.",
                                "",
                                "&eKlik untuk mengubah!"
                        )
                )
        );

        inventory.setItem(
                31,
                createItem(
                        Material.WRITABLE_BOOK,
                        "&b&lCLAN CHAT",
                        List.of(
                                "&7Aktifkan atau nonaktifkan",
                                "&7mode Clan Chat.",
                                "",
                                "&eKlik untuk mengubah!"
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of(
                                "&7Klik untuk menutup menu."
                        )
                )
        );

        player.openInventory(inventory);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, background);
        }

        int[] emptySlots = {
                20, 22, 24, 29, 31, 49
        };

        for (int slot : emptySlots) {
            inventory.setItem(slot, null);
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> coloredLore = new ArrayList<>();

            for (String line : lore) {
                coloredLore.add(color(line));
            }

            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof MainHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        switch (slot) {
            case 20 -> {
                player.closeInventory();
                player.performCommand("clan info");
            }

            case 22 -> {
                player.closeInventory();
                plugin.getClanLeaderboardGUI().open(player);
            }

            case 24 -> {
                player.closeInventory();
                player.performCommand("clan home");
            }

            case 29 -> {
                player.closeInventory();
                player.performCommand("clan pvp");
            }

            case 31 -> {
                player.closeInventory();
                player.performCommand("clan chat");
            }

            case 49 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof MainHolder) {
            event.setCancelled(true);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class MainHolder implements InventoryHolder {

        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
