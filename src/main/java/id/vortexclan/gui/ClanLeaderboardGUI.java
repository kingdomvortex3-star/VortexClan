
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

    private final VortexClan plugin;
    private final ClanLeaderboardManager leaderboardManager;

    public ClanLeaderboardGUI(
            VortexClan plugin,
            ClanLeaderboardManager leaderboardManager
    ) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
    }

    public void open(Player player) {
        open(player, SortType.LEVEL);
    }

    public void open(Player player, SortType sortType) {
        if (player == null || !player.isOnline()) {
            return;
        }

        SortType selectedSort = sortType == null
                ? SortType.LEVEL
                : sortType;

        List<ClanRank> rankings;

        int totalClans;

        try {
            rankings = leaderboardManager.getTopClans(
                    selectedSort,
                    ENTRY_SLOTS.length
            );

            totalClans = leaderboardManager.getTotalClans();

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "[ClanLeaderboardGUI] Gagal mengambil leaderboard: "
                            + exception.getMessage()
            );

            player.sendMessage(color(
                    "&cGagal memuat leaderboard clan."
            ));
            return;
        }

        LeaderboardHolder holder = new LeaderboardHolder(selectedSort);

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8&lCLAN LEADERBOARD")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        for (int i = 0; i < rankings.size(); i++) {
            ClanRank rank = rankings.get(i);

            inventory.setItem(
                    ENTRY_SLOTS[i],
                    createItem(
                            getRankMaterial(rank.position()),
                            getRankColor(rank.position())
                                    + "&l#" + rank.position()
                                    + " &f" + rank.clanName(),
                            List.of(
                                    "&7Tag: &b" + rank.clanTag(),
                                    "&7Level: &a" + rank.level(),
                                    "&7EXP: &e" + formatNumber(rank.exp()),
                                    "&7Bank: &6$" + formatNumber(rank.bank()),
                                    "&7Wins: &a" + rank.wins(),
                                    "&7Losses: &c" + rank.losses(),
                                    "&7Kills: &e" + rank.kills(),
                                    "",
                                    "&8Clan ID: " + rank.clanId()
                            )
                    )
            );
        }

        inventory.setItem(
                45,
                createItem(
                        selectedSort == SortType.LEVEL
                                ? Material.ENCHANTED_BOOK
                                : Material.BOOK,
                        selectedSort == SortType.LEVEL
                                ? "&a&lLEVEL"
                                : "&eLevel",
                        List.of(
                                "&7Ranking berdasarkan level clan.",
                                "",
                                selectedSort == SortType.LEVEL
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                47,
                createItem(
                        selectedSort == SortType.WINS
                                ? Material.GOLDEN_SWORD
                                : Material.IRON_SWORD,
                        selectedSort == SortType.WINS
                                ? "&a&lWINS"
                                : "&eWins",
                        List.of(
                                "&7Ranking berdasarkan kemenangan.",
                                "",
                                selectedSort == SortType.WINS
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.PAPER,
                        "&b&lSTATISTIK CLAN",
                        List.of(
                                "&7Total clan: &f" + totalClans,
                                "&7Ditampilkan: &f" + rankings.size(),
                                "",
                                "&8VortexClan"
                        )
                )
        );

        inventory.setItem(
                51,
                createItem(
                        selectedSort == SortType.BANK
                                ? Material.EMERALD
                                : Material.GOLD_INGOT,
                        selectedSort == SortType.BANK
                                ? "&a&lBANK"
                                : "&eBank",
                        List.of(
                                "&7Ranking berdasarkan saldo bank.",
                                "",
                                selectedSort == SortType.BANK
                                        ? "&aSedang dipilih"
                                        : "&eKlik untuk memilih."
                        )
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup leaderboard.")
                )
        );

        player.openInventory(inventory);
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

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> open(player, SortType.LEVEL);

            case 47 -> open(player, SortType.WINS);

            case 51 -> open(player, SortType.BANK);

            case 53 -> player.closeInventory();

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

    private void fillBackground(Inventory inventory) {
        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                "&8 ",
                List.of()
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }

        for (int slot : ENTRY_SLOTS) {
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

    private Material getRankMaterial(int position) {
        return switch (position) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.PLAYER_HEAD;
        };
    }

    private String getRankColor(int position) {
        return switch (position) {
            case 1 -> "&6";
            case 2 -> "&f";
            case 3 -> "&c";
            default -> "&b";
        };
    }

    private String formatNumber(Number number) {
        return String.format(
                java.util.Locale.US,
                "%,d",
                number.longValue()
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class LeaderboardHolder
            implements InventoryHolder {

        private final SortType sortType;
        private Inventory inventory;

        private LeaderboardHolder(SortType sortType) {
            this.sortType = sortType;
        }

        public SortType getSortType() {
            return sortType;
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
