
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
import org.bukkit.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClanDescriptionGUI implements Listener {

    private final VortexClan plugin;
    private final ClanDescriptionManager descriptionManager;
    private final Set<UUID> waitingInput = new HashSet<>();

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
            clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        } catch (Exception exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(
                    "Gagal membuka Clan Description GUI: "
                            + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan!"));
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new DescriptionHolder(),
                27,
                color("&8Clan Description")
        );

        String description = descriptionManager.getDescription(
                String.valueOf(clan.getId())
        );

        inventory.setItem(11, createItem(
                Material.WRITABLE_BOOK,
                "&b&lUbah Deskripsi",
                "&7Klik untuk mengubah deskripsi clan.",
                "",
                "&eMaksimal 100 karakter."
        ));

        inventory.setItem(13, createItem(
                Material.BOOK,
                "&b&lDeskripsi Saat Ini",
                "",
                description.isEmpty()
                        ? "&cBelum ada deskripsi."
                        : "&f" + description,
                "",
                "&7Clan: &b" + clan.getName()
        ));

        inventory.setItem(15, createItem(
                Material.BARRIER,
                "&c&lHapus Deskripsi",
                "&7Klik untuk menghapus deskripsi clan."
        ));

        inventory.setItem(22, createItem(
                Material.ARROW,
                "&c&lKembali",
                "&7Tutup menu."
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof DescriptionHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR) {
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

    private void requestDescription(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        } catch (Exception exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            return;
        }

        if (clan == null) {
            player.closeInventory();
            player.sendMessage(color("&cKamu belum memiliki clan!"));
            return;
        }

        String role;

        try {
            role = plugin.getClanManager().getMemberRole(player.getUniqueId());
        } catch (Exception exception) {
            player.sendMessage(color("&cGagal memeriksa role clan."));
            return;
        }

        if (!descriptionManager.canManage(role)) {
            player.sendMessage(color("&cHanya Leader dan Co-Leader yang dapat mengubah deskripsi!"));
            return;
        }

        waitingInput.add(player.getUniqueId());
        player.closeInventory();

        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color("&b&lCLAN DESCRIPTION"));
        player.sendMessage(color("&7Ketik deskripsi clan di chat."));
        player.sendMessage(color("&7Maksimal: &e100 karakter"));
        player.sendMessage(color("&7Ketik &c batal &7untuk membatalkan."));
        player.sendMessage(color("&8&m--------------------------------"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingInput.contains(uuid)) {
            return;
        }

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        if (!waitingInput.remove(uuid)) {
            return;
        }

        if (message.equalsIgnoreCase("batal")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(color("&cPengubahan deskripsi dibatalkan."));
                open(player);
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
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
                descriptionManager.removeDescription(player.getUniqueId());

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
                    player.sendMessage(color("&aDeskripsi clan berhasil diperbarui!"));

            case NOT_IN_CLAN ->
                    player.sendMessage(color("&cKamu belum memiliki clan!"));

            case NO_PERMISSION ->
                    player.sendMessage(color("&cHanya Leader dan Co-Leader yang dapat melakukan ini!"));

            case INVALID_DESCRIPTION ->
                    player.sendMessage(color("&cDeskripsi tidak boleh kosong!"));

            case TOO_LONG ->
                    player.sendMessage(color("&cDeskripsi maksimal 100 karakter!"));

            case CLAN_NOT_FOUND ->
                    player.sendMessage(color("&cClan tidak ditemukan!"));

            case DATABASE_ERROR ->
                    player.sendMessage(color("&cTerjadi kesalahan database!"));
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

            java.util.List<String> lines = new java.util.ArrayList<>();

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

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
  }
      
