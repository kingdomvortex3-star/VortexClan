
package id.vortexclan.gui;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanAllianceManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ClanAllianceGUI implements Listener {

    private final VortexClan plugin;
    private final ClanAllianceManager allianceManager;
    private final Set<UUID> waitingInput = new HashSet<>();

    public ClanAllianceGUI(
            VortexClan plugin,
            ClanAllianceManager allianceManager
    ) {
        this.plugin = plugin;
        this.allianceManager = allianceManager;
    }

    public void open(Player player) {
        Clan clan;

        try {
            clan = plugin.getClanManager()
                    .getClanByPlayer(player.getUniqueId());
        } catch (SQLException exception) {
            player.sendMessage(color("&cGagal mengambil data clan."));
            plugin.getLogger().severe(exception.getMessage());
            return;
        }

        if (clan == null) {
            player.sendMessage(color("&cKamu belum memiliki clan!"));
            return;
        }

        String clanId = String.valueOf(clan.getId());

        Inventory inventory = Bukkit.createInventory(
                new AllianceHolder(),
                54,
                color("&8Clan Alliance")
        );

        List<ClanAllianceManager.AllianceData> alliances =
                allianceManager.getAlliances(clanId);

        List<ClanAllianceManager.AllianceInvite> invites =
                allianceManager.getPendingInvites(clanId);

        int slot = 10;

        for (ClanAllianceManager.AllianceData alliance : alliances) {
            if (slot > 16) {
                break;
            }

            Clan alliedClan = getClan(alliance.clanId());

            if (alliedClan == null) {
                continue;
            }

            inventory.setItem(slot++, createItem(
                    Material.EMERALD_BLOCK,
                    "&a&l" + alliedClan.getName(),
                    "",
                    "&7Tag: &b" + alliedClan.getTag(),
                    "&7Status: &aBeraliansi",
                    "",
                    "&cKlik untuk menghapus aliansi."
            ));
        }

        inventory.setItem(22, createItem(
                Material.NETHER_STAR,
                "&b&lKirim Undangan Aliansi",
                "",
                "&7Undang clan lain untuk beraliansi.",
                "",
                "&eKlik untuk memasukkan nama clan/tag."
        ));

        slot = 28;

        for (ClanAllianceManager.AllianceInvite invite : invites) {
            if (slot > 34) {
                break;
            }

            Clan sourceClan = getClan(invite.sourceClan());

            if (sourceClan == null) {
                continue;
            }

            inventory.setItem(slot++, createItem(
                    Material.PAPER,
                    "&e&l" + sourceClan.getName(),
                    "",
                    "&7Tag: &b" + sourceClan.getTag(),
                    "&7Mengajak clan kamu beraliansi.",
                    "",
                    "&aKlik kiri: Terima",
                    "&cKlik kanan: Tolak"
            ));
        }

        inventory.setItem(49, createItem(
                Material.ARROW,
                "&c&lTutup",
                "&7Klik untuk menutup menu."
        ));

        player.openInventory(inventory);
    }

    private Clan getClan(String clanId) {
        try {
            return plugin.getClanManager().getClanById(clanId);
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "[ClanAllianceGUI] " + exception.getMessage()
            );
            return null;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof AllianceHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 22) {
            requestInvite(player);
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot >= 10 && slot <= 16
                && clicked.getType() == Material.EMERALD_BLOCK) {
            removeAlliance(player, clicked);
            return;
        }

        if (slot >= 28 && slot <= 34
                && clicked.getType() == Material.PAPER) {
            handleInvite(player, clicked, event.getClick());
        }
    }

    private void requestInvite(Player player) {
        if (!hasManagementPermission(player)) {
            player.sendMessage(color(
                    "&cHanya Leader dan Co-Leader yang dapat mengundang clan!"
            ));
            return;
        }

        waitingInput.add(player.getUniqueId());
        player.closeInventory();

        player.sendMessage(color("&8&m------------------------------"));
        player.sendMessage(color("&b&lCLAN ALLIANCE"));
        player.sendMessage(color("&7Masukkan nama atau tag clan tujuan."));
        player.sendMessage(color("&7Ketik &cbatal &7untuk membatalkan."));
        player.sendMessage(color("&8&m------------------------------"));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!waitingInput.contains(uuid)) {
            return;
        }

        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        if (!waitingInput.remove(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("batal")) {
                player.sendMessage(color("&cUndangan aliansi dibatalkan."));
                open(player);
                return;
            }

            ClanAllianceManager.Result result =
                    allianceManager.invite(uuid, input);

            switch (result) {
                case INVITE_SENT ->
                        player.sendMessage(color(
                                "&aUndangan aliansi berhasil dikirim!"
                        ));

                case ALREADY_ALLIED ->
                        player.sendMessage(color(
                                "&cClan tersebut sudah menjadi sekutu!"
                        ));

                case CLAN_NOT_FOUND ->
                        player.sendMessage(color(
                                "&cClan tujuan tidak ditemukan!"
                        ));

                case SAME_CLAN ->
                        player.sendMessage(color(
                                "&cKamu tidak bisa mengundang clan sendiri!"
                        ));

                case NOT_IN_CLAN ->
                        player.sendMessage(color(
                                "&cKamu belum memiliki clan!"
                        ));

                case NO_PERMISSION ->
                        player.sendMessage(color(
                                "&cKamu tidak memiliki izin!"
                        ));

                case DATABASE_ERROR ->
                        player.sendMessage(color(
                                "&cTerjadi kesalahan database!"
                        ));

                default ->
                        player.sendMessage(color(
                                "&cUndangan gagal diproses!"
                        ));
            }

            open(player);
        });
    }

    private void handleInvite(
            Player player,
            ItemStack item,
            ClickType click
    ) {
        String sourceName = ChatColor.stripColor(
                item.getItemMeta().getDisplayName()
        );

        if (sourceName == null) {
            return;
        }

        sourceName = sourceName.replace("✦", "").trim();

        ClanAllianceManager.Result result;

        if (click == ClickType.RIGHT
                || click == ClickType.SHIFT_RIGHT) {
            result = allianceManager.deny(
                    player.getUniqueId(),
                    sourceName
            );
        } else {
            result = allianceManager.accept(
                    player.getUniqueId(),
                    sourceName
            );
        }

        switch (result) {
            case SUCCESS ->
                    player.sendMessage(color(
                            "&aAliansi berhasil diterima!"
                    ));

            case INVITE_DENIED ->
                    player.sendMessage(color(
                            "&cUndangan aliansi ditolak!"
                    ));

            case INVITE_NOT_FOUND ->
                    player.sendMessage(color(
                            "&cUndangan tidak ditemukan!"
                    ));

            case INVITE_EXPIRED ->
                    player.sendMessage(color(
                            "&cUndangan aliansi sudah kedaluwarsa!"
                    ));

            case NO_PERMISSION ->
                    player.sendMessage(color(
                            "&cHanya Leader dan Co-Leader yang dapat melakukan ini!"
                    ));

            case NOT_IN_CLAN ->
                    player.sendMessage(color(
                            "&cKamu belum memiliki clan!"
                    ));

            case DATABASE_ERROR ->
                    player.sendMessage(color(
                            "&cTerjadi kesalahan database!"
                    ));

            default ->
                    player.sendMessage(color(
                            "&cUndangan gagal diproses!"
                    ));
        }

        open(player);
    }

    private void removeAlliance(Player player, ItemStack item) {
        if (!hasManagementPermission(player)) {
            player.sendMessage(color(
                    "&cHanya Leader dan Co-Leader yang dapat menghapus aliansi!"
            ));
            return;
        }

        String clanName = ChatColor.stripColor(
                item.getItemMeta().getDisplayName()
        );

        if (clanName == null) {
            return;
        }

        clanName = clanName.replace("✦", "").trim();

        ClanAllianceManager.Result result =
                allianceManager.removeAlliance(
                        player.getUniqueId(),
                        clanName
                );

        switch (result) {
            case ALLIANCE_REMOVED ->
                    player.sendMessage(color(
                            "&aAliansi berhasil dihapus!"
                    ));

            case NOT_ALLIED ->
                    player.sendMessage(color(
                            "&cClan tersebut bukan sekutu kamu!"
                    ));

            case NO_PERMISSION ->
                    player.sendMessage(color(
                            "&cKamu tidak memiliki izin!"
                    ));

            case CLAN_NOT_FOUND ->
                    player.sendMessage(color(
                            "&cClan tidak ditemukan!"
                    ));

            case DATABASE_ERROR ->
                    player.sendMessage(color(
                            "&cTerjadi kesalahan database!"
                    ));

            default ->
                    player.sendMessage(color(
                            "&cAliansi gagal dihapus!"
                    ));
        }

        open(player);
    }

    private boolean hasManagementPermission(Player player) {
        try {
            String role = plugin.getClanManager()
                    .getMemberRole(player.getUniqueId());

            return role != null
                    && (role.equalsIgnoreCase("LEADER")
                    || role.equalsIgnoreCase("CO_LEADER"));

        } catch (SQLException exception) {
            plugin.getLogger().severe(exception.getMessage());
            return false;
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

            List<String> lines = new java.util.ArrayList<>();

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

    private static class AllianceHolder implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
