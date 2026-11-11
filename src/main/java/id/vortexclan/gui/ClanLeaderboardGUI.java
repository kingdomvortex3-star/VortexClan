
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

public final class ClanLeaderboardGUI implements Listener {

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int PAGE_SIZE = ENTRY_SLOTS.length;

    private final VortexClan plugin;
    private final ClanLeaderboardManager leaderboardManager;

    public ClanLeaderboardGUI(VortexClan plugin) {
        this.plugin = plugin;
        this.leaderboardManager = plugin.getClanLeaderboardManager();
    }

    public void open(Player player) {
        open(player, SortType.LEVEL, 0);
    }

    public void open(Player player, SortType sortType) {
        open(player, sortType, 0);
    }

    public void open(Player player, SortType sortType, int page) {
        if (sortType == null) {
            sortType = SortType.LEVEL;
        }

        List<ClanRank> allClans;

        try {
            allClans = leaderboardManager.getTopClans(
                    sortType,
                    100
            );
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "[ClanLeaderboardGUI] " + exception.getMessage()
            );
            player.sendMessage(color("&cGagal mengambil leaderboard clan."));
            return;
        }

        int totalPages = Math.max(
                1,
                (allClans.size() + PAGE_SIZE - 1) / PAGE_SIZE
        );

        int currentPage = Math.max(
                0,
                Math.min(page, totalPages - 1)
        );

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allClans.size());

        ClanLeaderboardHolder holder = new ClanLeaderboardHolder(
                sortType,
                currentPage
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8Clan Leaderboard")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        for (int index = start; index < end; index++) {
            ClanRank clan = allClans.get(index);

            int slot = ENTRY_SLOTS[index - start];

            inventory.setItem(
                    slot,
                    createItem(
                            getRankMaterial(clan.position()),
                            getRankColor(clan.position())
                                    + "&l#" + clan.position()
                                    + " &f" + clan.clanName(),
                            List.of(
                                    "&7Tag: &b" + clan.clanTag(),
                                    "&7Level: &e" + clan.level(),
                                    "&7EXP: &f" + clan.exp(),
                                    "&7Bank: &a$" + format(clan.bank()),
                                    "&7Wins: &a" + clan.wins(),
                                    "&7Losses: &c" + clan.losses(),
                                    "&7Kills: &e" + clan.kills()
                            )
                    )
            );
        }

        inventory.setItem(
                45,
                createItem(
                        Material.EXPERIENCE_BOTTLE,
                        sortType == SortType.LEVEL
                                ? "&a&lLEVEL"
                                : "&eLevel",
                        List.of(
                                "&7Ranking berdasarkan level clan.",
                                "",
                                sortType == SortType.LEVEL
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                47,
                createItem(
                        Material.GOLD_INGOT,
                        sortType == SortType.BANK
                                ? "&a&lBANK"
                                : "&eBank",
                        List.of(
                                "&7Ranking berdasarkan saldo bank.",
                                "",
                                sortType == SortType.BANK
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.BOOK,
                        "&b&lLEADERBOARD",
                        List.of(
                                "&7Kategori: &f" + sortType.name(),
                                "&7Total clan: &f" + allClans.size(),
                                "&7Halaman: &f" + (currentPage + 1)
                                        + "/" + totalPages,
                                "",
                                "&8VortexClan"
                        )
                )
        );

        inventory.setItem(
                51,
                createItem(
                        Material.GOLDEN_SWORD,
                        sortType == SortType.WINS
                                ? "&a&lWINS"
                                : "&eWins",
                        List.of(
                                "&7Ranking berdasarkan kemenangan.",
                                "",
                                sortType == SortType.WINS
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
                        List.of("&7Halaman sebelumnya.")
                )
        );

        inventory.setItem(
                50,
                createItem(
                        currentPage + 1 < totalPages
                                ? Material.ARROW
                                : Material.GRAY_STAINED_GLASS_PANE,
                        "&a&lNEXT",
                        List.of("&7Halaman berikutnya.")
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup menu.")
                )
        );

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder()
                instanceof ClanLeaderboardHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        switch (slot) {
            case 45 -> open(player, SortType.LEVEL, 0);
            case 47 -> open(player, SortType.BANK, 0);
            case 51 -> open(player, SortType.WINS, 0);

            case 48 -> {
                if (holder.page > 0) {
                    open(player, holder.sortType, holder.page - 1);
                }
            }

            case 50 -> open(player, holder.sortType, holder.page + 1);

            case 53 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder()
                instanceof ClanLeaderboardHolder) {
            event.setCancelled(true);
        }
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
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

    private String format(double amount) {
        return String.format("%,.2f", amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class ClanLeaderboardHolder
            implements InventoryHolder {

        private final SortType sortType;
        private final int page;
        private Inventory inventory;

        private ClanLeaderboardHolder(SortType sortType, int page) {
            this.sortType = sortType;
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
