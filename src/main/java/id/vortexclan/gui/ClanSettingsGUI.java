
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;

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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClanSettingsGUI implements Listener {

    private final VortexClan plugin;

    public ClanSettingsGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(
                    player.getUniqueId()
            );
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(
                    "ClanSettingsGUI: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        String role;

        try {
            role = plugin.getClanManager().getMemberRole(
                    player.getUniqueId()
            );
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal memeriksa role clan."));
            plugin.getLogger().severe(exception.getMessage());
            return;
        }

        boolean canManage = role != null
                && (role.equalsIgnoreCase("LEADER")
                || role.equalsIgnoreCase("CO_LEADER"));

        SettingsHolder holder = new SettingsHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                45,
                color("&8Clan Settings")
        );

        holder.setInventory(inventory);
        fillBackground(inventory);

        setItem(
                inventory,
                4,
                Material.NETHER_STAR,
                "&b&lCLAN SETTINGS",
                List.of(
                        "&7Clan: &f" + clan.getName(),
                        "&7Role: &e" + formatRole(role),
                        "",
                        "&8Pengaturan clan"
                )
        );

        setItem(
                inventory,
                10,
                clan.isPvpEnabled()
                        ? Material.DIAMOND_SWORD
                        : Material.SHIELD,
                "&c&lCLAN PVP",
                List.of(
                        "&7Status: "
                                + (clan.isPvpEnabled()
                                ? "&aAktif"
                                : "&cNonaktif"),
                        "",
                        canManage
                                ? "&eKlik untuk mengubah status."
                                : "&cHanya Leader dan Co-Leader."
                )
        );

        setItem(
                inventory,
                12,
                Material.RED_BED,
                "&a&lSET CLAN HOME",
                List.of(
                        "&7Atur lokasi Clan Home.",
                        "",
                        canManage
                                ? "&eKlik untuk mengatur."
                                : "&cHanya Leader dan Co-Leader."
                )
        );

        setItem(
                inventory,
                14,
                Material.ENDER_PEARL,
                "&b&lTELEPORT CLAN HOME",
                List.of(
                        "&7Teleport ke lokasi Clan Home.",
                        "",
                        "&eKlik untuk teleport."
                )
        );

        setItem(
                inventory,
                16,
                Material.GOLD_INGOT,
                "&6&lCLAN BANK",
                List.of(
                        "&7Kelola saldo bank clan.",
                        "",
                        "&eKlik untuk membuka bank."
                )
        );

        setItem(
                inventory,
                28,
                Material.PLAYER_HEAD,
                "&b&lINVITE MEMBER",
                List.of(
                        "&7Undang pemain online",
                        "&7untuk bergabung dengan clan.",
                        "",
                        canManage
                                ? "&eKlik untuk membuka undangan."
                                : "&cHanya Leader dan Co-Leader."
                )
        );

        setItem(
                inventory,
                30,
                Material.BOOK,
                "&e&lCLAN INFORMATION",
                List.of(
                        "&7Lihat informasi lengkap clan.",
                        "",
                        "&eKlik untuk melihat."
                )
        );

        setItem(
                inventory,
                32,
                Material.GOLDEN_SWORD,
                "&6&lCLAN WAR",
                List.of(
                        "&7Lihat informasi dan ranking Clan War.",
                        "",
                        "&eKlik untuk membuka."
                )
        );

        setItem(
                inventory,
                40,
                Material.BARRIER,
                "&c&lTUTUP",
                List.of("&7Klik untuk menutup.")
        );

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof SettingsHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> {
                player.closeInventory();
                player.performCommand("clan pvp");
            }

            case 12 -> {
                player.closeInventory();
                player.performCommand("clan sethome");
            }

            case 14 -> {
                player.closeInventory();
                player.performCommand("clan home");
            }

            case 16 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getClanBankGUI().open(player);
                    }
                });
            }

            case 28 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getClanInviteGUI().open(player);
                    }
                });
            }

            case 30 -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getClanInfoGUI().open(player);
                    }
                });
            }

            case 32 -> {
                player.closeInventory();
                player.performCommand("clan war status");
            }

            case 40 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof SettingsHolder) {
            event.setCancelled(true);
        }
    }

    private String formatRole(String role) {
        if (role == null) {
            return "Unknown";
        }

        return switch (role.toUpperCase()) {
            case "LEADER" -> "Leader";
            case "CO_LEADER" -> "Co-Leader";
            default -> "Member";
        };
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }
    }

    private void setItem(
            Inventory inventory,
            int slot,
            Material material,
            String name,
            List<String> lore
    ) {
        inventory.setItem(
                slot,
                createItem(material, name, lore)
        );
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

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class SettingsHolder implements InventoryHolder {

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
