package de.bannerfall.automation;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.recruit.RecruitTemplateLetter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One line of a build plan (02-game-rules §25): a building and the level it should reach, the mines
 * line (1.6.2: the woodcutter, clay pit and iron mine together to one level), or the recruit line,
 * from which on the village keeps the recruit template {@code recruit} filled.
 *
 * @param building null on the mines line and the recruit line
 * @param level 1 … the building's max level (the lowest of the three mines' on the mines line); 0
 *     on the recruit line
 * @param recruit null on a building line and the mines line
 * @param mines true on the mines line; missing (plans saved before 1.6.2, old clients) reads as
 *     false
 */
public record BuildPlanLine(
        @Schema(nullable = true) BuildingType building,
        int level,
        @Schema(nullable = true) RecruitTemplateLetter recruit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean mines) {

    /** A request without the field (a client from before 1.6.2) reads as a line without mines. */
    public BuildPlanLine {
        mines = Boolean.TRUE.equals(mines);
    }

    /** The three mines the mines line covers. */
    public static final List<BuildingType> MINES =
            List.of(BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE);

    public static BuildPlanLine build(BuildingType building, int level) {
        return new BuildPlanLine(building, level, null, false);
    }

    public static BuildPlanLine recruit(RecruitTemplateLetter letter) {
        return new BuildPlanLine(null, 0, letter, false);
    }

    /** All three mines to {@code level}. */
    public static BuildPlanLine allMines(int level) {
        return new BuildPlanLine(null, level, null, true);
    }

    /**
     * The buildings a line builds, each with the level it should reach: one for a building line,
     * the three mines for the mines line, none for the recruit line (not a getter, so it stays out
     * of the JSON).
     */
    public Map<BuildingType, Integer> targets() {
        Map<BuildingType, Integer> result = new EnumMap<>(BuildingType.class);
        if (mines) {
            MINES.forEach(mine -> result.put(mine, level));
        } else if (building != null) {
            result.put(building, level);
        }
        return result;
    }

    /** The recruit line (not a getter, so it stays out of the JSON). */
    public boolean startsRecruiting() {
        return recruit != null;
    }
}
