
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

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ClanInfoGUI implements Listener {

    private final VortexClan plugin;
    private final DecimalFormat moneyFormat =
            new DecimalFormat("#,##0.##");

    public ClanInfoGUI(VortexClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(
                    player.getUniqueId()
            );
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil informasi clan."));
            plugin.getLogger().severe(
                    "ClanInfoGUI database error: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan."));
            return;
        }

        int members = plugin.getClanManager().getMemberCount(clan.getId());
        int capacity = plugin.getClanManager().getCapacity(clan.getId());

        int rank = plugin.getClanLeaderboardManager().getClanRank(
                clan.getId(),
                "level"
        );

        int onlineMembers = getOnlineMembers(String.valueOf(clan.getId()));

        String leaderName = getPlayerName(clan.getLeaderUuid().toString());

        Inventory inventory = Bukkit.createInventory(
                new InfoHolder(),
                45,
                color("&8Clan Information")
        );

        fillBackground(inventory);

        setItem(
                inventory,
                4,
                Material.NETHER_STAR,
                "&b&l" + clan.getName(),
                List.of(
                        "&7Tag: &f[" + clan.getTag() + "]",
                        "&7Leader: &f" + leaderName,
                        "",
                        "&8Clan Information"
                )
        );

        setItem(
                inventory,
                10,
                Material.EXPERIENCE_BOTTLE,
                "&a&lCLAN LEVEL",
                List.of(
                        "&7Level: &a" + clan.getLevel(),
                        "&7EXP: &f" + clan.getExp(),
                        "",
                        "&7Rank: &e#" + Math.max(0, rank),
                        "",
                        "&8Clan Progress"
                )
        );

        setItem(
                inventory,
                12,
                Material.PLAYER_HEAD,
                "&b&lMEMBERS",
                List.of(
                        "&7Total anggota: &f" + members,
                        "&7Online: &a" + onlineMembers,
                        "&7Kapasitas: &f" + capacity,
                        "",
                        "&8Clan Members"
                )
        );

        setItem(
                inventory,
                14,
                Material.GOLD_INGOT,
                "&6&lCLAN BANK",
                List.of(
                        "&7Saldo: &a$" + moneyFormat.format(clan.getBank()),
                        "",
                        "&8Clan Economy"
                )
        );

        setItem(
                inventory,
                16,
                clan.isPvpEnabled()
                        ? Material.DIAMOND_SWORD
                        : Material.SHIELD,
                "&c&lCLAN PVP",
                List.of(
                        "&7Status: "
                                + (clan.isPvpEnabled()
                                ? "&aAktif"
                                : "&cNonaktif"),
                        "",
                        "&8Friendly Fire"
                )
        );

        setItem(
                inventory,
                29,
                Material.GOLDEN_SWORD,
                "&a&lWAR WINS",
                List.of(
                        "&7Total kemenangan: &a" + clan.getWins()
                )
        );

        setItem(
                inventory,
                31,
                Material.SKELETON_SKULL,
                "&c&lWAR LOSSES",
                List.of(
                        "&7Total kekalahan: &c" + clan.getLosses()
                )
        );

        setItem(
                inventory,
                33,
                Material.IRON_SWORD,
                "&e&lTOTAL KILLS",
                List.of(
                        "&7Total kill: &e" + clan.getKills()
                )
        );

        setItem(
                inventory,
                40,
                Material.BARRIER,
                "&c&lTUTUP",
                List.of("&7Klik untuk menutup.")
        );

        player.openInventory(inventory);
    }

    private int getOnlineMembers(String clanId) {
        String sql = """
                SELECT uuid
                FROM clan_members
                WHERE clan_id = ?
                """;

        int online = 0;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (var statement = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        try {
                            java.util.UUID uuid = java.util.UUID.fromString(
                                    result.getString("uuid")
                            );

                            Player member = Bukkit.getPlayer(uuid);

                            if (member != null && member.isOnline()) {
                                online++;
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().warning(
                        "Gagal menghitung online members: "
                                + exception.getMessage()
                );
            }
        }

        return online;
    }

    private String getPlayerName(String uuid) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(
                    java.util.UUID.fromString(uuid)
            );

            return offlinePlayer.getName() != null
                    ? offlinePlayer.getName()
                    : "Unknown";
        } catch (IllegalArgumentException exception) {
            return "Unknown";
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof InfoHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        if (event.getRawSlot() == 40) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof InfoHolder) {
            event.setCancelled(true);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class InfoHolder implements InventoryHolder {

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
