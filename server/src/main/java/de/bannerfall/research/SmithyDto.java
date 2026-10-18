package de.bannerfall.research;

import de.bannerfall.gamedata.AttackType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitStats;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.Requirement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /api/villages/{id}/smithy} (04-api): every unit with its stats and, if the world has
 * unit levels, its level and the next research. The village's stock comes with the {@code
 * VillageDto}.
 *
 * @param smithyLevel the village's smithy level (0 = not built)
 * @param levelsEnabled the world setting; off: the stats are the base stats and nothing can be
 *     researched
 * @param units all units in unit order
 * @param running the research that runs in this village (its smithy does one at a time); null if
 *     none
 */
public record SmithyDto(
        int smithyLevel,
        boolean levelsEnabled,
        List<SmithyUnitDto> units,
        @Schema(nullable = true) ResearchOrderDto running) {

    /**
     * @param trainedIn where the unit's levels are researched: {@code SMITHY}, or {@code
     *     BANNER_HALL} for the paladin and the banner thief (Hall training, 1.4; their levels count
     *     with unit levels off too, and the smithy page offers no research for them)
     * @param attackType decides which defence value of the defender counts against this unit
     * @param minutesPerField how long the unit takes for one field in this world: {@code minutes
     *     per field ÷ (speed × unitSpeed)}, not rounded
     * @param carry loot capacity per unit
     * @param level the player's level of the unit now (always 1 for a Smithy unit with levels off);
     *     a unit without research steps has {@code maxLevel} 1
     * @param stats the stats of every level, {@code stats[0]} is level 1; one entry for a unit
     *     without levels
     * @param researching the research of this unit that is running anywhere (maybe in another
     *     village of the player); null if none
     * @param next the next level's research; null with levels off, without levels, at the top
     *     level, while this unit is being researched, and on the page of the building that does not
     *     research it
     */
    public record SmithyUnitDto(
            UnitType unit,
            BuildingType trainedIn,
            AttackType attackType,
            double minutesPerField,
            int carry,
            int level,
            int maxLevel,
            List<UnitStats> stats,
            @Schema(nullable = true) ResearchOrderDto researching,
            @Schema(nullable = true) NextResearchDto next) {}

    /**
     * @param level the level the research reaches
     * @param durationSeconds with the level of the researching building and the world speed of
     *     right now
     * @param requires everything the village needs: the unit's own requirements and the level's
     * @param missing what of that is not met yet, in building order
     * @param affordable the village's stock covers the cost
     * @param villageBusy the village's researching building (smithy or hall) runs another research
     *     (the unit itself is not being researched, else {@code next} would be null)
     */
    public record NextResearchDto(
            int level,
            ResourceAmounts cost,
            long durationSeconds,
            List<Requirement> requires,
            List<Requirement> missing,
            boolean affordable,
            boolean villageBusy) {}
}
