package de.bannerfall.automation;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.recruit.RecruitBuildingDto;
import de.bannerfall.recruit.RecruitBuildingDto.RecruitUnitDto;
import de.bannerfall.recruit.RecruitOrderDto;
import de.bannerfall.recruit.RecruitOrderRequest;
import de.bannerfall.recruit.RecruitTemplateLetter;
import de.bannerfall.unit.UnitCounts;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** The rules of the village automation as plain functions (02-game-rules §25). */
class AutomationPlanningTest {

    private static final List<BuildPlanLine> PLAN =
            List.of(
                    BuildPlanLine.build(BuildingType.WOODCUTTER, 3),
                    BuildPlanLine.build(BuildingType.BANNER_HALL, 1),
                    BuildPlanLine.recruit(RecruitTemplateLetter.B),
                    BuildPlanLine.build(BuildingType.WOODCUTTER, 5),
                    BuildPlanLine.build(BuildingType.FARM, 4));

    private static Map<BuildingType, Integer> levels(Object... buildingsAndLevels) {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, 0);
        }
        for (int i = 0; i < buildingsAndLevels.length; i += 2) {
            levels.put((BuildingType) buildingsAndLevels[i], (Integer) buildingsAndLevels[i + 1]);
        }
        return levels;
    }

    @Test
    void theCurrentLineIsTheFirstBuildingLineNotReached() {
        assertThat(AutomationPlanning.currentLine(PLAN, levels(), true)).isZero();
        assertThat(AutomationPlanning.currentLine(PLAN, levels(BuildingType.WOODCUTTER, 3), true))
                .isEqualTo(1);
        // the recruit line is never the current one
        assertThat(
                        AutomationPlanning.currentLine(
                                PLAN,
                                levels(BuildingType.WOODCUTTER, 3, BuildingType.BANNER_HALL, 1),
                                true))
                .isEqualTo(3);
        // a level lost to catapults makes an earlier line the current one again
        assertThat(
                        AutomationPlanning.currentLine(
                                PLAN,
                                levels(
                                        BuildingType.WOODCUTTER,
                                        2,
                                        BuildingType.BANNER_HALL,
                                        1,
                                        BuildingType.FARM,
                                        4),
                                true))
                .isZero();
        assertThat(
                        AutomationPlanning.currentLine(
                                PLAN,
                                levels(
                                        BuildingType.WOODCUTTER,
                                        5,
                                        BuildingType.BANNER_HALL,
                                        1,
                                        BuildingType.FARM,
                                        4),
                                true))
                .isEqualTo(PLAN.size());
    }

    @Test
    void theMinesLineIsCurrentUntilAllThreeMinesReachItAndBuildsTheLowestFirst() {
        List<BuildPlanLine> plan =
                List.of(BuildPlanLine.allMines(3), BuildPlanLine.build(BuildingType.FARM, 2));
        assertThat(AutomationPlanning.currentLine(plan, levels(), true)).isZero();
        assertThat(
                        AutomationPlanning.currentLine(
                                plan,
                                levels(
                                        BuildingType.WOODCUTTER,
                                        3,
                                        BuildingType.CLAY_PIT,
                                        3,
                                        BuildingType.IRON_MINE,
                                        2),
                                true))
                .isZero();
        assertThat(
                        AutomationPlanning.currentLine(
                                plan,
                                levels(
                                        BuildingType.WOODCUTTER,
                                        3,
                                        BuildingType.CLAY_PIT,
                                        4,
                                        BuildingType.IRON_MINE,
                                        3),
                                true))
                .isEqualTo(1);
        assertThat(AutomationPlanning.steps(plan, true))
                .containsExactly(
                        Map.of(
                                BuildingType.WOODCUTTER,
                                3,
                                BuildingType.CLAY_PIT,
                                3,
                                BuildingType.IRON_MINE,
                                3),
                        Map.of(BuildingType.FARM, 2));
    }

    @Test
    void bannerHallLinesAreSkippedWhereTheHallCantBeBuilt() {
        assertThat(AutomationPlanning.currentLine(PLAN, levels(BuildingType.WOODCUTTER, 3), false))
                .isEqualTo(3);
        assertThat(AutomationPlanning.steps(PLAN, false))
                .containsExactly(
                        Map.of(BuildingType.WOODCUTTER, 3),
                        Map.of(BuildingType.WOODCUTTER, 5),
                        Map.of(BuildingType.FARM, 4));
        assertThat(AutomationPlanning.steps(PLAN, true)).hasSize(4);
    }

    @Test
    void recruitingStartsOnceEveryLineAboveTheRecruitLineIsReached() {
        assertThat(AutomationPlanning.recruitLine(PLAN)).isEqualTo(2);
        assertThat(AutomationPlanning.recruiting(PLAN, 1)).isFalse();
        assertThat(AutomationPlanning.recruiting(PLAN, 3)).isTrue();
        assertThat(AutomationPlanning.recruiting(PLAN, PLAN.size())).isTrue();
        List<BuildPlanLine> without = List.of(BuildPlanLine.build(BuildingType.FARM, 2));
        assertThat(AutomationPlanning.recruitLine(without)).isEqualTo(-1);
        assertThat(AutomationPlanning.recruiting(without, 1)).isFalse();
    }

    @Test
    void secondsUntilTheStockPaysUseTheSlowestResource() {
        Map<ResourceType, Double> perHour =
                Map.of(ResourceType.WOOD, 100.0, ResourceType.CLAY, 50.0, ResourceType.IRON, 0.0);
        ResourceAmounts stock = new ResourceAmounts(100, 100, 100);
        assertThat(AutomationPlanning.secondsUntil(new ResourceAmounts(100, 50, 0), stock, perHour))
                .hasValue(0);
        // 100 wood at 100/h = 1 h, 50 clay at 50/h = 1 h, 100 clay at 50/h = 2 h
        assertThat(
                        AutomationPlanning.secondsUntil(
                                new ResourceAmounts(200, 200, 100), stock, perHour))
                .hasValue(7200);
        // rounded up: one wood at 100/h is 36 s
        assertThat(AutomationPlanning.secondsUntil(new ResourceAmounts(101, 0, 0), stock, perHour))
                .hasValue(36);
        assertThat(AutomationPlanning.secondsUntil(new ResourceAmounts(0, 0, 101), stock, perHour))
                .isEqualTo(OptionalLong.empty());
    }

    // ---- the recruit template ----

    private static RecruitUnitDto unit(UnitType type, int wood, int pop, boolean available) {
        return new RecruitUnitDto(
                type, new ResourceAmounts(wood, 0, 0), pop, 100, available, List.of(), 0, 0, 20);
    }

    private static RecruitOrderDto order(BuildingType building, UnitType type) {
        Instant at = Instant.parse("2026-10-20T12:00:00Z");
        return new RecruitOrderDto(1, building, type, 5, 0, 100, at, at.plusSeconds(500));
    }

    private static RecruitBuildingDto barracks(List<RecruitOrderDto> queue) {
        return new RecruitBuildingDto(
                BuildingType.BARRACKS,
                5,
                List.of(
                        unit(UnitType.SPEARMAN, 10, 1, true),
                        unit(UnitType.SWORDSMAN, 10, 1, true),
                        unit(UnitType.AXEMAN, 10, 1, false)),
                queue,
                List.of());
    }

    private static RecruitBuildingDto academy() {
        return new RecruitBuildingDto(
                BuildingType.ACADEMY,
                1,
                List.of(unit(UnitType.NOBLE, 1000, 100, true)),
                List.of(),
                List.of());
    }

    private static UnitCounts counts(Object... unitsAndCounts) {
        UnitCounts counts = new UnitCounts();
        for (int i = 0; i < unitsAndCounts.length; i += 2) {
            counts.set((UnitType) unitsAndCounts[i], (Integer) unitsAndCounts[i + 1]);
        }
        return counts;
    }

    @Test
    void theTemplateRecruitsWhatTheVillageLacksCutByOneShare() {
        UnitCounts goal = counts(UnitType.SPEARMAN, 100, UnitType.SWORDSMAN, 50);
        UnitCounts have = counts(UnitType.SPEARMAN, 60);
        // missing 40 spears and 50 swords = 900 wood; 450 wood pay half of it
        List<RecruitOrderRequest> orders =
                AutomationPlanning.recruitOrders(
                        goal,
                        List.of(barracks(List.of())),
                        have,
                        new ResourceAmounts(450, 0, 0),
                        1000,
                        5,
                        0);
        assertThat(orders)
                .containsExactly(
                        new RecruitOrderRequest(UnitType.SPEARMAN, 20),
                        new RecruitOrderRequest(UnitType.SWORDSMAN, 25));
        // enough of everything: all of it
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of())),
                                have,
                                new ResourceAmounts(5000, 0, 0),
                                1000,
                                5,
                                0))
                .containsExactly(
                        new RecruitOrderRequest(UnitType.SPEARMAN, 40),
                        new RecruitOrderRequest(UnitType.SWORDSMAN, 50));
        // the population is short too
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of())),
                                have,
                                new ResourceAmounts(5000, 0, 0),
                                9,
                                5,
                                0))
                .containsExactly(
                        new RecruitOrderRequest(UnitType.SPEARMAN, 4),
                        new RecruitOrderRequest(UnitType.SWORDSMAN, 5));
    }

    @Test
    void aReachedGoalRecruitsNothingAndLossesAreRecruitedAgain() {
        UnitCounts goal = counts(UnitType.SPEARMAN, 100);
        ResourceAmounts stock = new ResourceAmounts(5000, 0, 0);
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of())),
                                counts(UnitType.SPEARMAN, 100),
                                stock,
                                1000,
                                5,
                                0))
                .isEmpty();
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of())),
                                counts(UnitType.SPEARMAN, 70),
                                stock,
                                1000,
                                5,
                                0))
                .containsExactly(new RecruitOrderRequest(UnitType.SPEARMAN, 30));
    }

    @Test
    void noblesLockedUnitsAndBusyQueuesAreSkipped() {
        UnitCounts goal = counts(UnitType.SPEARMAN, 10, UnitType.AXEMAN, 10, UnitType.NOBLE, 2);
        ResourceAmounts stock = new ResourceAmounts(100_000, 0, 0);
        // the axeman is locked, the noble stays by hand
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of()), academy()),
                                new UnitCounts(),
                                stock,
                                1000,
                                5,
                                0))
                .containsExactly(new RecruitOrderRequest(UnitType.SPEARMAN, 10));
        // one order in the barracks: still room; two: busy
        RecruitOrderDto running = order(BuildingType.BARRACKS, UnitType.SWORDSMAN);
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of(running))),
                                new UnitCounts(),
                                stock,
                                1000,
                                5,
                                0))
                .hasSize(1);
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of(running, running))),
                                new UnitCounts(),
                                stock,
                                1000,
                                5,
                                0))
                .isEmpty();
    }

    @Test
    void eachUnitTakesAPlaceInItsQueue() {
        UnitCounts goal = counts(UnitType.SPEARMAN, 10, UnitType.SWORDSMAN, 10);
        RecruitOrderDto running = order(BuildingType.BARRACKS, UnitType.SPEARMAN);
        // a queue of 2 with one order: room for one more unit type
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal,
                                List.of(barracks(List.of(running))),
                                new UnitCounts(),
                                new ResourceAmounts(100_000, 0, 0),
                                1000,
                                2,
                                0))
                .containsExactly(new RecruitOrderRequest(UnitType.SPEARMAN, 10));
    }

    @Test
    void thePaladinIsCappedAtTheFreeSlots() {
        RecruitBuildingDto hall =
                new RecruitBuildingDto(
                        BuildingType.BANNER_HALL,
                        1,
                        List.of(unit(UnitType.PALADIN, 10, 10, true)),
                        List.of(),
                        List.of());
        UnitCounts goal = counts(UnitType.PALADIN, 3);
        ResourceAmounts stock = new ResourceAmounts(100_000, 0, 0);
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal, List.of(hall), new UnitCounts(), stock, 1000, 5, 1))
                .containsExactly(new RecruitOrderRequest(UnitType.PALADIN, 1));
        assertThat(
                        AutomationPlanning.recruitOrders(
                                goal, List.of(hall), new UnitCounts(), stock, 1000, 5, 0))
                .isEmpty();
    }
}
