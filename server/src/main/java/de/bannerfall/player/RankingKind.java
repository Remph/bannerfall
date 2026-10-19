package de.bannerfall.player;

import de.bannerfall.statistics.Stat;

/** What a ranking sorts by (04-api, {@code GET /api/ranking?by=}; the tabs since 1.5). */
public enum RankingKind {
    POINTS(null),
    /** Defenders killed in his attacks (02-game-rules §24). */
    ATTACKER(Stat.KILLED_ATTACKING),
    /** Attackers killed where he defended. */
    DEFENDER(Stat.KILLED_DEFENDING),
    /** Resources his attacks took. */
    PLUNDERER(Stat.LOOT_TAKEN);

    private final Stat stat;

    RankingKind(Stat stat) {
        this.stat = stat;
    }

    /** The statistics total behind it, null for the points. */
    public Stat stat() {
        return stat;
    }
}
