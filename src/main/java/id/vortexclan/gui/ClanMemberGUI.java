
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.database.DatabaseManager;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanMemberGUI implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int ENTRIES_PER_PAGE = 28;

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;

    public ClanMemberGUI(VortexClan plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void open(Player player) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null) {
                player.sendMessage(message("&cKamu belum memiliki clan!"));
                return;
            }

            List<MemberData> members = getMembers(clan.getId());

            int totalMembers = members.size();
            int onlineMembers = 0;

            for (MemberData member : members) {
                if (Bukkit.getPlayer(member.uuid()) != null) {
                    onlineMembers++;
                }
            }

            int totalPages = Math.max(
                    1,
                    (int) Math.ceil(
                            totalMembers / (double) ENTRIES_PER_PAGE
                    )
            );

            openPage(
                    player,
                    clan,
                    members,
                    0,
                    totalPages,
                    onlineMembers
            );

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Gagal membuka Clan Member GUI: "
                            + exception.getMessage()
            );

            player.sendMessage(message(
                    "&cGagal mengambil daftar anggota clan!"
            ));
        }
    }

    private void openPage(
            Player player,
            Clan clan,
            List<MemberData> members,
            int page,
            int totalPages,
            int onlineMembers
    ) {
        MemberHolder holder = new MemberHolder(
                page,
                totalPages,
                clan.getId()
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                color("&8&lCLAN MEMBERS")
        );

        holder.setInventory(inventory);

        fillBackground(inventory);

        inventory.setItem(
                4,
                createItem(
                        Material.PLAYER_HEAD,
                        "&b&lCLAN MEMBERS",
                        List.of(
                                "&7Clan: &f" + clan.getName(),
                                "&7Tag: &b[" + clan.getTag() + "]",
                                "",
                                "&7Total Anggota: &f" + members.size(),
                                "&7Online: &a" + onlineMembers,
                                "&7Offline: &c"
                                        + (members.size() - onlineMembers),
                                "",
                                "&7Halaman: &f" + (page + 1)
                                        + "&7/&f" + totalPages
                        )
                )
        );

        int start = page * ENTRIES_PER_PAGE;
        int end = Math.min(
                start + ENTRIES_PER_PAGE,
                members.size()
        );

        for (int index = start; index < end; index++) {
            MemberData member = members.get(index);

            Player online = Bukkit.getPlayer(member.uuid());

            boolean isOnline = online != null;

            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.uuid());

            String playerName = isOnline
                    ? online.getName()
                    : offline.getName();

            if (playerName == null || playerName.isBlank()) {
                playerName = member.uuid().toString().substring(0, 8);
            }

            Material material = isOnline
                    ? Material.PLAYER_HEAD
                    : Material.SKELETON_SKULL;

            String status = isOnline
                    ? "&aONLINE"
                    : "&cOFFLINE";

            String role = formatRole(member.role());

            List<String> lore = new ArrayList<>();

            lore.add("&7Status: " + status);
            lore.add("&7Role: " + role);
            lore.add("");

            if (member.role().equalsIgnoreCase("LEADER")) {
                lore.add("&6Pemimpin clan.");
            } else if (member.role().equalsIgnoreCase("CO_LEADER")) {
                lore.add("&bWakil pemimpin clan.");
            } else {
                lore.add("&7Anggota clan.");
            }

            int slot = ENTRY_SLOTS[index - start];

            inventory.setItem(
                    slot,
                    createItem(
                            material,
                            "&f" + playerName,
                            lore
                    )
            );
        }

        inventory.setItem(
                45,
                createItem(
                        Material.ARROW,
                        "&e&lSEBELUMNYA",
                        List.of(
                                page > 0
                                        ? "&eKlik untuk kembali!"
                                        : "&cTidak ada halaman sebelumnya!"
                        )
                )
        );

        inventory.setItem(
                49,
                createItem(
                        Material.BARRIER,
                        "&c&lTUTUP",
                        List.of("&7Klik untuk menutup menu.")
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.ARROW,
                        "&e&lSELANJUTNYA",
                        List.of(
                                page + 1 < totalPages
                                        ? "&eKlik untuk melanjutkan!"
                                        : "&cTidak ada halaman selanjutnya!"
                        )
                )
        );

        player.openInventory(inventory);
    }

    private List<MemberData> getMembers(String clanId)
            throws SQLException {

        List<MemberData> members = new ArrayList<>();

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            String sql = """
                    SELECT uuid, role, joined_at
                    FROM clan_members
                    WHERE clan_id = ?
                    ORDER BY
                        CASE UPPER(role)
                            WHEN 'LEADER' THEN 1
                            WHEN 'CO_LEADER' THEN 2
                            ELSE 3
                        END,
                        joined_at ASC
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(
                                rs.getString("uuid")
                        );

                        members.add(new MemberData(
                                uuid,
                                rs.getString("role"),
                                rs.getLong("joined_at")
                        ));
                    }
                }
            }
        }

        return members;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, background);
        }

        int[] emptySlots = new int[ENTRY_SLOTS.length + 4];

        System.arraycopy(
                ENTRY_SLOTS,
                0,
                emptySlots,
                0,
                ENTRY_SLOTS.length
        );

        emptySlots[28] = 4;
        emptySlots[29] = 45;
        emptySlots[30] = 49;
        emptySlots[31] = 53;

        for (int slot : emptySlots) {
            inventory.setItem(slot, null);
        }
    }

    private String formatRole(String role) {
        if (role == null) {
            return "&7UNKNOWN";
        }

        return switch (role.toUpperCase()) {
            case "LEADER" -> "&6LEADER";
            case "CO_LEADER", "COLEADER" -> "&bCO-LEADER";
            default -> "&fMEMBER";
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

    private String message(String text) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        return color(prefix + " " + text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder()
                instanceof MemberHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot == 45 && holder.getPage() > 0) {
            refreshPage(player, holder, holder.getPage() - 1);
            return;
        }

        if (slot == 53
                && holder.getPage() + 1 < holder.getTotalPages()) {
            refreshPage(player, holder, holder.getPage() + 1);
        }
    }

    private void refreshPage(
            Player player,
            MemberHolder holder,
            int page
    ) {
        try {
            Clan clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());

            if (clan == null
                    || !clan.getId().equals(holder.getClanId())) {
                player.closeInventory();
                player.sendMessage(message(
                        "&cKamu bukan lagi anggota clan ini!"
                ));
                return;
            }

            List<MemberData> members = getMembers(clan.getId());

            int online = 0;

            for (MemberData member : members) {
                if (Bukkit.getPlayer(member.uuid()) != null) {
                    online++;
                }
            }

            int pages = Math.max(
                    1,
                    (int) Math.ceil(
                            members.size() / (double) ENTRIES_PER_PAGE
                    )
            );

            int safePage = Math.min(page, pages - 1);

            openPage(
                    player,
                    clan,
                    members,
                    safePage,
                    pages,
                    online
            );

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Gagal memperbarui Clan Member GUI: "
                            + exception.getMessage()
            );

            player.sendMessage(message(
                    "&cGagal memperbarui daftar anggota!"
            ));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof MemberHolder) {
            event.setCancelled(true);
        }
    }

    private record MemberData(
            UUID uuid,
            String role,
            long joinedAt
    ) {
    }

    private static class MemberHolder implements InventoryHolder {

        private final int page;
        private final int totalPages;
        private final String clanId;

        private Inventory inventory;

        private MemberHolder(
                int page,
                int totalPages,
                String clanId
        ) {
            this.page = page;
            this.totalPages = totalPages;
            this.clanId = clanId;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public String getClanId() {
            return clanId;
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
