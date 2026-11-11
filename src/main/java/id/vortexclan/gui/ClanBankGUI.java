
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanBankManager;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanBankGUI implements Listener {

    private final VortexClan plugin;
    private final ClanBankManager bankManager;

    private final Map<UUID, BankAction> pendingInputs =
            new ConcurrentHashMap<>();

    private final DecimalFormat moneyFormat =
            new DecimalFormat("#,##0.##");

    public ClanBankGUI(VortexClan plugin) {
        this.plugin = plugin;
        this.bankManager = plugin.getClanBankManager();
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
                    "ClanBankGUI: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        double balance = bankManager.getBalance(
                String.valueOf(clan.getId())
        );

        BankHolder holder = new BankHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&8Clan Bank")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        setItem(
                inventory,
                11,
                Material.LIME_DYE,
                "&a&lDEPOSIT",
                List.of(
                        "&7Masukkan uang ke bank clan.",
                        "",
                        "&eKlik untuk deposit."
                )
        );

        setItem(
                inventory,
                13,
                Material.GOLD_INGOT,
                "&6&lBANK BALANCE",
                List.of(
                        "&7Clan: &b" + clan.getTag(),
                        "&7Saldo: &a$" + formatMoney(balance),
                        "",
                        "&eSaldo bank clan saat ini."
                )
        );

        String role = plugin.getClanManager().getMemberRole(
                player.getUniqueId()
        );

        boolean canWithdraw = role != null
                && (role.equalsIgnoreCase("LEADER")
                || role.equalsIgnoreCase("CO_LEADER"));

        setItem(
                inventory,
                15,
                canWithdraw ? Material.RED_DYE : Material.GRAY_DYE,
                canWithdraw
                        ? "&c&lWITHDRAW"
                        : "&7&lWITHDRAW",
                canWithdraw
                        ? List.of(
                                "&7Tarik uang dari bank clan.",
                                "",
                                "&eKlik untuk withdraw."
                        )
                        : List.of(
                                "&cHanya Leader dan Co-Leader",
                                "&cyang dapat melakukan withdraw."
                        )
        );

        setItem(
                inventory,
                22,
                Material.BARRIER,
                "&c&lTUTUP",
                List.of("&7Klik untuk menutup menu.")
        );

        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof BankHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> requestAmount(player, BankAction.DEPOSIT);

            case 15 -> requestAmount(player, BankAction.WITHDRAW);

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

    private void requestAmount(Player player, BankAction action) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(
                    player.getUniqueId()
            );
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            return;
        }

        if (clan == null) {
            player.closeInventory();
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        if (action == BankAction.DEPOSIT
                && !plugin.getConfig().getBoolean(
                        "clan.bank.allow-deposit", true
                )) {
            player.sendMessage(color("&cDeposit sedang dinonaktifkan."));
            return;
        }

        if (action == BankAction.WITHDRAW) {
            String role = plugin.getClanManager().getMemberRole(
                    player.getUniqueId()
            );

            if (role == null
                    || (!role.equalsIgnoreCase("LEADER")
                    && !role.equalsIgnoreCase("CO_LEADER"))) {
                player.sendMessage(color(
                        "&cHanya Leader dan Co-Leader yang dapat withdraw."
                ));
                return;
            }

            if (!plugin.getConfig().getBoolean(
                    "clan.bank.allow-withdraw", true
            )) {
                player.sendMessage(color("&cWithdraw sedang dinonaktifkan."));
                return;
            }
        }

        pendingInputs.put(player.getUniqueId(), action);

        player.closeInventory();

        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color(
                action == BankAction.DEPOSIT
                        ? "&a&lCLAN BANK - DEPOSIT"
                        : "&c&lCLAN BANK - WITHDRAW"
        ));
        player.sendMessage("");
        player.sendMessage(color("&7Masukkan jumlah uang yang ingin "
                + (action == BankAction.DEPOSIT
                ? "disimpan."
                : "ditarik.")));
        player.sendMessage(color("&7Contoh: &f1000"));
        player.sendMessage("");
        player.sendMessage(color("&7Ketik &c&lcancel &7untuk membatalkan."));
        player.sendMessage(color("&8&m--------------------------------"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();

        BankAction action = pendingInputs.remove(
                player.getUniqueId()
        );

        if (action == null) {
            return;
        }

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(color("&cInput Clan Bank dibatalkan."));
                open(player);
                return;
            }

            double amount;

            try {
                amount = Double.parseDouble(message);
            } catch (NumberFormatException exception) {
                player.sendMessage(color("&cJumlah uang tidak valid."));
                player.sendMessage(color(
                        "&7Buka kembali GUI Clan Bank untuk mencoba lagi."
                ));
                return;
            }

            if (!Double.isFinite(amount) || amount <= 0) {
                player.sendMessage(color("&cJumlah uang harus lebih dari 0."));
                return;
            }

            ClanBankManager.Result result = action == BankAction.DEPOSIT
                    ? bankManager.deposit(player.getUniqueId(), amount)
                    : bankManager.withdraw(player.getUniqueId(), amount);

            handleResult(player, result, action, amount);
        });
    }

    private void handleResult(
            Player player,
            ClanBankManager.Result result,
            BankAction action,
            double amount
    ) {
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(color(
                        action == BankAction.DEPOSIT
                                ? "&aBerhasil deposit &f$" + formatMoney(amount)
                                : "&aBerhasil withdraw &f$" + formatMoney(amount)
                ));

                open(player);
            }

            case NO_CLAN ->
                    player.sendMessage(color("&cKamu belum memiliki clan."));

            case ECONOMY_UNAVAILABLE ->
                    player.sendMessage(color("&cVault Economy tidak tersedia."));

            case INVALID_AMOUNT ->
                    player.sendMessage(color("&cJumlah uang tidak valid."));

            case INSUFFICIENT_MONEY ->
                    player.sendMessage(color("&cUang kamu tidak mencukupi."));

            case INSUFFICIENT_BANK ->
                    player.sendMessage(color("&cSaldo bank clan tidak mencukupi."));

            case NO_PERMISSION ->
                    player.sendMessage(color("&cKamu tidak memiliki izin withdraw."));

            case DISABLED ->
                    player.sendMessage(color("&cFitur bank sedang dinonaktifkan."));

            case DATABASE_ERROR ->
                    player.sendMessage(color("&cTerjadi kesalahan database."));
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

    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void clearPendingInputs() {
        pendingInputs.clear();
    }

    private enum BankAction {
        DEPOSIT,
        WITHDRAW
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
