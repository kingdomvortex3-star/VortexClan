
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.ClanLeaderboardManager;
import id.vortexclan.clan.ClanLeaderboardManager.ClanRank;
import id.vortexclan.clan.ClanLeaderboardManager.SortType;

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
import java.util.Locale;

public class ClanLeaderboardGUI implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ENTRIES_PER_PAGE = 28;

    /*
     * ClanLeaderboardManager saat ini membatasi hasil query
     * maksimal 100 clan. Batas ini akan dihilangkan saat
     * manager diperbarui menggunakan sistem SQL OFFSET.
     */
    private static final int MAX_FETCHED_CLANS = 100;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final VortexClan plugin;

    public ClanLeaderboardGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, SortType.LEVEL, 0);
    }

    public void open(Player player, SortType sortType) {
        open(player, sortType, 0);
    }

    private void open(Player player, SortType sortType, int requestedPage) {
        if (sortType == null) {
            sortType = SortType.LEVEL;
        }

        if (requestedPage < 0) {
            requestedPage = 0;
        }

        try {
            ClanLeaderboardManager manager =
                    plugin.getClanLeaderboardManager();

            int totalClans = manager.getTotalClans();

            int availableClans = Math.min(
                    totalClans,
                    MAX_FETCHED_CLANS
            );

            int totalPages = Math.max(
                    1,
                    (int) Math.ceil(
                            availableClans / (double) ENTRIES_PER_PAGE
                    )
            );

            int page = Math.min(requestedPage, totalPages - 1);

            List<ClanRank> clans = manager.getTopClans(
                    sortType,
                    Math.min(
                            (page + 1) * ENTRIES_PER_PAGE,
                            MAX_FETCHED_CLANS
                    )
            );

            LeaderboardHolder holder = new LeaderboardHolder(
                    sortType,
                    page,
                    totalPages
            );

            Inventory inventory = Bukkit.createInventory(
                    holder,
                    INVENTORY_SIZE,
                    color("&8Clan Leaderboard")
            );

            holder.setInventory(inventory);

            fillBackground(inventory);
            addCategoryButtons(inventory, sortType);
            addClanEntries(inventory, clans, page, sortType);

            inventory.setItem(
                    4,
                    createItem(
                            Material.BOOK,
                            "&b&lCLAN LEADERBOARD",
                            List.of(
                                    "&7Kategori: &f" + getCategoryName(sortType),
                                    "&7Total Clan: &f" + totalClans,
                                    "&7Ditampilkan: &f" + availableClans,
                                    "&7Halaman: &f" + (page + 1)
                                            + "&7/&f" + totalPages,
                                    "",
                                    "&ePilih kategori di bagian bawah!"
                            )
                    )
            );

            addNavigationButtons(inventory, page, totalPages);

            player.openInventory(inventory);

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Gagal mengambil data Clan Leaderboard: "
                            + exception.getMessage()
            );

            player.sendMessage(color(
                    "&8[&bVortexClan&8] &cGagal memuat leaderboard!"
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
    }

    private void addCategoryButtons(
            Inventory inventory,
            SortType selected
    ) {
        inventory.setItem(
                47,
                createItem(
                        selected == SortType.LEVEL
                                ? Material.ENCHANTED_BOOK
                                : Material.BOOK,
                        "&b&lTOP LEVEL",
                        List.of(
                                "&7Ranking berdasarkan level clan.",
                                "",
                                selected == SortType.LEVEL
                                        ? "&aKategori sedang dipilih!"
                                        : "&eKlik untuk melihat ranking!"
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        selected == SortType.WINS
                                ? Material.DIAMOND_SWORD
                                : Material.IRON_SWORD,
                        "&a&lTOP WINS",
                        List.of(
                                "&7Ranking berdasarkan kemenangan war.",
                                "",
                                selected == SortType.WINS
                                        ? "&aKategori sedang dipilih!"
                                        : "&eKlik untuk melihat ranking!"
                        )
                )
        );

        inventory.setItem(
                51,
                createItem(
                        selected == SortType.BANK
                                ? Material.GOLD_BLOCK
                                : Material.GOLD_INGOT,
                        "&6&lTOP BANK",
                        List.of(
                                "&7Ranking berdasarkan saldo bank clan.",
                                "",
                                selected == SortType.BANK
                                        ? "&aKategori sedang dipilih!"
                                        : "&eKlik untuk melihat ranking!"
                        )
                )
        );
    }

    private void addClanEntries(
            Inventory inventory,
            List<ClanRank> clans,
            int page,
            SortType sortType
    ) {
        int startIndex = page * ENTRIES_PER_PAGE;

        for (int slotIndex = 0; slotIndex < ENTRY_SLOTS.length; slotIndex++) {
            int clanIndex = startIndex + slotIndex;

            if (clanIndex >= clans.size()) {
                break;
            }

            ClanRank clan = clans.get(clanIndex);

            inventory.setItem(
                    ENTRY_SLOTS[slotIndex],
                    createClanItem(clan, sortType)
            );
        }
    }

    private void addNavigationButtons(
            Inventory inventory,
            int page,
            int totalPages
    ) {
        inventory.setItem(
                45,
                createItem(
                        Material.ARROW,
                        "&e&lHALAMAN SEBELUMNYA",
                        List.of(
                                "&7Halaman saat ini: &f" + (page + 1),
                                "",
                                page > 0
                                        ? "&eKlik untuk kembali!"
                                        : "&cTidak ada halaman sebelumnya!"
                        )
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.ARROW,
                        "&e&lHALAMAN SELANJUTNYA",
                        List.of(
                                "&7Halaman: &f" + (page + 1)
                                        + "&7/&f" + totalPages,
                                "",
                                page + 1 < totalPages
                                        ? "&eKlik untuk melanjutkan!"
                                        : "&cTidak ada halaman selanjutnya!"
                        )
                )
        );

        inventory.setItem(
                50,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup menu.")
                )
        );
    }

    private ItemStack createClanItem(
            ClanRank clan,
            SortType sortType
    ) {
        Material material = switch (clan.position()) {
            case 1 -> Material.DIAMOND_BLOCK;
            case 2 -> Material.GOLD_BLOCK;
            case 3 -> Material.IRON_BLOCK;
            default -> Material.PAPER;
        };

        String rankColor = switch (clan.position()) {
            case 1 -> "&6";
            case 2 -> "&f";
            case 3 -> "&e";
            default -> "&b";
        };

        List<String> lore = new ArrayList<>();

        lore.add("&7Peringkat: " + rankColor + "#" + clan.position());
        lore.add("&7Nama Clan: &f" + clan.clanName());
        lore.add("&7Tag: &b[" + clan.clanTag() + "]");
        lore.add("");

        lore.add("&7Level: &b" + clan.level());
        lore.add("&7EXP: &f" + clan.exp());
        lore.add("&7Wins: &a" + clan.wins());
        lore.add("&7Losses: &c" + clan.losses());
        lore.add("&7Kills: &e" + clan.kills());

        lore.add("");
        lore.add("&7Bank: &6$" + formatNumber(clan.bank()));
        lore.add("");

        switch (sortType) {
            case LEVEL -> lore.add(
                    "&bRanking berdasarkan level dan EXP"
            );

            case WINS -> lore.add(
                    "&aRanking berdasarkan kemenangan war"
            );

            case BANK -> lore.add(
                    "&6Ranking berdasarkan saldo bank"
            );
        }

        return createItem(
                material,
                rankColor + "&l#" + clan.position()
                        + " &f" + clan.clanName(),
                lore
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

    private String getCategoryName(SortType type) {
        return switch (type) {
            case LEVEL -> "Top Level";
            case WINS -> "Top Wins";
            case BANK -> "Top Bank";
        };
    }

    private String formatNumber(double number) {
        return String.format(
                Locale.US,
                "%,.2f",
                number
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof LeaderboardHolder holder)) {
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
            case 45 -> {
                if (holder.getPage() > 0) {
                    open(
                            player,
                            holder.getSortType(),
                            holder.getPage() - 1
                    );
                }
            }

            case 47 -> open(player, SortType.LEVEL);

            case 49 -> open(player, SortType.WINS);

            case 50 -> player.closeInventory();

            case 51 -> open(player, SortType.BANK);

            case 53 -> {
                if (holder.getPage() + 1 < holder.getTotalPages()) {
                    open(
                            player,
                            holder.getSortType(),
                            holder.getPage() + 1
                    );
                }
            }

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof LeaderboardHolder) {
            event.setCancelled(true);
        }
    }

    private static class LeaderboardHolder implements InventoryHolder {

        private final SortType sortType;
        private final int page;
        private final int totalPages;
        private Inventory inventory;

        private LeaderboardHolder(
                SortType sortType,
                int page,
                int totalPages
        ) {
            this.sortType = sortType;
            this.page = page;
            this.totalPages = totalPages;
        }

        public SortType getSortType() {
            return sortType;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
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
