
package id.vortexclan.clan;

import id.vortexclan.VortexClan;
import id.vortexclan.database.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanWarManager {

    private final VortexClan plugin;
    private final DatabaseManager databaseManager;

    private final Map<Long, BukkitTask> countdownTasks =
            new ConcurrentHashMap<>();

    private final Map<Long, BukkitTask> durationTasks =
            new ConcurrentHashMap<>();

    public ClanWarManager(
            VortexClan plugin,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public enum Result {
        SUCCESS,
        CHALLENGE_SENT,
        WAR_ACCEPTED,
        WAR_DENIED,
        WAR_STARTED,
        WAR_FINISHED,
        NOT_IN_CLAN,
        NO_PERMISSION,
        CLAN_NOT_FOUND,
        SAME_CLAN,
        ALREADY_IN_WAR,
        CHALLENGE_NOT_FOUND,
        NOT_PARTICIPANT,
        WAR_NOT_ACTIVE,
        DATABASE_ERROR
    }

    public record WarData(
            long id,
            String clanA,
            String clanB,
            String status,
            int killsA,
            int killsB,
            String winner,
            long createdAt,
            long startedAt,
            long endedAt
    ) {
    }

    public Result challenge(UUID playerUuid, String targetName) {
        if (playerUuid == null || targetName == null
                || targetName.isBlank()) {
            return Result.CLAN_NOT_FOUND;
        }

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                MemberData challenger = getMember(connection, playerUuid);

                if (challenger == null) {
                    return Result.NOT_IN_CLAN;
                }

                if (!canManageWar(challenger.role())) {
                    return Result.NO_PERMISSION;
                }

                ClanData target = findClan(connection, targetName);

                if (target == null) {
                    return Result.CLAN_NOT_FOUND;
                }

                if (challenger.clanId().equals(target.id())) {
                    return Result.SAME_CLAN;
                }

                if (hasOngoingWar(connection, challenger.clanId())
                        || hasOngoingWar(connection, target.id())) {
                    return Result.ALREADY_IN_WAR;
                }

                String sql = """
                        INSERT INTO clan_wars (
                            clan_a,
                            clan_b,
                            status,
                            kills_a,
                            kills_b,
                            created_at
                        )
                        VALUES (?, ?, 'PENDING', 0, 0, ?)
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setString(1, challenger.clanId());
                    statement.setString(2, target.id());
                    statement.setLong(3, System.currentTimeMillis());

                    statement.executeUpdate();
                }

                notifyClan(
                        challenger.clanId(),
                        "&aTantangan Clan War berhasil dikirim kepada &b"
                                + target.name() + "&a!"
                );

                notifyClan(
                        target.id(),
                        "&cClan &b" + challenger.clanName()
                                + " &cmengajak clan kamu berperang!"
                                + "\n&7Gunakan &a/clan war accept"
                                + " &7atau &c/clan war deny"
                );

                return Result.CHALLENGE_SENT;

            } catch (SQLException exception) {
                logError("Gagal membuat tantangan Clan War", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result accept(UUID playerUuid) {
        if (playerUuid == null) {
            return Result.NOT_IN_CLAN;
        }

        long warId;
        String clanA;
        String clanB;

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                MemberData member = getMember(connection, playerUuid);

                if (member == null) {
                    return Result.NOT_IN_CLAN;
                }

                if (!canManageWar(member.role())) {
                    return Result.NO_PERMISSION;
                }

                String sql = """
                        SELECT id, clan_a, clan_b
                        FROM clan_wars
                        WHERE clan_b = ?
                          AND status = 'PENDING'
                        ORDER BY created_at ASC
                        LIMIT 1
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setString(1, member.clanId());

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            return Result.CHALLENGE_NOT_FOUND;
                        }

                        warId = rs.getLong("id");
                        clanA = rs.getString("clan_a");
                        clanB = rs.getString("clan_b");
                    }
                }

                if (hasOtherOngoingWar(connection, clanA, warId)
                        || hasOtherOngoingWar(connection, clanB, warId)) {
                    return Result.ALREADY_IN_WAR;
                }

                String updateSql = """
                        UPDATE clan_wars
                        SET status = 'COUNTDOWN'
                        WHERE id = ?
                          AND status = 'PENDING'
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {

                    statement.setLong(1, warId);

                    if (statement.executeUpdate() <= 0) {
                        return Result.CHALLENGE_NOT_FOUND;
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal menerima tantangan Clan War", exception);
                return Result.DATABASE_ERROR;
            }
        }

        notifyClan(clanA, "&aTantangan Clan War diterima!");
        notifyClan(clanB, "&aKamu menerima tantangan Clan War!");

        startCountdown(warId, clanA, clanB);

        return Result.WAR_ACCEPTED;
    }

    public Result deny(UUID playerUuid) {
        if (playerUuid == null) {
            return Result.NOT_IN_CLAN;
        }

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                MemberData member = getMember(connection, playerUuid);

                if (member == null) {
                    return Result.NOT_IN_CLAN;
                }

                if (!canManageWar(member.role())) {
                    return Result.NO_PERMISSION;
                }

                String sql = """
                        UPDATE clan_wars
                        SET status = 'DENIED',
                            ended_at = ?
                        WHERE id = (
                            SELECT id
                            FROM clan_wars
                            WHERE clan_b = ?
                              AND status = 'PENDING'
                            ORDER BY created_at ASC
                            LIMIT 1
                        )
                        AND status = 'PENDING'
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setLong(1, System.currentTimeMillis());
                    statement.setString(2, member.clanId());

                    if (statement.executeUpdate() <= 0) {
                        return Result.CHALLENGE_NOT_FOUND;
                    }
                }

                return Result.WAR_DENIED;

            } catch (SQLException exception) {
                logError("Gagal menolak tantangan Clan War", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    private void startCountdown(
            long warId,
            String clanA,
            String clanB
    ) {
        int countdown = Math.max(
                1,
                plugin.getConfig().getInt(
                        "war.countdown-seconds",
                        10
                )
        );

        notifyWar(
                clanA,
                clanB,
                "&eClan War akan dimulai dalam &f"
                        + countdown + " detik&e!"
        );

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    countdownTasks.remove(warId);

                    if (startWar(warId)) {
                        notifyWar(
                                clanA,
                                clanB,
                                "&c&lCLAN WAR DIMULAI!"
                        );

                        scheduleWarEnd(warId);
                    }
                },
                countdown * 20L
        );

        countdownTasks.put(warId, task);
    }

    private boolean startWar(long warId) {
        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                String sql = """
                        UPDATE clan_wars
                        SET status = 'ACTIVE',
                            started_at = ?
                        WHERE id = ?
                          AND status = 'COUNTDOWN'
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setLong(1, System.currentTimeMillis());
                    statement.setLong(2, warId);

                    return statement.executeUpdate() > 0;
                }

            } catch (SQLException exception) {
                logError("Gagal memulai Clan War", exception);
                return false;
            }
        }
    }

    private void scheduleWarEnd(long warId) {
        int duration = Math.max(
                1,
                plugin.getConfig().getInt(
                        "war.duration-minutes",
                        15
                )
        );

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    durationTasks.remove(warId);
                    endWar(warId);
                },
                duration * 60L * 20L
        );

        durationTasks.put(warId, task);
    }

    public Result recordKill(UUID killerUuid, UUID victimUuid) {
        if (killerUuid == null || victimUuid == null) {
            return Result.NOT_PARTICIPANT;
        }

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                MemberData killer = getMember(connection, killerUuid);
                MemberData victim = getMember(connection, victimUuid);

                if (killer == null || victim == null) {
                    return Result.NOT_PARTICIPANT;
                }

                if (killer.clanId().equals(victim.clanId())) {
                    return Result.NOT_PARTICIPANT;
                }

                String sql = """
                        UPDATE clan_wars
                        SET kills_a = CASE
                            WHEN clan_a = ? THEN kills_a + 1
                            ELSE kills_a
                        END,
                        kills_b = CASE
                            WHEN clan_b = ? THEN kills_b + 1
                            ELSE kills_b
                        END
                        WHERE status = 'ACTIVE'
                          AND (
                              (clan_a = ? AND clan_b = ?)
                              OR
                              (clan_a = ? AND clan_b = ?)
                          )
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setString(1, killer.clanId());
                    statement.setString(2, killer.clanId());
                    statement.setString(3, killer.clanId());
                    statement.setString(4, victim.clanId());
                    statement.setString(5, victim.clanId());
                    statement.setString(6, killer.clanId());

                    return statement.executeUpdate() > 0
                            ? Result.SUCCESS
                            : Result.WAR_NOT_ACTIVE;
                }

            } catch (SQLException exception) {
                logError("Gagal mencatat kill Clan War", exception);
                return Result.DATABASE_ERROR;
            }
        }
    }

    public Result endWar(long warId) {
        String clanA;
        String clanB;
        String winner = null;
        int killsA;
        int killsB;
        double reward = Math.max(
                0,
                plugin.getConfig().getDouble("war.reward", 10000)
        );

        synchronized (databaseManager.getLock()) {
            Connection connection = databaseManager.getConnection();

            try {
                connection.setAutoCommit(false);

                String selectSql = """
                        SELECT clan_a, clan_b, kills_a, kills_b
                        FROM clan_wars
                        WHERE id = ?
                          AND status = 'ACTIVE'
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(selectSql)) {

                    statement.setLong(1, warId);

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return Result.WAR_NOT_ACTIVE;
                        }

                        clanA = rs.getString("clan_a");
                        clanB = rs.getString("clan_b");
                        killsA = rs.getInt("kills_a");
                        killsB = rs.getInt("kills_b");
                    }
                }

                if (killsA > killsB) {
                    winner = clanA;
                } else if (killsB > killsA) {
                    winner = clanB;
                }

                String updateWar = """
                        UPDATE clan_wars
                        SET status = 'FINISHED',
                            winner = ?,
                            ended_at = ?
                        WHERE id = ?
                          AND status = 'ACTIVE'
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateWar)) {

                    statement.setString(1, winner);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setLong(3, warId);

                    if (statement.executeUpdate() <= 0) {
                        connection.rollback();
                        return Result.WAR_NOT_ACTIVE;
                    }
                }

                updateClanStats(connection, clanA, killsA,
                        winner != null && winner.equals(clanA));

                updateClanStats(connection, clanB, killsB,
                        winner != null && winner.equals(clanB));

                if (winner != null && reward > 0) {
                    rewardClan(connection, winner, reward);
                }

                connection.commit();

            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                logError("Gagal menyelesaikan Clan War", exception);
                return Result.DATABASE_ERROR;

            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException exception) {
                    logError("Gagal memulihkan transaksi database", exception);
                }
            }
        }

        if (winner == null) {
            notifyWar(
                    clanA,
                    clanB,
                    "&eCLAN WAR BERAKHIR SERI!"
            );
        } else {
            notifyClan(
                    winner,
                    "&6&lCLAN WAR DIMENANGKAN!"
                            + " &7Reward: &a$" + reward
            );

            String loser = winner.equals(clanA) ? clanB : clanA;

            notifyClan(
                    loser,
                    "&cClan kamu kalah dalam Clan War!"
            );
        }

        return Result.WAR_FINISHED;
    }

    private void updateClanStats(
            Connection connection,
            String clanId,
            int kills,
            boolean won
    ) throws SQLException {

        String sql = won
                ? """
                  UPDATE clans
                  SET wins = wins + 1,
                      kills = kills + ?
                  WHERE id = ?
                  """
                : """
                  UPDATE clans
                  SET losses = losses + 1,
                      kills = kills + ?
                  WHERE id = ?
                  """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, kills);
            statement.setString(2, clanId);
            statement.executeUpdate();
        }
    }

    private void rewardClan(
            Connection connection,
            String clanId,
            double reward
    ) throws SQLException {

        String updateSql = """
                UPDATE clans
                SET bank = bank + ?
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(updateSql)) {

            statement.setDouble(1, reward);
            statement.setString(2, clanId);

            if (statement.executeUpdate() <= 0) {
                throw new SQLException(
                        "Clan pemenang tidak ditemukan!"
                );
            }
        }

        double newBalance;

        String balanceSql = """
                SELECT bank
                FROM clans
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(balanceSql)) {

            statement.setString(1, clanId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Gagal mengambil saldo reward!"
                    );
                }

                newBalance = rs.getDouble("bank");
            }
        }

        String historySql = """
                INSERT INTO clan_transactions (
                    clan_id,
                    player_uuid,
                    transaction_type,
                    amount,
                    balance_after,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(historySql)) {

            statement.setString(1, clanId);
            statement.setString(2, "SYSTEM");
            statement.setString(3, "WAR_REWARD");
            statement.setDouble(4, reward);
            statement.setDouble(5, newBalance);
            statement.setLong(6, System.currentTimeMillis());

            statement.executeUpdate();
        }
    }

    public WarData getActiveWar(String clanId)
            throws SQLException {

        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT id, clan_a, clan_b, status,
                           kills_a, kills_b, winner,
                           created_at, started_at, ended_at
                    FROM clan_wars
                    WHERE (clan_a = ? OR clan_b = ?)
                      AND status IN ('COUNTDOWN', 'ACTIVE')
                    ORDER BY created_at DESC
                    LIMIT 1
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);
                statement.setString(2, clanId);

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }

                    return mapWar(rs);
                }
            }
        }
    }

    private WarData mapWar(ResultSet rs) throws SQLException {
        return new WarData(
                rs.getLong("id"),
                rs.getString("clan_a"),
                rs.getString("clan_b"),
                rs.getString("status"),
                rs.getInt("kills_a"),
                rs.getInt("kills_b"),
                rs.getString("winner"),
                rs.getLong("created_at"),
                rs.getLong("started_at"),
                rs.getLong("ended_at")
        );
    }

    private MemberData getMember(
            Connection connection,
            UUID uuid
    ) throws SQLException {

        String sql = """
                SELECT cm.clan_id, cm.role, c.name
                FROM clan_members cm
                JOIN clans c ON c.id = cm.clan_id
                WHERE cm.uuid = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new MemberData(
                        rs.getString("clan_id"),
                        rs.getString("role"),
                        rs.getString("name")
                );
            }
        }
    }

    private ClanData findClan(
            Connection connection,
            String nameOrTag
    ) throws SQLException {

        String sql = """
                SELECT id, name, tag
                FROM clans
                WHERE LOWER(name) = LOWER(?)
                   OR LOWER(tag) = LOWER(?)
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, nameOrTag);
            statement.setString(2, nameOrTag);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new ClanData(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("tag")
                );
            }
        }
    }

    private boolean hasOngoingWar(
            Connection connection,
            String clanId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM clan_wars
                WHERE (clan_a = ? OR clan_b = ?)
                  AND status IN ('PENDING', 'COUNTDOWN', 'ACTIVE')
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, clanId);
            statement.setString(2, clanId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasOtherOngoingWar(
            Connection connection,
            String clanId,
            long excludedWarId
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM clan_wars
                WHERE (clan_a = ? OR clan_b = ?)
                  AND id != ?
                  AND status IN ('PENDING', 'COUNTDOWN', 'ACTIVE')
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, clanId);
            statement.setString(2, clanId);
            statement.setLong(3, excludedWarId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean canManageWar(String role) {
        if (role == null) {
            return false;
        }

        return switch (role.toUpperCase().replace("-", "_")) {
            case "LEADER", "CO_LEADER", "COLEADER" -> true;
            default -> false;
        };
    }

    private void notifyClan(String clanId, String text) {
        synchronized (databaseManager.getLock()) {
            String sql = """
                    SELECT uuid
                    FROM clan_members
                    WHERE clan_id = ?
                    """;

            try (PreparedStatement statement =
                         databaseManager.getConnection()
                                 .prepareStatement(sql)) {

                statement.setString(1, clanId);

                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        Player player = Bukkit.getPlayer(uuid);

                        if (player != null) {
                            player.sendMessage(message(text));
                        }
                    }
                }

            } catch (SQLException exception) {
                logError("Gagal mengirim notifikasi Clan War", exception);
            }
        }
    }

    private void notifyWar(
            String clanA,
            String clanB,
            String text
    ) {
        notifyClan(clanA, text);
        notifyClan(clanB, text);
    }

    private String message(String text) {
        String prefix = plugin.getConfig().getString(
                "settings.prefix",
                "&8[&bVortexClan&8]"
        );

        return ChatColor.translateAlternateColorCodes(
                '&',
                prefix + " " + text
        );
    }

    private void logError(String message, SQLException exception) {
        plugin.getLogger().severe(
                message + ": " + exception.getMessage()
        );
    }

    public void cancelTasks() {
        countdownTasks.values().forEach(BukkitTask::cancel);
        durationTasks.values().forEach(BukkitTask::cancel);

        countdownTasks.clear();
        durationTasks.clear();
    }

    private record MemberData(
            String clanId,
            String role,
            String clanName
    ) {
    }

    private record ClanData(
            String id,
            String name,
            String tag
    ) {
    }
}
