
package id.vortexclan.placeholder;

import id.vortexclan.VortexClan;
import id.vortexclan.clan.Clan;
import id.vortexclan.clan.ClanLeaderboardManager.SortType;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VortexClanExpansion extends PlaceholderExpansion {

    private static final Pattern TOP_PATTERN = Pattern.compile(
            "^top_(name|tag|level|bank|wins|losses|kills|members)_(\\d+)$"
    );

    private final VortexClan plugin;

    public VortexClanExpansion(VortexClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vortexclan";
    }

    @Override
    public String getAuthor() {
        return "VortexTeam";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(
            OfflinePlayer offlinePlayer,
            String params
    ) {
        if (offlinePlayer == null || params == null) {
            return "";
        }

        UUID uuid = offlinePlayer.getUniqueId();
        String parameter = params.toLowerCase(Locale.ROOT);

        Matcher matcher = TOP_PATTERN.matcher(parameter);

        if (matcher.matches()) {
            try {
                int position = Integer.parseInt(matcher.group(2));

                return getTopPlaceholder(
                        matcher.group(1),
                        position
                );

            } catch (NumberFormatException exception) {
                return "";
            }
        }

        Clan clan;

        try {
            clan = plugin.getClanManager().getClanByPlayer(uuid);

        } catch (SQLException exception) {
            plugin.getLogger().warning(
                    "PlaceholderAPI clan error: "
                            + exception.getMessage()
            );
            return "";
        }

        if (clan == null) {
            return getNoClanValue(parameter);
        }

        String clanId = clan.getId();

        return switch (parameter) {
            case "name" -> clan.getName();
            case "tag" -> clan.getTag();
            case "level" -> String.valueOf(clan.getLevel());
            case "exp" -> String.valueOf(clan.getExp());
            case "bank" -> formatMoney(clan.getBank());
            case "wins" -> String.valueOf(clan.getWins());
            case "losses" -> String.valueOf(clan.getLosses());
            case "kills" -> String.valueOf(clan.getKills());
            case "pvp" -> clan.isPvpEnabled() ? "ON" : "OFF";
            case "role" -> getRole(uuid);
            case "members" -> String.valueOf(getMemberCount(clanId));
            case "max_members" -> String.valueOf(
                    plugin.getClanManager().getCapacity(clan.getLevel())
            );
            case "online" -> String.valueOf(getOnlineMembers(clanId));
            case "rank" -> getClanRank(clanId);
            case "tax_due" -> getTaxDue(clan.getTaxDueAt());
            default -> "";
        };
    }

    private String getNoClanValue(String parameter) {
        return switch (parameter) {
            case "name", "tag", "role" -> "None";

            case "level", "exp", "bank", "wins", "losses",
                 "kills", "members", "max_members", "online" -> "0";

            case "pvp" -> "OFF";
            case "rank", "tax_due" -> "-";

            default -> "";
        };
    }

    private String getClanRank(String clanId) {
        try {
            int rank = plugin.getClanLeaderboardManager()
                    .getClanPosition(clanId, SortType.LEVEL);

            return rank > 0 ? String.valueOf(rank) : "-";

        } catch (SQLException exception) {
            return "-";
        }
    }

    private String getTopPlaceholder(
            String type,
            int position
    ) {
        if (position < 1 || position > 1000) {
            return "";
        }

        String orderBy = switch (type) {
            case "name", "tag" -> "name ASC";
            case "level" -> "level DESC, exp DESC, name ASC";
            case "bank" -> "bank DESC, name ASC";
            case "wins" -> "wins DESC, kills DESC, name ASC";
            case "losses" -> "losses DESC, name ASC";
            case "kills" -> "kills DESC, wins DESC, name ASC";
            case "members" -> "member_count DESC, name ASC";
            default -> null;
        };

        if (orderBy == null) {
            return "";
        }

        String sql = """
                SELECT
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
                LIMIT 1 OFFSET ?
                """.formatted(orderBy);

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement(sql)) {

                statement.setInt(1, position - 1);

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return "";
                    }

                    return switch (type) {
                        case "name" ->
                                result.getString("name");

                        case "tag" ->
                                result.getString("tag");

                        case "level" ->
                                String.valueOf(result.getInt("level"));

                        case "bank" ->
                                formatMoney(result.getDouble("bank"));

                        case "wins" ->
                                String.valueOf(result.getInt("wins"));

                        case "losses" ->
                                String.valueOf(result.getInt("losses"));

                        case "kills" ->
                                String.valueOf(result.getInt("kills"));

                        case "members" ->
                                String.valueOf(
                                        result.getInt("member_count")
                                );

                        default -> "";
                    };
                }

            } catch (SQLException exception) {
                plugin.getLogger().warning(
                        "Leaderboard placeholder error: "
                                + exception.getMessage()
                );
            }
        }

        return "";
    }

    private String getRole(UUID uuid) {
        try {
            String role = plugin.getClanManager()
                    .getMemberRole(uuid);

            if (role == null) {
                return "None";
            }

            return switch (role.toUpperCase(Locale.ROOT)) {
                case "LEADER" -> "Leader";
                case "CO_LEADER" -> "Co-Leader";
                default -> "Member";
            };

        } catch (SQLException exception) {
            return "None";
        }
    }

    private int getMemberCount(String clanId) {
        String sql = """
                SELECT COUNT(*)
                FROM clan_members
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
                return 0;
            }
        }
    }

    private int getOnlineMembers(String clanId) {
        String sql = """
                SELECT uuid
                FROM clan_members
                WHERE clan_id = ?
                """;

        int online = 0;

        synchronized (plugin.getDatabaseManager().getLock()) {
            try (PreparedStatement statement =
                         plugin.getDatabaseManager()
                                 .getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID uuid;

                        try {
                            uuid = UUID.fromString(
                                    result.getString("uuid")
                            );
                        } catch (IllegalArgumentException exception) {
                            continue;
                        }

                        Player player = Bukkit.getPlayer(uuid);

                        if (player != null && player.isOnline()) {
                            online++;
                        }
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().warning(
                        "Gagal mengambil online clan members: "
                                + exception.getMessage()
                );
            }
        }

        return online;
    }

    private String getTaxDue(long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }

        long remaining =
                timestamp - (System.currentTimeMillis() / 1000L);

        if (remaining <= 0) {
            return "Due";
        }

        long days = remaining / 86400;
        long hours = (remaining % 86400) / 3600;
        long minutes = (remaining % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }

        return minutes + "m";
    }

    private synchronized String formatMoney(double amount) {
        return new DecimalFormat("#,##0.##").format(amount);
    }
}
