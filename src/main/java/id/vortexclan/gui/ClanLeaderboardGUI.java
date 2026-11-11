
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanWarLeaderboardManager;
import id.vortexclan.clan.ClanWarLeaderboardManager.WarLeaderboardEntry;

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

public class ClanWarLeaderboardGUI implements Listener {

    private static final int PAGE_SIZE = 27;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final VortexClan plugin;
    private final ClanWarLeaderboardManager leaderboardManager;

    public ClanWarLeaderboardGUI(
            VortexClan plugin,
            ClanWarLeaderboardManager leaderboardManager
    ) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
    }

    public void open(Player player) {
        open(player, "wins", 0);
    }

    public void open(Player player, String sortType) {
        open(player, sortType, 0);
    }

    public void open(Player player, String sortType, int page) {
        String selectedSort = normalizeSort(sortType);

        if (selectedSort == null) {
            selectedSort = "wins";
        }

        int totalPages = leaderboardManager.getTotalPages(PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        List<WarLeaderboardEntry> entries =
                leaderboardManager.getTop(
                        selectedSort,
                        PAGE_SIZE,
                        currentPage * PAGE_SIZE
                );

        WarLeaderboardHolder holder = new WarLeaderboardHolder(
                selectedSort,
                currentPage
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8Clan War Leaderboard")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
            WarLeaderboardEntry entry = entries.get(i);

            inventory.setItem(
                    ENTRY_SLOTS[i],
                    createItem(
                            getRankMaterial(entry.position()),
                            getRankColor(entry.position())
                                    + "&l#" + entry.position()
                                    + " &f" + entry.clanName(),
                            List.of(
                                    "&7Tag: &b" + entry.clanTag(),
                                    "&7Wins: &a" + entry.wins(),
                                    "&7Losses: &c" + entry.losses(),
                                    "&7Kills: &e" + entry.kills(),
                                    "",
                                    "&8Clan War Ranking"
                            )
                    )
            );
        }

        inventory.setItem(
                45,
                createItem(
                        selectedSort.equals("wins")
                                ? Material.ENCHANTED_GOLDEN_APPLE
                                : Material.GOLDEN_SWORD,
                        selectedSort.equals("wins")
                                ? "&a&lWINS"
                                : "&eWins",
                        List.of(
                                "&7Ranking berdasarkan kemenangan.",
                                "",
                                selectedSort.equals("wins")
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                47,
                createItem(
                        Material.IRON_SWORD,
                        selectedSort.equals("kills")
                                ? "&a&lKILLS"
                                : "&eKills",
                        List.of(
                                "&7Ranking berdasarkan total kill.",
                                "",
                                selectedSort.equals("kills")
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.PAPER,
                        "&b&lHALAMAN " + (currentPage + 1)
                                + "&7/&f" + totalPages,
                        List.of(
                                "&7Total clan: &f"
                                        + leaderboardManager.getTotalClans(),
                                "&7Ditampilkan: &f" + entries.size(),
                                "",
                                "&8VortexClan"
                        )
                )
        );

        inventory.setItem(
                51,
                createItem(
                        Material.SKELETON_SKULL,
                        selectedSort.equals("losses")
                                ? "&a&lLOSSES"
                                : "&eLosses",
                        List.of(
                                "&7Ranking berdasarkan kekalahan.",
                                "",
                                selectedSort.equals("losses")
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                48,
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
                50,
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

        inventory.setItem(
                53,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup GUI.")
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
                instanceof WarLeaderboardHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> open(player, "wins", 0);

            case 47 -> open(player, "kills", 0);

            case 48 -> {
                if (holder.page() > 0) {
                    open(
                            player,
                            holder.sortType(),
                            holder.page() - 1
                    );
                }
            }

            case 49 -> {
            }

            case 50 -> {
                int totalPages =
                        leaderboardManager.getTotalPages(PAGE_SIZE);

                if (holder.page() + 1 < totalPages) {
                    open(
                            player,
                            holder.sortType(),
                            holder.page() + 1
                    );
                }
            }

            case 51 -> open(player, "losses", 0);

            case 53 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof WarLeaderboardHolder) {
            event.setCancelled(true);
        }
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

    private Material getRankMaterial(int position) {
        return switch (position) {
            case 1 -> Material.DIAMOND;
            case 2 -> Material.EMERALD;
            case 3 -> Material.GOLD_INGOT;
            default -> Material.PLAYER_HEAD;
        };
    }

    private String getRankColor(int position) {
        return switch (position) {
            case 1 -> "&b";
            case 2 -> "&a";
            case 3 -> "&6";
            default -> "&f";
        };
    }

    private String normalizeSort(String sortType) {
        if (sortType == null) {
            return null;
        }

        return switch (sortType.toLowerCase()) {
            case "wins", "kills", "losses" -> sortType.toLowerCase();
            default -> null;
        };
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

    private static class WarLeaderboardHolder implements InventoryHolder {

        private final String sortType;
        private final int page;
        private Inventory inventory;

        private WarLeaderboardHolder(String sortType, int page) {
            this.sortType = sortType;
            this.page = page;
        }

        public String sortType() {
            return sortType;
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
