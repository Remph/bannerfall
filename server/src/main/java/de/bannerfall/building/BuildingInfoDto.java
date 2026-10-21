package de.bannerfall.building;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildingEffect;
import de.bannerfall.rules.Requirement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One building in {@code GET /api/villages/{id}/buildings} (04-api): everything the Town Hall
 * screen needs except the village's stock, which comes with the {@code VillageDto}.
 *
 * @param level the current level
 * @param queuedLevel the level once all queued orders are done
 * @param next the next order that could be queued (for level {@code queuedLevel + 1}); null at the
 *     maximum level
 * @param requirementsMet whether the requirements of {@code next} are met, queued levels counted
 * @param missing the requirements that are not met yet, in building order
 * @param demolish the next demolition (1.6, for level {@code queuedLevel − 1}); null at the lowest
 *     level the building can be demolished to
 */
public record BuildingInfoDto(
        BuildingType building,
        int level,
        int queuedLevel,
        int maxLevel,
        @Schema(nullable = true) NextLevelDto next,
        boolean requirementsMet,
        List<Requirement> missing,
        @Schema(nullable = true) DemolishDto demolish) {

    /**
     * @param cost the price at the next free position of the queue, a paid slot's surcharge
     *     included (M13c)
     * @param surchargePercent that slot's surcharge (0 within {@code buildQueueSize}, else 25, 50,
     *     100, …)
     * @param pop the extra population the upgrade needs
     * @param durationSeconds with the Town Hall level and world speed of right now
     * @param effects what the upgrade changes, before and after (M24, the Town Hall preview); empty
     *     for buildings that only unlock things
     * @param unlocksBuildings the buildings whose requirements this upgrade completes (queued
     *     levels counted), in building order
     * @param unlocksUnits the units that this upgrade makes recruitable, in unit order
     */
    public record NextLevelDto(
            int level,
            ResourceAmounts cost,
            int surchargePercent,
            int pop,
            long durationSeconds,
            List<BuildingEffect> effects,
            List<BuildingType> unlocksBuildings,
            List<UnitType> unlocksUnits) {}

    /**
     * The next demolition of a building (1.6, 02-game-rules §4.5). It costs nothing and gives
     * nothing back.
     *
     * @param level the level the building has after it
     * @param durationSeconds a fifth of the build time of the level it removes, with the Town Hall
     *     level and world speed of right now
     * @param pop the population it frees once it is done
     * @param points the village points it costs once it is done
     * @param locksBuildings the built buildings whose requirements are no longer met after it (they
     *     keep their level but can't be upgraded), in building order
     * @param locksUnits the units that can no longer be recruited after it, in unit order
     * @param blocker why it can't be queued right now, or null: {@code BUILD_ORDER_CONFLICT} (an
     *     upgrade of this building is queued) or {@code NOT_ENOUGH_POPULATION} (the smaller farm
     *     can't feed the population). A full queue is not named here, the queue says so.
     */
    public record DemolishDto(
            int level,
            long durationSeconds,
            int pop,
            int points,
            List<BuildingType> locksBuildings,
            List<UnitType> locksUnits,
            @Schema(nullable = true) ErrorCode blocker) {}
}
