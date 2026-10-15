package de.bannerfall.report;

import java.util.List;

/**
 * One page of a player's reports, newest first ({@code GET /api/reports}).
 *
 * @param page 0-based, as requested
 * @param size the page size, as requested
 * @param total all reports of the player
 * @param unread the player's unread reports (all pages)
 * @param newestId the id of the player's newest report (all pages), 0 without reports: "Delete all"
 *     sends it back, so a report that arrives while the player confirms isn't deleted unseen
 * @param barbarian how many reports "Delete barbarian reports" would delete with {@code newestId}
 *     (1.2.3): the player's own attack and scout reports on barbarian villages without a new banner
 */
public record ReportPageDto(
        List<ReportItemDto> items,
        int page,
        int size,
        long total,
        long unread,
        long newestId,
        long barbarian) {}
