package de.bannerfall.automation;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.recruit.RecruitTemplateLetter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One line of a build plan (02-game-rules §25): either a building and the level it should reach, or
 * the recruit line, from which on the village keeps the recruit template {@code recruit} filled.
 *
 * @param building null on the recruit line
 * @param level 1 … the building's max level; 0 on the recruit line
 * @param recruit null on a building line
 */
public record BuildPlanLine(
        @Schema(nullable = true) BuildingType building,
        int level,
        @Schema(nullable = true) RecruitTemplateLetter recruit) {

    public static BuildPlanLine build(BuildingType building, int level) {
        return new BuildPlanLine(building, level, null);
    }

    public static BuildPlanLine recruit(RecruitTemplateLetter letter) {
        return new BuildPlanLine(null, 0, letter);
    }

    /** The recruit line (not a getter, so it stays out of the JSON). */
    public boolean startsRecruiting() {
        return recruit != null;
    }
}
