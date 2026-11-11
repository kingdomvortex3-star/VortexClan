
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ClanInviteGUI implements Listener {

    private static final int PAGE_SIZE = 28;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final VortexClan plugin;

    public ClanInviteGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(
                    player.getUniqueId()
            );
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(
                    "ClanInviteGUI: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        List<Player> targets = Bukkit.getOnlinePlayers()
                .stream()
                .filter(target -> !target.getUniqueId().equals(
                        player.getUniqueId()
                ))
                .sorted(Comparator.comparing(
                        Player::getName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();

        int totalPages = Math.max(
                1,
                (int) Math.ceil(targets.size() / (double) PAGE_SIZE)
        );

        int currentPage = Math.max(
                0,
                Math.min(page, totalPages - 1)
        );

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, targets.size());

        InviteHolder holder = new InviteHolder(currentPage);

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8Clan Invite")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        for (int i = start; i < end; i++) {
            Player target = targets.get(i);
            int slot = ENTRY_SLOTS[i - start];

            inventory.setItem(
                    slot,
                    createPlayerHead(
                            target,
                            "&b&l" + target.getName(),
                            List.of(
                                    "&7Pemain: &f" + target.getName(),
                                    "&7Status: &aOnline",
                                    "",
                                    "&eKlik untuk mengundang."
                            )
                    )
            );
        }

        inventory.setItem(
                45,
                createItem(
                        currentPage > 0
                                ? Material.ARROW
                                : Material.GRAY_STAINED_GLASS_PANE,
                        "&a&lPREVIOUS",
                        List.of(
                                currentPage > 0
                                        ? "&7Halaman sebelumnya."
                                        : "&cTidak ada halaman sebelumnya."
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.PAPER,
                        "&b&lINFORMASI",
                        List.of(
                                "&7Clan: &f" + clan.getName(),
                                "&7Pemain online: &f" + targets.size(),
                                "&7Halaman: &f" + (currentPage + 1)
                                        + "&7/&f" + totalPages,
                                "",
                                "&8Klik kepala untuk mengundang."
                        )
                )
        );

        inventory.setItem(
                50,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup.")
                )
        );

        inventory.setItem(
                53,
                createItem(
                        currentPage + 1 < totalPages
                                ? Material.ARROW
                                : Material.GRAY_STAINED_GLASS_PANE,
                        "&a&lNEXT",
                        List.of(
                                currentPage + 1 < totalPages
                                        ? "&7Halaman selanjutnya."
                                        : "&cTidak ada halaman selanjutnya."
                        )
                )
        );

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof InviteHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 45) {
            if (holder.page() > 0) {
                open(player, holder.page() - 1);
            }
            return;
        }

        if (slot == 50) {
            player.closeInventory();
            return;
        }

        if (slot == 53) {
            open(player, holder.page() + 1);
            return;
        }

        for (int entrySlot : ENTRY_SLOTS) {
            if (slot != entrySlot) {
                continue;
            }

            ItemStack item = event.getCurrentItem();

            if (item == null || item.getType() != Material.PLAYER_HEAD) {
                return;
            }

            if (!(item.getItemMeta() instanceof SkullMeta meta)) {
                return;
            }

            OfflinePlayer target = meta.getOwningPlayer();

            if (target == null || target.getName() == null) {
                player.sendMessage(color("&cPemain tidak ditemukan."));
                return;
            }

            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());

            if (onlineTarget == null) {
                player.sendMessage(color("&cPemain tersebut sudah offline."));
                open(player, holder.page());
                return;
            }

            if (onlineTarget.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(color("&cKamu tidak bisa mengundang diri sendiri."));
                return;
            }

            player.closeInventory();
            player.performCommand("clan invite " + onlineTarget.getName());
            return;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof InviteHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack createPlayerHead(
            Player player,
            String name,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(player);
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

    private static class InviteHolder implements InventoryHolder {

        private final int page;
        private Inventory inventory;

        private InviteHolder(int page) {
            this.page = page;
        }

        public int page() {
            return page;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
