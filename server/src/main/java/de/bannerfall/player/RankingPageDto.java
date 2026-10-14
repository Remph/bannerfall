package de.bannerfall.player;

import java.util.List;

/**
 * A page of the ranking.
 *
 * @param page 0-based
 * @param total how many players the ranking lists in all
 */
public record RankingPageDto(int page, int size, long total, List<RankingEntryDto> entries) {}
