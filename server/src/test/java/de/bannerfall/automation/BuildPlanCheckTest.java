package de.bannerfall.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.common.ApiException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.recruit.RecruitTemplateLetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a build plan may hold (02-game-rules §25). */
class BuildPlanCheckTest {

    private static final GameData DATA = TestGameData.load();

    private static List<BuildPlanLine> checked(BuildPlanLine... lines) {
        return BuildPlanService.checked(List.of(lines), DATA);
    }

    private static void refused(BuildPlanLine... lines) {
        assertThatThrownBy(() -> checked(lines)).isInstanceOf(ApiException.class);
    }

    @Test
    void aValidPlanIsStoredAsGiven() {
        List<BuildPlanLine> lines =
                checked(
                        BuildPlanLine.build(BuildingType.WOODCUTTER, 5),
                        new BuildPlanLine(null, 7, RecruitTemplateLetter.A, false),
                        BuildPlanLine.build(BuildingType.HEADQUARTERS, 3),
                        BuildPlanLine.build(BuildingType.BARRACKS, 1));
        // the recruit line's level is stored as 0
        assertThat(lines)
                .containsExactly(
                        BuildPlanLine.build(BuildingType.WOODCUTTER, 5),
                        BuildPlanLine.recruit(RecruitTemplateLetter.A),
                        BuildPlanLine.build(BuildingType.HEADQUARTERS, 3),
                        BuildPlanLine.build(BuildingType.BARRACKS, 1));
    }

    @Test
    void requirementsMustBeMetByTheLinesAbove() {
        // the barracks needs Town Hall 3; a new village has Town Hall 1
        refused(BuildPlanLine.build(BuildingType.BARRACKS, 1));
        refused(
                BuildPlanLine.build(BuildingType.BARRACKS, 1),
                BuildPlanLine.build(BuildingType.HEADQUARTERS, 3));
        // a later line with a lower level doesn't take the higher one back
        checked(
                BuildPlanLine.build(BuildingType.HEADQUARTERS, 3),
                BuildPlanLine.build(BuildingType.HEADQUARTERS, 2),
                BuildPlanLine.build(BuildingType.BARRACKS, 1));
    }

    @Test
    void linesOutsideTheLimitsAreRefused() {
        refused();
        refused(BuildPlanLine.build(BuildingType.WOODCUTTER, 0));
        refused(BuildPlanLine.build(BuildingType.WOODCUTTER, 31));
        refused(BuildPlanLine.build(BuildingType.RALLY_POINT, 2));
        refused(new BuildPlanLine(null, 3, null, false));
        refused(new BuildPlanLine(BuildingType.FARM, 3, RecruitTemplateLetter.A, false));
        refused(
                BuildPlanLine.recruit(RecruitTemplateLetter.A),
                BuildPlanLine.recruit(RecruitTemplateLetter.B));
        List<BuildPlanLine> many =
                new ArrayList<>(
                        Collections.nCopies(
                                BuildPlanService.MAX_LINES,
                                BuildPlanLine.build(BuildingType.FARM, 2)));
        BuildPlanService.checked(many, DATA);
        many.add(BuildPlanLine.build(BuildingType.FARM, 2));
        assertThatThrownBy(() -> BuildPlanService.checked(many, DATA))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void theMinesLineCoversThreeBuildingsAndIsStoredAsGiven() {
        List<BuildPlanLine> lines =
                checked(BuildPlanLine.allMines(5), BuildPlanLine.build(BuildingType.WOODCUTTER, 6));
        assertThat(lines)
                .containsExactly(
                        BuildPlanLine.allMines(5), BuildPlanLine.build(BuildingType.WOODCUTTER, 6));
        assertThat(lines.getFirst().targets())
                .containsOnlyKeys(
                        BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE)
                .containsValues(5);
        refused(BuildPlanLine.allMines(0));
        refused(BuildPlanLine.allMines(31));
        refused(new BuildPlanLine(BuildingType.FARM, 3, null, true));
        refused(new BuildPlanLine(null, 0, RecruitTemplateLetter.A, true));
    }

    @Test
    void plansSavedBeforeTheMinesLineReadAsLinesWithoutMines() {
        BuildPlanLinesConverter converter = new BuildPlanLinesConverter();
        List<BuildPlanLine> old =
                converter.convertToEntityAttribute(
                        "[{\"building\":\"FARM\",\"level\":6,\"recruit\":null},"
                                + "{\"building\":null,\"level\":0,\"recruit\":\"B\"}]");
        assertThat(old)
                .containsExactly(
                        BuildPlanLine.build(BuildingType.FARM, 6),
                        BuildPlanLine.recruit(RecruitTemplateLetter.B));
        List<BuildPlanLine> withMines = List.of(BuildPlanLine.allMines(4));
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(withMines)))
                .isEqualTo(withMines);
    }
}
