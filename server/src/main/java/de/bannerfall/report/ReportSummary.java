package de.bannerfall.report;

import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the report list shows of a report (04-api <i>Reports</i>), so the list doesn't carry the
 * full payloads.
 *
 * @param from the attacking village, the supporting village
 * @param to the attacked village, the supported village
 * @param outcome battles only, null otherwise
 */
public record ReportSummary(
        VillageRefDto from, VillageRefDto to, @Schema(nullable = true) BattleOutcome outcome) {}
