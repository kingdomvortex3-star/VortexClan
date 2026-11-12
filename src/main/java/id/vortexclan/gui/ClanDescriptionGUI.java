
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanDescriptionManager;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.event.player.AsyncChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanDescriptionGUI implements Listener {

    private final VortexClan plugin;
    private final ClanDescriptionManager descriptionManager;

    private final Set<UUID> waitingInput =
            ConcurrentHashMap.newKeySet();

    public ClanDescriptionGUI(
            VortexClan plugin,
            ClanDescriptionManager descriptionManager
    ) {
        this.plugin = plugin;
        this.descriptionManager = descriptionManager;
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(
                    player.getUniqueId()
            );
        } catch (Exception exception) {
            player.sendMessage(color(
                    "&cGagal mengambil data clan."
            ));
            plugin.getLogger().severe(
                    "Gagal membuka Clan Description GUI: "
                            + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color(
                    "&cKamu belum memiliki clan!"
            ));
            return;
        }

        String description = descriptionManager.getDescription(
                String.valueOf(clan.getId())
        );

        DescriptionHolder holder = new DescriptionHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&8Clan Description")
        );

        holder.setInventory(inventory);

        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " "
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }

        inventory.setItem(11, createItem(
                Material.WRITABLE_BOOK,
                "&b&lUBAH DESKRIPSI",
                "&7Klik untuk mengubah deskripsi clan.",
                "",
                "&eMaksimal 100 karakter."
        ));

        inventory.setItem(13, createItem(
                Material.BOOK,
                "&b&lDESKRIPSI SAAT INI",
                "",
                description.isEmpty()
                        ? "&cBelum ada deskripsi."
                        : "&f" + description,
                "",
                "&7Clan: &b" + clan.getName()
        ));

        inventory.setItem(15, createItem(
                Material.BARRIER,
                "&c&lHAPUS DESKRIPSI",
                "&7Klik untuk menghapus deskripsi clan."
        ));

        inventory.setItem(22, createItem(
                Material.ARROW,
                "&c&lKEMBALI",
                "&7Klik untuk menutup menu."
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof DescriptionHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> requestDescription(player);
            case 15 -> removeDescription(player);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof DescriptionHolder) {
            event.setCancelled(true);
        }
    }

    private void requestDescription(Player player) {
        UUID uuid = player.getUniqueId();

        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(uuid);
        } catch (Exception exception) {
            player.sendMessage(color(
                    "&cGagal mengambil data clan."
            ));
            plugin.getLogger().severe(exception.getMessage());
            return;
        }

        if (clan == null) {
            player.closeInventory();
            player.sendMessage(color(
                    "&cKamu belum memiliki clan!"
            ));
            return;
        }

        String role;

        try {
            role = plugin.getClanManager().getMemberRole(uuid);
        } catch (Exception exception) {
            player.sendMessage(color(
                    "&cGagal memeriksa role clan."
            ));
            plugin.getLogger().severe(exception.getMessage());
            return;
        }

        if (!descriptionManager.canManage(role)) {
            player.sendMessage(color(
                    "&cHanya Leader dan Co-Leader yang dapat mengubah deskripsi!"
            ));
            return;
        }

        if (!waitingInput.add(uuid)) {
            player.sendMessage(color(
                    "&cKamu masih memiliki input deskripsi yang aktif!"
            ));
            return;
        }

        player.closeInventory();

        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color("&b&lCLAN DESCRIPTION"));
        player.sendMessage(color("&7Ketik deskripsi clan di chat."));
        player.sendMessage(color("&7Maksimal: &e100 karakter"));
        player.sendMessage(color("&7Ketik &cBATAL &7untuk membatalkan."));
        player.sendMessage(color("&8&m--------------------------------"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingInput.remove(uuid)) {
            return;
        }

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (message.equalsIgnoreCase("batal")) {
                player.sendMessage(color(
                        "&cPengubahan deskripsi dibatalkan."
                ));
                open(player);
                return;
            }

            ClanDescriptionManager.Result result =
                    descriptionManager.setDescription(uuid, message);

            handleResult(player, result);

            if (result == ClanDescriptionManager.Result.SUCCESS) {
                open(player);
            }
        });
    }

    private void removeDescription(Player player) {
        ClanDescriptionManager.Result result =
                descriptionManager.removeDescription(
                        player.getUniqueId()
                );

        handleResult(player, result);

        if (result == ClanDescriptionManager.Result.SUCCESS) {
            open(player);
        }
    }

    private void handleResult(
            Player player,
            ClanDescriptionManager.Result result
    ) {
        switch (result) {
            case SUCCESS ->
                    player.sendMessage(color(
                            "&aDeskripsi clan berhasil diperbarui!"
                    ));

            case NOT_IN_CLAN ->
                    player.sendMessage(color(
                            "&cKamu belum memiliki clan!"
                    ));

            case NO_PERMISSION ->
                    player.sendMessage(color(
                            "&cHanya Leader dan Co-Leader yang dapat melakukan ini!"
                    ));

            case INVALID_DESCRIPTION ->
                    player.sendMessage(color(
                            "&cDeskripsi tidak boleh kosong!"
                    ));

            case TOO_LONG ->
                    player.sendMessage(color(
                            "&cDeskripsi maksimal 100 karakter!"
                    ));

            case CLAN_NOT_FOUND ->
                    player.sendMessage(color(
                            "&cClan tidak ditemukan!"
                    ));

            case DATABASE_ERROR ->
                    player.sendMessage(color(
                            "&cTerjadi kesalahan database!"
                    ));
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            String... lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> lines = new ArrayList<>();

            for (String line : lore) {
                lines.add(color(line));
            }

            meta.setLore(lines);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void clearWaitingInputs() {
        waitingInput.clear();
    }

    private static class DescriptionHolder implements InventoryHolder {

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
