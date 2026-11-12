
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanBankTransactionGUI implements Listener {

    private static final int PAGE_SIZE = 45;

    private final VortexClan plugin;

    private final DecimalFormat moneyFormat =
            new DecimalFormat("#,##0.##");

    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    public ClanBankTransactionGUI(VortexClan plugin) {
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
                    "ClanBankTransactionGUI: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        String clanId = String.valueOf(clan.getId());

        int totalTransactions = getTotalTransactions(clanId);

        int totalPages = Math.max(
                1,
                (int) Math.ceil(
                        totalTransactions / (double) PAGE_SIZE
                )
        );

        int currentPage = Math.max(
                0,
                Math.min(page, totalPages - 1)
        );

        TransactionHolder holder = new TransactionHolder(
                clanId,
                currentPage
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8Clan Bank History")
        );

        holder.setInventory(inventory);
        fillBackground(inventory);

        List<TransactionEntry> transactions = getTransactions(
                clanId,
                currentPage
        );

        int slot = 0;

        for (TransactionEntry transaction : transactions) {
            inventory.setItem(
                    slot++,
                    createTransactionItem(transaction)
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
                                        ? "&7Klik untuk halaman sebelumnya."
                                        : "&cTidak ada halaman sebelumnya."
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.PAPER,
                        "&b&lINFORMASI RIWAYAT",
                        List.of(
                                "&7Clan: &f" + clan.getName(),
                                "&7Total transaksi: &f" + totalTransactions,
                                "&7Halaman: &f" + (currentPage + 1)
                                        + "&7/&f" + totalPages,
                                "",
                                "&8Transaksi terbaru ditampilkan dahulu."
                        )
                )
        );

        inventory.setItem(
                50,
                createItem(
                        Material.BARRIER,
                        "&c&lKEMBALI",
                        List.of("&7Klik untuk kembali ke bank clan.")
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
                                        ? "&7Klik untuk halaman selanjutnya."
                                        : "&cTidak ada halaman selanjutnya."
                        )
                )
        );

        player.openInventory(inventory);
    }

    private ItemStack createTransactionItem(
            TransactionEntry transaction
    ) {
        boolean deposit = isDeposit(transaction.type());

        Material material = deposit
                ? Material.LIME_DYE
                : Material.RED_DYE;

        String transactionName = formatTransactionType(
                transaction.type()
        );

        String playerName = getPlayerName(
                transaction.playerUuid()
        );

        return createItem(
                material,
                (deposit ? "&a&l" : "&c&l") + transactionName,
                List.of(
                        "&7Pemain: &f" + playerName,
                        "&7Jumlah: " + (deposit ? "&a+$" : "&c-$")
                                + formatMoney(transaction.amount()),
                        "&7Saldo setelah transaksi: &e$"
                                + formatMoney(transaction.balanceAfter()),
                        "",
                        "&7Waktu: &f" + formatDate(
                                transaction.createdAt()
                        ),
                        "",
                        "&8Transaction ID: " + transaction.id()
                )
        );
    }

    private List<TransactionEntry> getTransactions(
            String clanId,
            int page
    ) {
        List<TransactionEntry> transactions = new ArrayList<>();

        String sql = """
                SELECT
                    id,
                    player_uuid,
                    transaction_type,
                    amount,
                    balance_after,
                    created_at
                FROM clan_transactions
                WHERE clan_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setInt(2, PAGE_SIZE);
                statement.setInt(3, page * PAGE_SIZE);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        transactions.add(new TransactionEntry(
                                result.getLong("id"),
                                result.getString("player_uuid"),
                                result.getString("transaction_type"),
                                result.getDouble("amount"),
                                result.getDouble("balance_after"),
                                result.getLong("created_at")
                        ));
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "Gagal mengambil riwayat transaksi: "
                                + exception.getMessage()
                );
            }
        }

        return transactions;
    }

    private int getTotalTransactions(String clanId) {
        String sql = """
                SELECT COUNT(*)
                FROM clan_transactions
                WHERE clan_id = ?
                """;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "Gagal menghitung transaksi clan: "
                                + exception.getMessage()
                );
                return 0;
            }
        }
    }

    private String getPlayerName(String playerUuid) {
        if (playerUuid == null
                || playerUuid.equalsIgnoreCase("SYSTEM")) {
            return "System";
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(
                    UUID.fromString(playerUuid)
            );

            String name = offlinePlayer.getName();

            return name != null ? name : "Unknown";

        } catch (IllegalArgumentException exception) {
            return "Unknown";
        }
    }

    private boolean isDeposit(String type) {
        if (type == null) {
            return false;
        }

        String normalized = type.toUpperCase();

        return normalized.contains("DEPOSIT")
                || normalized.contains("WAR_REWARD")
                || normalized.contains("REFUND");
    }

    private String formatTransactionType(String type) {
        if (type == null) {
            return "UNKNOWN";
        }

        return switch (type.toUpperCase()) {
            case "DEPOSIT" -> "DEPOSIT";
            case "WITHDRAW" -> "WITHDRAW";
            case "WAR_REWARD" -> "WAR REWARD";
            case "TAX" -> "CLAN TAX";
            case "REFUND" -> "REFUND";
            default -> type.replace("_", " ");
        };
    }

    private String formatDate(long timestamp) {
        try {
            return dateFormat.format(
                    Instant.ofEpochMilli(timestamp)
            );
        } catch (Exception exception) {
            return "Unknown";
        }
    }

    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, background);
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof TransactionHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 45 -> {
                if (holder.page() > 0) {
                    open(player, holder.page() - 1);
                }
            }

            case 50 -> {
                player.closeInventory();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getClanBankGUI().open(player);
                    }
                });
            }

            case 53 -> {
                if (holder.page() + 1
                        < getTotalPages(holder.clanId())) {
                    open(player, holder.page() + 1);
                }
            }

            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof TransactionHolder) {
            event.setCancelled(true);
        }
    }

    private int getTotalPages(String clanId) {
        int total = getTotalTransactions(clanId);

        return Math.max(
                1,
                (int) Math.ceil(total / (double) PAGE_SIZE)
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record TransactionEntry(
            long id,
            String playerUuid,
            String type,
            double amount,
            double balanceAfter,
            long createdAt
    ) {
    }

    private static class TransactionHolder implements InventoryHolder {

        private final String clanId;
        private final int page;
        private Inventory inventory;

        private TransactionHolder(String clanId, int page) {
            this.clanId = clanId;
            this.page = page;
        }

        public String clanId() {
            return clanId;
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
