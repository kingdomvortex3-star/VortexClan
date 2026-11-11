
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ClanWarRecoveryManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;
    private final ClanWarManager warManager;

    public ClanWarRecoveryManager(
            VortexClan plugin,
            DatabaseManager databaseManager,
            ClanWarManager warManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.warManager = warManager;
    }

    public void recover() {
        List<Long> interruptedWars = new ArrayList<>();

        synchronized (databaseManager.getLock()) {
            try {
                recoverCountdownWars();

                String sql = """
                        SELECT id
                        FROM clan_wars
                        WHERE status = 'ACTIVE'
                        ORDER BY id ASC
                        """;

                try (PreparedStatement statement =
                             databaseManager.getConnection()
                                     .prepareStatement(sql);
                     ResultSet result = statement.executeQuery()) {

                    while (result.next()) {
                        interruptedWars.add(result.getLong("id"));
                    }
                }

            } catch (SQLException exception) {
                plugin.getLogger().severe(
                        "[VortexClan] Gagal membaca data recovery Clan War: "
                                + exception.getMessage()
                );
                return;
            }
        }

        int recovered = 0;

        for (long warId : interruptedWars) {
            try {
                warManager.endWar(warId);
                recovered++;

            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "[VortexClan] Gagal memulihkan Clan War ID "
                                + warId + ": "
                                + exception.getMessage()
                );
            }
        }

        plugin.getLogger().info(
                "[VortexClan] Recovery selesai. "
                        + recovered + " perang aktif diproses."
        );
    }

    private void recoverCountdownWars() throws SQLException {
        String sql = """
                UPDATE clan_wars
                SET status = 'PENDING',
                    started_at = NULL
                WHERE status = 'COUNTDOWN'
                """;

        try (PreparedStatement statement =
                     databaseManager.getConnection()
                             .prepareStatement(sql)) {

            int updated = statement.executeUpdate();

            if (updated > 0) {
                plugin.getLogger().info(
                        "[VortexClan] " + updated
                                + " countdown dikembalikan menjadi pending."
                );
            }
        }
    }
}
