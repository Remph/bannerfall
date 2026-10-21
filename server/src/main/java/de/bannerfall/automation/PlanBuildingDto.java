package de.bannerfall.automation;

import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.rules.Requirement;
import java.util.List;

/**
 * {@code GET /api/build-plans/buildings}: what the plan editor needs of a building to write valid
 * lines (04-api <i>Village automation</i>, 1.6).
 *
 * @param startLevel its level in a new village
 * @param requires the levels a line above it must reach
 * @param available false for the Banner Hall in a world without it (its lines are skipped)
 */
public record PlanBuildingDto(
        BuildingType building,
        int startLevel,
        int maxLevel,
        List<Requirement> requires,
        boolean available) {

    static PlanBuildingDto of(BuildingType type, BuildingDef def, boolean available) {
        List<Requirement> requires =
                def.requires().entrySet().stream()
                        .map(entry -> new Requirement(entry.getKey(), entry.getValue()))
                        .toList();
        return new PlanBuildingDto(type, def.startLevel(), def.maxLevel(), requires, available);
    }
}
