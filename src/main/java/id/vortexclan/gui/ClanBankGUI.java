
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanBankManager;

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

public final class ClanBankGUI implements Listener {

    private static final int INVENTORY_SIZE = 27;

    private final VortexClan plugin;

    public ClanBankGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                player.sendMessage(message("&cKamu belum memiliki clan!"));
                return;
            }

            double balance = plugin.getClanBankManager()
                    .getBalance(clan.getId());

            BankHolder holder = new BankHolder();

            Inventory inventory = Bukkit.createInventory(
                    holder,
                    INVENTORY_SIZE,
                    color("&8&lCLAN BANK")
            );

            holder.setInventory(inventory);

            fillBackground(inventory);

            inventory.setItem(
                    4,
                    createItem(
                            Material.GOLD_BLOCK,
                            "&6&lCLAN BANK",
                            List.of(
                                    "&7Nama Clan: &f" + clan.getName(),
                                    "&7Tag: &b[" + clan.getTag() + "]",
                                    "",
                                    "&7Saldo Bank:",
                                    "&6$" + formatNumber(balance)
                            )
                    )
            );

            inventory.setItem(
                    11,
                    createItem(
                            Material.EMERALD,
                            "&a&lDEPOSIT",
                            List.of(
                                    "&7Masukkan uang ke bank clan.",
                                    "",
                                    "&eKlik untuk deposit!",
                                    "&8Gunakan command:",
                                    "&f/clan deposit <jumlah>"
                            )
                    )
            );

            inventory.setItem(
                    13,
                    createItem(
                            Material.BOOK,
                            "&b&lSALDO BANK",
                            List.of(
                                    "&7Saldo clan saat ini:",
                                    "",
                                    "&6$" + formatNumber(balance)
                            )
                    )
            );

            inventory.setItem(
                    15,
                    createItem(
                            Material.GOLD_INGOT,
                            "&6&lWITHDRAW",
                            List.of(
                                    "&7Ambil uang dari bank clan.",
                                    "",
                                    "&eKlik untuk withdraw!",
                                    "&8Gunakan command:",
                                    "&f/clan withdraw <jumlah>"
                            )
                    )
            );

            inventory.setItem(
                    22,
                    createItem(
                            Material.BARRIER,
                            "&c&lTUTUP",
                            List.of("&7Klik untuk menutup menu.")
                    )
            );

            player.openInventory(inventory);

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Gagal membuka Clan Bank GUI: "
                            + exception.getMessage()
            );

            player.sendMessage(message(
                    "&cGagal mengambil data bank clan!"
            ));
        }
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
                4, 11, 13, 15, 22
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

    private String formatNumber(double number) {
        return String.format(
                java.util.Locale.US,
                "%,.2f",
                number
        );
    }

    private String message(String text) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        return color(prefix + " " + text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof BankHolder)) {
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
            case 11 -> {
                player.closeInventory();
                player.sendMessage(message(
                        "&eGunakan &f/clan deposit <jumlah>"
                ));
            }

            case 15 -> {
                player.closeInventory();
                player.sendMessage(message(
                        "&eGunakan &f/clan withdraw <jumlah>"
                ));
            }

            case 22 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof BankHolder) {
            event.setCancelled(true);
        }
    }

    private static class BankHolder implements InventoryHolder {

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
