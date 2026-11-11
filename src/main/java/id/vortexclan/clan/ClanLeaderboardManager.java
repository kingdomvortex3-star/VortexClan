
package id.vortexclan.clan;

import id.vortexclan.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanLeaderboardManager {

    private final DatabaseManager databaseManager;

    public ClanLeaderboardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public enum SortType {
        LEVEL,
        WINS,
        BANK
    }

    public record ClanRank(
            int position,
            String clanId,
            String clanName,
            String clanTag,
            UUID leader,
            int level,
            long exp,
            double bank,
            int wins,
            int losses,
            int kills
    ) {}

    public List<ClanRank> getTopClans(
            SortType sortType,
            int limit
    ) throws SQLException {

        if (sortType == null) {
            throw new IllegalArgumentException(
                    "SortType tidak boleh null!"
            );
        }

        int safeLimit = Math.max(1, Math.min(100, limit));

        String sql = """
                SELECT id, name, tag, leader_uuid, level, exp, bank,
                       wins, losses, kills
                FROM clans
                ORDER BY %s
                LIMIT ?
                """.formatted(getOrderBy(sortType));

        List<ClanRank> result = new ArrayList<>();

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setInt(1, safeLimit);

                try (ResultSet rs = statement.executeQuery()) {
                    int position = 1;

                    while (rs.next()) {
                        result.add(mapRank(rs, position));
                        position++;
                    }
                }
            }
        }

        return result;
    }

    public ClanRank getClanRank(
            String clanId,
            SortType sortType
    ) throws SQLException {

        if (clanId == null || clanId.isBlank()) {
            return null;
        }

        if (sortType == null) {
            throw new IllegalArgumentException(
                    "SortType tidak boleh null!"
            );
        }

        String sql = """
                SELECT id, name, tag, leader_uuid, level, exp, bank,
                       wins, losses, kills
                FROM clans
                ORDER BY %s
                """.formatted(getOrderBy(sortType));

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try (PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {

                int position = 1;

                while (rs.next()) {
                    if (clanId.equals(rs.getString("id"))) {
                        return mapRank(rs, position);
                    }

                    position++;
                }
            }
        }

        return null;
    }

    public int getClanPosition(
            String clanId,
            SortType sortType
    ) throws SQLException {

        ClanRank rank = getClanRank(clanId, sortType);

        return rank == null ? -1 : rank.position();
    }

    public int getTotalClans() throws SQLException {

        String sql = "SELECT COUNT(*) FROM clans";

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {

                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String getOrderBy(SortType sortType) {
        return switch (sortType) {

            case LEVEL ->
                    """
                    level DESC,
                    exp DESC,
                    wins DESC,
                    name COLLATE NOCASE ASC,
                    id ASC
                    """;

            case WINS ->
                    """
                    wins DESC,
                    level DESC,
                    exp DESC,
                    name COLLATE NOCASE ASC,
                    id ASC
                    """;

            case BANK ->
                    """
                    bank DESC,
                    level DESC,
                    wins DESC,
                    name COLLATE NOCASE ASC,
                    id ASC
                    """;
        };
    }

    private ClanRank mapRank(
            ResultSet rs,
            int position
    ) throws SQLException {

        return new ClanRank(
                position,
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getInt("level"),
                rs.getLong("exp"),
                rs.getDouble("bank"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("kills")
        );
    }
}
