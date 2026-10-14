package de.bannerfall.recruit;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.Requirement;
import java.util.List;

/**
 * One recruiting building in {@code GET /api/villages/{id}/recruitment} (04-api): its units and its
 * queue. The village's stock and units at home come with the {@code VillageDto}.
 *
 * @param level the current level (0 = not built yet)
 * @param units the units this building recruits, in unit order
 * @param queue the building's recruit orders in running order (first = running)
 * @param dismissQueue the building's dismiss orders in running order (since the dismiss feature,
 *     02-game-rules §5.2a)
 */
public record RecruitBuildingDto(
        BuildingType building,
        int level,
        List<RecruitUnitDto> units,
        List<RecruitOrderDto> queue,
        List<DismissOrderDto> dismissQueue) {

    /**
     * @param cost per unit
     * @param pop per unit
     * @param secondsPerUnit with the building level and world speed of right now; for a building
     *     that isn't built yet the time at level 1
     * @param available whether the requirements are met (current levels, queued ones don't count)
     * @param missing the requirements that are not met yet, in building order
     * @param maxAffordable how many units the stock and the free population pay for right now (at
     *     most {@code RecruitRules.MAX_COUNT}); requirements and the queue are not looked at
     * @param atHome how many of this unit stand in the home stack (what can be dismissed)
     * @param dismissSecondsPerUnit seconds per unit when dismissing: a fifth of {@code
     *     secondsPerUnit}
     */
    public record RecruitUnitDto(
            UnitType unit,
            ResourceAmounts cost,
            int pop,
            long secondsPerUnit,
            boolean available,
            List<Requirement> missing,
            int maxAffordable,
            int atHome,
            long dismissSecondsPerUnit) {}
}
