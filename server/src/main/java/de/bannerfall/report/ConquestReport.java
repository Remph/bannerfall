package de.bannerfall.report;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A village changed owner (kinds CONQUEST for the new owner, VILLAGE_LOST for the previous one;
 * 02-game-rules §10). The battle itself is in the ATTACK and DEFENSE reports of the same moment.
 *
 * @param village the conquered village as it was before (snapshot: the previous owner, its name; no
 *     owner and no name for a barbarian village)
 * @param from the village the nobles came from (snapshot)
 * @param newName the village's name from now on (a barbarian village gets one)
 * @param banner the banner that flew in the village (M32b, 02-game-rules §21.6.1); null if none
 *     (and in reports from before M32b)
 */
public record ConquestReport(
        VillageRefDto village,
        VillageRefDto from,
        String newName,
        @Schema(nullable = true) ConqueredBanner banner)
        implements ReportPayload {

    /** A conquest of a village without a banner. */
    public ConquestReport(VillageRefDto village, VillageRefDto from, String newName) {
        this(village, from, newName, null);
    }

    /**
     * The banner of a conquered village.
     *
     * @param toStash true: it went into the conqueror's stash; false: it stays in the village,
     *     asleep (an NPC lord conquered it, or banners are switched off)
     */
    public record ConqueredBanner(BannerType type, int tier, boolean toStash) {}

    @Override
    public ReportSummary summary() {
        return new ReportSummary(from, village, null);
    }
}
