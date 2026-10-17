package de.bannerfall.report;

import java.util.List;

/** The players the caller last sent a report to by name, newest first (1.3.1). */
public record RecentRecipientsDto(List<String> names) {}
