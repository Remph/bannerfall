package de.bannerfall.player;

/** One row of {@link PlayerRepository#findRankingPage} (a projection of the native query). */
public interface RankingRow {
    long getRank();

    long getPlayerId();

    String getName();

    boolean getNpc();

    int getPoints();

    int getVillageCount();

    /** What the ranking sorts by: the points, or the statistics total of its tab. */
    long getValue();

    Long getGuildId();

    String getGuildTag();
}
