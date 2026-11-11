
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ClanWarLeaderboardGUI implements Listener {

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
        open(player, "wins");
    }

    public void open(Player player, String sortType) {
        String selectedSort = normalizeSort(sortType);

        if (selectedSort == null) {
            selectedSort = "wins";
        }

        WarLeaderboardHolder holder = new WarLeaderboardHolder(selectedSort);

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8Clan War Leaderboard")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        List<WarLeaderboardEntry> entries =
                leaderboardManager.getTop(selectedSort, 100);

        int displayed = Math.min(entries.size(), ENTRY_SLOTS.length);

        for (int i = 0; i < displayed; i++) {
            WarLeaderboardEntry entry = entries.get(i);

            inventory.setItem(
                    ENTRY_SLOTS[i],
                    createItem(
                            getRankMaterial(entry.position()),
                            getRankColor(entry.position()) + "&l#" + entry.position()
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
                        Material.GOLDEN_SWORD,
                        selectedSort.equals("wins") ? "&a&lWINS" : "&eWins",
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
                        selectedSort.equals("kills") ? "&a&lKILLS" : "&eKills",
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
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup.")
                )
        );

        inventory.setItem(
                51,
                createItem(
                        Material.SKELETON_SKULL,
                        selectedSort.equals("losses") ? "&a&lLOSSES" : "&eLosses",
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
                53,
                createItem(
                        Material.PAPER,
                        "&b&lINFORMASI",
                        List.of(
                                "&7Total clan: &f" + leaderboardManager.getTotalClans(),
                                "&7Ranking ditampilkan: &f" + displayed,
                                "",
                                "&8VortexClan"
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
                instanceof WarLeaderboardHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> open(player, "wins");
            case 47 -> open(player, "kills");
            case 49 -> player.closeInventory();
            case 51 -> open(player, "losses");
            default -> {
            }
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
        private Inventory inventory;

        private WarLeaderboardHolder(String sortType) {
            this.sortType = sortType;
        }

        public String getSortType() {
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
