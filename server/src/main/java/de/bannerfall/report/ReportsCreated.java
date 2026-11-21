package de.bannerfall.report;

import java.time.Instant;
import java.util.Set;

/**
 * Published by {@link ReportService#create} inside the transaction that created the reports, for
 * listeners that keep something in step with them (the first contacts of NPC lords, 09-npc-lords
 * §16.7). Runs before the commit: a listener's write commits or rolls back with the reports.
 *
 * @param recipients the players who got a copy
 */
public record ReportsCreated(
        ReportKind kind, Instant happenedAt, ReportPayload payload, Set<Long> recipients) {}
