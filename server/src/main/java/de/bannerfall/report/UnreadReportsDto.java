package de.bannerfall.report;

/**
 * {@code GET /api/reports/unread}: the number for the counter in the navigation, without loading a
 * page of the list.
 *
 * @param unread the player's unread reports (all pages)
 */
public record UnreadReportsDto(long unread) {}
