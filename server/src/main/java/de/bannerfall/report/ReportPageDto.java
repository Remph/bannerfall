package de.bannerfall.report;

import java.util.List;
import java.util.Map;

/**
 * One page of a player's reports, newest first ({@code GET /api/reports}).
 *
 * @param page 0-based, as requested
 * @param size the page size, as requested
 * @param total the player's reports in this list: all of them, or those of the filter
 * @param unread the player's unread reports (all pages)
 * @param newestId the id of the player's newest report (all pages), 0 without reports: "Delete all"
 *     sends it back, so a report that arrives while the player confirms isn't deleted unseen
 * @param barbarian how many reports "Delete barbarian reports" would delete with {@code newestId}
 *     (1.2.3): the player's own attack and scout reports on barbarian villages without a new banner
 * @param counts the player's reports per filter (1.3), every filter listed, 0 included; their sum
 *     is all his reports
 */
public record ReportPageDto(
        List<ReportItemDto> items,
        int page,
        int size,
        long total,
        long unread,
        long newestId,
        long barbarian,
        Map<ReportFilter, Long> counts) {}
