
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanRoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanMemberManagementGUI implements Listener {

    private static final int PAGE_SIZE = 45;

    private final VortexClan plugin;
    private final ClanRoleManager roleManager;

    public ClanMemberManagementGUI(
            VortexClan plugin,
            ClanRoleManager roleManager
    ) {
        this.plugin = plugin;
        this.roleManager = roleManager;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (Exception exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(exception.getMessage());
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan!"));
            return;
        }

        List<MemberData> members;

        try {
            members = getMembers(String.valueOf(clan.getId()));
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil daftar anggota."));
            plugin.getLogger().severe(
                    "[ClanMemberManagementGUI] " + exception.getMessage()
            );
            return;
        }

        int maxPage = Math.max(
                0,
                (members.size() - 1) / PAGE_SIZE
        );

        page = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(
                new MemberHolder(clan.getId(), page, members),
                54,
                color("&8Clan Member Management")
        );

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, members.size());

        for (int index = start; index < end; index++) {
            MemberData member = members.get(index);
            int slot = index - start;

            inventory.setItem(slot, createMemberItem(member));
        }

        inventory.setItem(45, createItem(
                Material.ARROW,
                "&c&lHalaman Sebelumnya",
                "&7Klik untuk kembali.",
                "&7Halaman: &f" + (page + 1) + "/" + (maxPage + 1)
        ));

        inventory.setItem(49, createItem(
                Material.BOOK,
                "&b&lPetunjuk",
                "",
                "&7Klik kiri: &aPromote",
                "&7Klik kanan: &cDemote",
                "",
                "&7Total anggota: &f" + members.size()
        ));

        inventory.setItem(53, createItem(
                Material.ARROW,
                "&a&lHalaman Berikutnya",
                "&7Klik untuk melanjutkan.",
                "&7Halaman: &f" + (page + 1) + "/" + (maxPage + 1)
        ));

        player.openInventory(inventory);
    }

    private List<MemberData> getMembers(String clanId)
            throws SQLException {

        List<MemberData> members = new ArrayList<>();

        String sql = """
                SELECT uuid, role
                FROM clan_members
                WHERE clan_id = ?
                ORDER BY
                    CASE role
                        WHEN 'LEADER' THEN 1
                        WHEN 'CO_LEADER' THEN 2
                        ELSE 3
                    END,
                    joined_at ASC
                """;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement = plugin
                    .getDatabaseManager()
                    .getConnection()
                    .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID uuid;

                        try {
                            uuid = UUID.fromString(result.getString("uuid"));
                        } catch (IllegalArgumentException exception) {
                            continue;
                        }

                        members.add(new MemberData(
                                uuid,
                                result.getString("role")
                        ));
                    }
                }
            }
        }

        return members;
    }

    private ItemStack createMemberItem(MemberData member) {
        OfflinePlayer offlinePlayer =
                Bukkit.getOfflinePlayer(member.uuid());

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);

            String name = offlinePlayer.getName();

            if (name == null) {
                name = member.uuid().toString().substring(0, 8);
            }

            meta.setDisplayName(color("&b&l" + name));

            List<String> lore = new ArrayList<>();

            lore.add(color(""));
            lore.add(color("&7Role: &f" + member.role()));
            lore.add(color("&7Status: " + (
                    offlinePlayer.isOnline()
                            ? "&aOnline"
                            : "&cOffline"
            )));
            lore.add(color(""));

            if (member.role().equalsIgnoreCase("LEADER")) {
                lore.add(color("&6Leader clan"));
            } else {
                lore.add(color("&aKlik kiri untuk promote"));
                lore.add(color("&cKlik kanan untuk demote"));
            }

            lore.add(color(""));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof MemberHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 45) {
            if (holder.page() > 0) {
                open(player, holder.page() - 1);
            }
            return;
        }

        if (slot == 53) {
            int maxPage = Math.max(
                    0,
                    (holder.members().size() - 1) / PAGE_SIZE
            );

            if (holder.page() < maxPage) {
                open(player, holder.page() + 1);
            }
            return;
        }

        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }

        if (slot >= holder.members().size()
                - holder.page() * PAGE_SIZE) {
            return;
        }

        int index = holder.page() * PAGE_SIZE + slot;

        if (index < 0 || index >= holder.members().size()) {
            return;
        }

        MemberData target = holder.members().get(index);

        if (target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(color("&cKamu tidak bisa mengubah role sendiri!"));
            return;
        }

        if (event.getClick() == ClickType.LEFT) {
            promote(player, target);
        } else if (event.getClick() == ClickType.RIGHT) {
            demote(player, target);
        }
    }

    private void promote(Player player, MemberData target) {
        ClanRoleManager.Result result = roleManager.promote(
                player.getUniqueId(),
                target.uuid()
        );

        switch (result) {
            case SUCCESS_PROMOTED ->
                    player.sendMessage(color("&aAnggota berhasil dipromosikan menjadi Co-Leader!"));

            case ALREADY_CO_LEADER ->
                    player.sendMessage(color("&cAnggota tersebut sudah menjadi Co-Leader!"));

            case CANNOT_MODIFY_LEADER ->
                    player.sendMessage(color("&cRole Leader tidak dapat diubah!"));

            case NO_PERMISSION ->
                    player.sendMessage(color("&cHanya Leader yang dapat mengatur role!"));

            case NOT_IN_CLAN ->
                    player.sendMessage(color("&cKamu tidak memiliki clan!"));

            case TARGET_NOT_IN_CLAN ->
                    player.sendMessage(color("&cAnggota tidak ditemukan!"));

            case NOT_SAME_CLAN ->
                    player.sendMessage(color("&cAnggota tersebut bukan bagian dari clan kamu!"));

            case CANNOT_MODIFY_SELF ->
                    player.sendMessage(color("&cKamu tidak bisa mengubah role sendiri!"));

            case DATABASE_ERROR ->
                    player.sendMessage(color("&cTerjadi kesalahan database!"));

            default ->
                    player.sendMessage(color("&cTidak dapat mengubah role anggota!"));
        }

        open(player, holderPage(player));
    }

    private void demote(Player player, MemberData target) {
        ClanRoleManager.Result result = roleManager.demote(
                player.getUniqueId(),
                target.uuid()
        );

        switch (result) {
            case SUCCESS_DEMOTED ->
                    player.sendMessage(color("&aCo-Leader berhasil diturunkan menjadi Member!"));

            case ALREADY_MEMBER ->
                    player.sendMessage(color("&cAnggota tersebut sudah menjadi Member!"));

            case CANNOT_MODIFY_LEADER ->
                    player.sendMessage(color("&cRole Leader tidak dapat diubah!"));

            case NO_PERMISSION ->
                    player.sendMessage(color("&cHanya Leader yang dapat mengatur role!"));

            case NOT_IN_CLAN ->
                    player.sendMessage(color("&cKamu tidak memiliki clan!"));

            case TARGET_NOT_IN_CLAN ->
                    player.sendMessage(color("&cAnggota tidak ditemukan!"));

            case NOT_SAME_CLAN ->
                    player.sendMessage(color("&cAnggota tersebut bukan bagian dari clan kamu!"));

            case CANNOT_MODIFY_SELF ->
                    player.sendMessage(color("&cKamu tidak bisa mengubah role sendiri!"));

            case DATABASE_ERROR ->
                    player.sendMessage(color("&cTerjadi kesalahan database!"));

            default ->
                    player.sendMessage(color("&cTidak dapat mengubah role anggota!"));
        }

        open(player, holderPage(player));
    }

    private int holderPage(Player player) {
        if (player.getOpenInventory().getTopInventory()
                .getHolder() instanceof MemberHolder holder) {
            return holder.page();
        }

        return 0;
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

    private record MemberData(UUID uuid, String role) {
    }

    private record MemberHolder(
            Object clanId,
            int page,
            List<MemberData> members
    ) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
