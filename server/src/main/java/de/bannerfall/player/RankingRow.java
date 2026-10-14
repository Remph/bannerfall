package de.bannerfall.player;

/** One row of {@link PlayerRepository#findRankingPage} (a projection of the native query). */
public interface RankingRow {
    long getRank();

    long getPlayerId();

    String getName();

    boolean getNpc();

    int getPoints();

    int getVillageCount();

    Long getGuildId();

    String getGuildTag();
}
