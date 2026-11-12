
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanWarManager;

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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClanWarGUI implements Listener {

    private final VortexClan plugin;
    private final ClanWarManager warManager;

    public ClanWarGUI(VortexClan plugin, ClanWarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(
                    "ClanWarGUI: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.sendMessage(color(
                    plugin.getConfig().getString(
                            "messages.no-clan",
                            "&cKamu belum memiliki clan!"
                    )
            ));
            return;
        }

        String clanId = String.valueOf(clan.getId());

        ClanWarManager.WarData activeWar;

        try {
            activeWar = warManager.getActiveWar(clanId);
        } catch (SQLException exception) {
            player.sendMessage(color(
                    "&cGagal mengambil informasi Clan War."
            ));
            plugin.getLogger().severe(
                    "Gagal mengambil perang aktif: "
                            + exception.getMessage()
            );
            return;
        }

        PendingWar pendingWar = getPendingWar(clanId);

        WarHolder holder = new WarHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&8Clan War")
        );

        holder.setInventory(inventory);

        fill(inventory);

        if (activeWar != null) {
            setItem(
                    inventory,
                    13,
                    Material.IRON_SWORD,
                    "&c&lCLAN WAR AKTIF",
                    List.of(
                            "&7Clan A: &f" + activeWar.clanA(),
                            "&7Clan B: &f" + activeWar.clanB(),
                            "",
                            "&7Kill Clan A: &a" + activeWar.killsA(),
                            "&7Kill Clan B: &c" + activeWar.killsB(),
                            "",
                            "&ePerang sedang berlangsung!"
                    )
            );
        } else {
            setItem(
                    inventory,
                    13,
                    Material.SHIELD,
                    "&b&lCLAN WAR",
                    List.of(
                            "&7Belum ada perang aktif.",
                            "",
                            "&7Tantang clan lain untuk memulai",
                            "&7pertarungan antar-clan.",
                            "",
                            "&eGunakan command Clan War."
                    )
            );
        }

        if (pendingWar != null) {
            setItem(
                    inventory,
                    11,
                    Material.LIME_CONCRETE,
                    "&a&lTERIMA TANTANGAN",
                    List.of(
                            "&7Tantangan dari:",
                            "&f" + pendingWar.clanName(),
                            "",
                            "&eKlik untuk menerima perang."
                    )
            );

            setItem(
                    inventory,
                    15,
                    Material.RED_CONCRETE,
                    "&c&lTOLAK TANTANGAN",
                    List.of(
                            "&7Tantangan dari:",
                            "&f" + pendingWar.clanName(),
                            "",
                            "&eKlik untuk menolak perang."
                    )
            );
        } else {
            setItem(
                    inventory,
                    11,
                    Material.GRAY_CONCRETE,
                    "&7&lTIDAK ADA TANTANGAN",
                    List.of(
                            "&7Saat ini tidak ada",
                            "&7tantangan perang masuk."
                    )
            );

            setItem(
                    inventory,
                    15,
                    Material.GRAY_CONCRETE,
                    "&7&lTIDAK ADA TANTANGAN",
                    List.of(
                            "&7Tidak ada tantangan",
                            "&7yang bisa ditolak."
                    )
            );
        }

        setItem(
                inventory,
                22,
                Material.BARRIER,
                "&c&lTUTUP",
                List.of("&7Klik untuk menutup GUI.")
        );

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory()
                .getHolder() instanceof WarHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        if (slot != 11 && slot != 15) {
            return;
        }

        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (SQLException exception) {
            player.sendMessage(color(
                    "&cGagal mengambil data clan."
            ));
            plugin.getLogger().severe(
                    "ClanWarGUI click: " + exception.getMessage()
            );
            return;
        }

        if (clan == null) {
            player.closeInventory();
            player.sendMessage(color(
                    "&cKamu tidak memiliki clan."
            ));
            return;
        }

        PendingWar pendingWar = getPendingWar(
                String.valueOf(clan.getId())
        );

        if (pendingWar == null) {
            player.sendMessage(color(
                    "&cTidak ada tantangan Clan War."
            ));
            open(player);
            return;
        }

        player.closeInventory();

        ClanWarManager.Result result = slot == 11
                ? warManager.accept(player.getUniqueId())
                : warManager.deny(player.getUniqueId());

        switch (result) {
            case CHALLENGE_SENT:
            case WAR_ACCEPTED:
                player.sendMessage(color(
                        "&aTantangan Clan War berhasil diterima!"
                ));
                break;

            case WAR_DENIED:
                player.sendMessage(color(
                        "&cTantangan Clan War ditolak."
                ));
                break;

            case NO_PERMISSION:
                player.sendMessage(color(
                        "&cHanya Leader atau Co-Leader yang dapat melakukan ini."
                ));
                break;

            case ALREADY_IN_WAR:
                player.sendMessage(color(
                        "&cClan kamu sedang memiliki perang atau tantangan lain."
                ));
                break;

            case CHALLENGE_NOT_FOUND:
                player.sendMessage(color(
                        "&cTantangan sudah tidak tersedia."
                ));
                break;

            case NOT_IN_CLAN:
                player.sendMessage(color(
                        "&cKamu tidak tergabung dalam clan."
                ));
                break;

            case DATABASE_ERROR:
                player.sendMessage(color(
                        "&cTerjadi kesalahan database."
                ));
                break;

            default:
                player.sendMessage(color(
                        "&cAksi gagal: &f" + result.name()
                ));
                break;
        }
    }

    private PendingWar getPendingWar(String clanId) {
        String sql = """
                SELECT cw.id, c.name
                FROM clan_wars cw
                JOIN clans c ON c.id = cw.clan_a
                WHERE cw.clan_b = ?
                  AND cw.status = 'PENDING'
                ORDER BY cw.created_at ASC
                LIMIT 1
                """;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new PendingWar(
                                result.getLong("id"),
                                result.getString("name")
                        );
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "Gagal mengambil tantangan Clan War: "
                                + exception.getMessage()
                );
            }
        }

        return null;
    }

    private void fill(Inventory inventory) {
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

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record PendingWar(long id, String clanName) {
    }

    private static class WarHolder implements InventoryHolder {

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
