package de.bannerfall.report;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

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
 * @param loot battles with a main battle (1.3): what the attackers took, from the attacker's or
 *     from the defenders' side; null for other reports and scouting
 * @param losses battles (1.3): the reader's side's dead units, the attacker's in ATTACK and SCOUT,
 *     all defenders' in DEFENSE (empty if the attacker saw nothing); empty for other reports
 * @param fullHaul ATTACK only (1.3): the survivors carried all they could, so there was more to
 *     take; false in reports from before 1.3
 * @param nobles battles (1.4.1): the attack had nobles in it, in every copy (the list marks the
 *     line in gold); false for other reports
 */
public record ReportSummary(
        VillageRefDto from,
        VillageRefDto to,
        @Schema(nullable = true) BattleOutcome outcome,
        List<ScoutedBanner> bannersGained,
        @Schema(nullable = true) ResourceAmounts loot,
        Map<UnitType, Integer> losses,
        boolean fullHaul,
        boolean nobles) {

    /** A report that isn't a battle: no loot, no losses. */
    public ReportSummary(
            VillageRefDto from,
            VillageRefDto to,
            BattleOutcome outcome,
            List<ScoutedBanner> bannersGained) {
        this(from, to, outcome, bannersGained, null, Map.of(), false, false);
    }

    public ReportSummary {
        bannersGained = bannersGained == null ? List.of() : List.copyOf(bannersGained);
        losses = ReportMaps.units(losses);
    }
}
