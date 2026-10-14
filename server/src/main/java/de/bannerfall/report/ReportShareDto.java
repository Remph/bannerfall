package de.bannerfall.report;

import java.util.List;

/**
 * The answer of {@code POST /api/reports/{id}/share} (M40).
 *
 * @param sentTo the recipients' names as they are spelled, each once
 */
public record ReportShareDto(List<String> sentTo) {}
