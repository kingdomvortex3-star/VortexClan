
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ClanTopGUI implements Listener {

    private static final int PAGE_SIZE = 27;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final VortexClan plugin;
    private final DecimalFormat moneyFormat =
            new DecimalFormat("#,##0.##");

    public ClanTopGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, "level", 0);
    }

    public void open(Player player, String category) {
        open(player, category, 0);
    }

    public void open(Player player, String category, int page) {
        String sort = normalizeCategory(category);

        if (sort == null) {
            sort = "level";
        }

        int total = getTotalClans();
        int totalPages = Math.max(
                1,
                (int) Math.ceil(total / (double) PAGE_SIZE)
        );

        int currentPage = Math.max(
                0,
                Math.min(page, totalPages - 1)
        );

        List<TopEntry> entries = getEntries(
                sort,
                PAGE_SIZE,
                currentPage * PAGE_SIZE
        );

        TopHolder holder = new TopHolder(sort, currentPage);

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8VortexClan Leaderboard")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        for (int i = 0; i < entries.size(); i++) {
            TopEntry entry = entries.get(i);

            inventory.setItem(
                    ENTRY_SLOTS[i],
                    createItem(
                            getRankMaterial(entry.position()),
                            getRankColor(entry.position())
                                    + "&l#" + entry.position()
                                    + " &f" + entry.name(),
                            List.of(
                                    "&7Tag: &b" + entry.tag(),
                                    "&7Level: &a" + entry.level(),
                                    "&7Bank: &e$" + formatMoney(entry.bank()),
                                    "&7Members: &f" + entry.members(),
                                    "&7Wins: &a" + entry.wins(),
                                    "&7Kills: &c" + entry.kills(),
                                    "",
                                    "&8VortexClan Ranking"
                            )
                    )
            );
        }

        inventory.setItem(
                45,
                categoryItem(
                        Material.EXPERIENCE_BOTTLE,
                        "level",
                        sort,
                        "LEVEL",
                        "Ranking berdasarkan level clan."
                )
        );

        inventory.setItem(
                46,
                categoryItem(
                        Material.GOLD_INGOT,
                        "bank",
                        sort,
                        "BANK",
                        "Ranking berdasarkan saldo bank."
                )
        );

        inventory.setItem(
                47,
                categoryItem(
                        Material.PLAYER_HEAD,
                        "members",
                        sort,
                        "MEMBERS",
                        "Ranking berdasarkan jumlah anggota."
                )
        );

        inventory.setItem(
                48,
                categoryItem(
                        Material.GOLDEN_SWORD,
                        "wins",
                        sort,
                        "WINS",
                        "Ranking berdasarkan kemenangan."
                )
        );

        inventory.setItem(
                49,
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
                        Material.PAPER,
                        "&b&lLEADERBOARD",
                        List.of(
                                "&7Kategori: &f" + sort.toUpperCase(),
                                "&7Halaman: &f" + (currentPage + 1)
                                        + "&7/&f" + totalPages,
                                "&7Total clan: &f" + total,
                                "&7Ditampilkan: &f" + entries.size(),
                                "",
                                "&8VortexClan"
                        )
                )
        );

        inventory.setItem(
                51,
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
                52,
                categoryItem(
                        Material.IRON_SWORD,
                        "kills",
                        sort,
                        "KILLS",
                        "Ranking berdasarkan total kill."
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup.")
                )
        );

        player.openInventory(inventory);
    }

    private List<TopEntry> getEntries(
            String category,
            int limit,
            int offset
    ) {
        List<TopEntry> entries = new ArrayList<>();

        String orderBy = switch (category) {
            case "level" -> "c.level DESC, c.exp DESC, c.name ASC";
            case "bank" -> "c.bank DESC, c.name ASC";
            case "members" -> "member_count DESC, c.name ASC";
            case "wins" -> "c.wins DESC, c.kills DESC, c.name ASC";
            case "kills" -> "c.kills DESC, c.wins DESC, c.name ASC";
            default -> "c.level DESC, c.name ASC";
        };

        String sql = """
                SELECT
                    c.id,
                    c.name,
                    c.tag,
                    c.level,
                    c.bank,
                    c.wins,
                    c.losses,
                    c.kills,
                    (
                        SELECT COUNT(*)
                        FROM clan_members cm
                        WHERE cm.clan_id = c.id
                    ) AS member_count
                FROM clans c
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(orderBy);

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {

                statement.setInt(1, limit);
                statement.setInt(2, offset);

                try (ResultSet result = statement.executeQuery()) {
                    int position = offset + 1;

                    while (result.next()) {
                        entries.add(new TopEntry(
                                position++,
                                result.getString("name"),
                                result.getString("tag"),
                                result.getInt("level"),
                                result.getDouble("bank"),
                                result.getInt("member_count"),
                                result.getInt("wins"),
                                result.getInt("losses"),
                                result.getInt("kills")
                        ));
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "[VortexClan] Gagal mengambil leaderboard: "
                                + exception.getMessage()
                );
            }
        }

        return entries;
    }

    private int getTotalClans() {
        String sql = "SELECT COUNT(*) FROM clans";

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {

                return result.next() ? result.getInt(1) : 0;

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "[VortexClan] Gagal menghitung clan: "
                                + exception.getMessage()
                );
                return 0;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof TopHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> open(player, "level", 0);
            case 46 -> open(player, "bank", 0);
            case 47 -> open(player, "members", 0);
            case 48 -> open(player, "wins", 0);

            case 49 -> {
                if (holder.page() > 0) {
                    open(
                            player,
                            holder.category(),
                            holder.page() - 1
                    );
                }
            }

            case 51 -> {
                int totalPages = Math.max(
                        1,
                        (int) Math.ceil(
                                getTotalClans() / (double) PAGE_SIZE
                        )
                );

                if (holder.page() + 1 < totalPages) {
                    open(
                            player,
                            holder.category(),
                            holder.page() + 1
                    );
                }
            }

            case 52 -> open(player, "kills", 0);

            case 53 -> player.closeInventory();

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof TopHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack categoryItem(
            Material material,
            String category,
            String selected,
            String title,
            String description
    ) {
        boolean active = category.equals(selected);

        return createItem(
                material,
                (active ? "&a&l" : "&e&l") + title,
                List.of(
                        "&7" + description,
                        "",
                        active
                                ? "&aSedang dipilih"
                                : "&eKlik untuk memilih."
                )
        );
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return null;
        }

        return switch (category.toLowerCase()) {
            case "level", "bank", "members", "wins", "kills" ->
                    category.toLowerCase();
            default -> null;
        };
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

    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record TopEntry(
            int position,
            String name,
            String tag,
            int level,
            double bank,
            int members,
            int wins,
            int losses,
            int kills
    ) {
    }

    private static class TopHolder implements InventoryHolder {

        private final String category;
        private final int page;
        private Inventory inventory;

        private TopHolder(String category, int page) {
            this.category = category;
            this.page = page;
        }

        public String category() {
            return category;
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
