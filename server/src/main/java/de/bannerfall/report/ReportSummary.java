package de.bannerfall.report;

import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What the report list shows of a report (04-api <i>Reports</i>), so the list doesn't carry the
 * full payloads.
 *
 * @param from the attacking village, the supporting village
 * @param to the attacked village, the supported village
 * @param outcome battles only, null otherwise
 * @param bannersGained the banners the recipient gained in it (02-game-rules §21.6): found in the
 *     battle, stolen by his thieves, or taken into his stash with a conquered village; empty
 *     otherwise. Worked out from the stored payload, so reports from before this field show it too
 */
public record ReportSummary(
        VillageRefDto from,
        VillageRefDto to,
        @Schema(nullable = true) BattleOutcome outcome,
        List<ScoutedBanner> bannersGained) {

    public ReportSummary {
        bannersGained = bannersGained == null ? List.of() : List.copyOf(bannersGained);
    }
}
