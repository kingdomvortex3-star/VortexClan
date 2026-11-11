
package id.vortexclan.clan;

import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClanWarLeaderboardManager {

    private final DatabaseManager databaseManager;

    public ClanWarLeaderboardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<WarLeaderboardEntry> getTop(String sortType, int limit) {
        return getTop(sortType, limit, 0);
    }

    public List<WarLeaderboardEntry> getTop(
            String sortType,
            int limit,
            int offset
    ) {
        List<WarLeaderboardEntry> entries = new ArrayList<>();

        String orderBy = getOrderBy(sortType);

        if (orderBy == null) {
            return entries;
        }

        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        String sql = """
                SELECT id, name, tag, wins, losses, kills
                FROM clans
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(orderBy);

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setInt(1, safeLimit);
                statement.setInt(2, safeOffset);

                try (ResultSet result = statement.executeQuery()) {
                    int position = safeOffset + 1;

                    while (result.next()) {
                        entries.add(new WarLeaderboardEntry(
                                position++,
                                result.getString("id"),
                                result.getString("name"),
                                result.getString("tag"),
                                result.getInt("wins"),
                                result.getInt("losses"),
                                result.getInt("kills")
                        ));
                    }
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }

        return entries;
    }

    public int getRank(String clanId, String sortType) {
        String orderBy = getOrderBy(sortType);

        if (orderBy == null || clanId == null) {
            return -1;
        }

        String sql = """
                SELECT id
                FROM clans
                ORDER BY %s
                """.formatted(orderBy);

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {

                int position = 1;

                while (result.next()) {
                    if (clanId.equals(result.getString("id"))) {
                        return position;
                    }

                    position++;
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }

        return -1;
    }

    public int getTotalClans() {
        String sql = "SELECT COUNT(*) FROM clans";

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {

                return result.next() ? result.getInt(1) : 0;

            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }

        return 0;
    }

    public int getTotalPages(int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }

        int total = getTotalClans();

        return Math.max(
                1,
                (int) Math.ceil(total / (double) pageSize)
        );
    }

    private String getOrderBy(String sortType) {
        if (sortType == null) {
            return null;
        }

        return switch (sortType.toLowerCase()) {
            case "wins" -> "wins DESC, kills DESC, name ASC";
            case "losses" -> "losses DESC, wins DESC, name ASC";
            case "kills" -> "kills DESC, wins DESC, name ASC";
            default -> null;
        };
    }

    public record WarLeaderboardEntry(
            int position,
            String clanId,
            String clanName,
            String clanTag,
            int wins,
            int losses,
            int kills
    ) {
    }
}
