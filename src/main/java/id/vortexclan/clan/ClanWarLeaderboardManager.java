
package id.vortexclan.clan;

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
        List<WarLeaderboardEntry> entries = new ArrayList<>();

        String orderBy = getOrderBy(sortType);

        if (orderBy == null) {
            return entries;
        }

        limit = Math.max(1, Math.min(limit, 100));

        String sql = """
                SELECT id, name, tag, wins, losses, kills
                FROM clans
                ORDER BY %s
                LIMIT ?
                """.formatted(orderBy);

        synchronized (databaseManager.getLock()) {
            try (PreparedStatement statement = databaseManager
                    .getConnection().prepareStatement(sql)) {

                statement.setInt(1, limit);

                try (ResultSet result = statement.executeQuery()) {
                    int position = 1;

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

        if (orderBy == null) {
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
                    if (result.getString("id").equals(clanId)) {
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

                if (result.next()) {
                    return result.getInt(1);
                }

            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }

        return 0;
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
