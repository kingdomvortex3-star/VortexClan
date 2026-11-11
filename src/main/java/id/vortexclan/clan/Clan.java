
package id.vortexclan.clan;

import java.util.UUID;

public final class Clan {

    private final String id;
    private final String name;
    private final String tag;
    private final UUID leader;
    private final String description;

    private final int level;
    private final long exp;
    private final double bank;

    private final boolean pvpEnabled;

    private final int wins;
    private final int losses;
    private final int kills;

    private final long createdAt;
    private final long taxDueAt;

    public Clan(
            String id,
            String name,
            String tag,
            UUID leader,
            String description,
            int level,
            long exp,
            double bank,
            boolean pvpEnabled,
            int wins,
            int losses,
            int kills,
            long createdAt,
            long taxDueAt
    ) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.description = description;
        this.level = level;
        this.exp = exp;
        this.bank = bank;
        this.pvpEnabled = pvpEnabled;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.createdAt = createdAt;
        this.taxDueAt = taxDueAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public UUID getLeader() {
        return leader;
    }

    public UUID getLeaderUuid() {
        return leader;
    }

    public String getDescription() {
        return description;
    }

    public int getLevel() {
        return level;
    }

    public long getExp() {
        return exp;
    }

    public double getBank() {
        return bank;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getKills() {
        return kills;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getTaxDueAt() {
        return taxDueAt;
    }
}
