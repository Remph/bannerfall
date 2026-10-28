package de.bannerfall.tutorial;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.world.WorldSettings;
import org.junit.jupiter.api.Test;

/** The rules of the tutorial's steps (02-game-rules §27), without a database. */
class TutorialRulesTest {

    private static final GameData GAME_DATA = TestGameData.load();
    private static final WorldSettings WORLD = WorldSettings.defaults();

    private static TutorialFacts facts(
            int woodcutter, int headquarters, int barracks, int market, int warehouse) {
        return new TutorialFacts(
                woodcutter,
                headquarters,
                barracks,
                market,
                warehouse,
                0,
                0,
                0,
                1,
                0,
                false,
                false,
                0,
                false);
    }

    private static TutorialFacts none() {
        return facts(1, 1, 0, 0, 1);
    }

    @Test
    void stepsAreDoneWhenTheGameShowsWhatTheyAskFor() {
        assertThat(TutorialRules.done(TutorialStep.WOODCUTTER, none())).isFalse();
        assertThat(TutorialRules.done(TutorialStep.WOODCUTTER, facts(2, 1, 0, 0, 1))).isTrue();
        assertThat(TutorialRules.done(TutorialStep.HEADQUARTERS, facts(1, 2, 0, 0, 1))).isFalse();
        assertThat(TutorialRules.done(TutorialStep.HEADQUARTERS, facts(1, 3, 0, 0, 1))).isTrue();
        assertThat(TutorialRules.done(TutorialStep.BARRACKS, facts(1, 3, 1, 0, 1))).isTrue();
        assertThat(TutorialRules.done(TutorialStep.MARKET, facts(1, 3, 0, 1, 2))).isTrue();
    }

    @Test
    void fiveSpearmenAreNeededNotFour() {
        assertThat(TutorialRules.done(TutorialStep.SPEARMEN, withSpearmen(4))).isFalse();
        assertThat(TutorialRules.done(TutorialStep.SPEARMEN, withSpearmen(5))).isTrue();
    }

    private static TutorialFacts withSpearmen(int spearmen) {
        return new TutorialFacts(1, 3, 1, 0, 1, 0, 0, 0, 1, spearmen, false, false, 0, false);
    }

    @Test
    void attackReportFarmListAndGuildComeFromTheirFacts() {
        TutorialFacts f = new TutorialFacts(1, 1, 0, 0, 1, 0, 0, 0, 1, 0, true, true, 2, true);
        assertThat(TutorialRules.done(TutorialStep.ATTACK, f)).isTrue();
        assertThat(TutorialRules.done(TutorialStep.REPORT, f)).isTrue();
        assertThat(TutorialRules.done(TutorialStep.FARM_LIST, f)).isTrue();
        assertThat(TutorialRules.done(TutorialStep.GUILD, f)).isTrue();
        assertThat(TutorialRules.done(TutorialStep.ATTACK, none())).isFalse();
    }

    @Test
    void stepsThatTheClientTicksAndLessonsAreNeverDoneByTheState() {
        TutorialFacts all = new TutorialFacts(9, 9, 9, 9, 9, 9, 9, 9, 9, 99, true, true, 9, true);
        assertThat(TutorialRules.done(TutorialStep.MAP_BARBARIAN, all)).isFalse();
        assertThat(TutorialRules.done(TutorialStep.SIMULATOR, all)).isFalse();
        assertThat(TutorialRules.done(TutorialStep.LESSON_CONQUEST, all)).isFalse();
    }

    @Test
    void aStepWaitsForTheBuildingItNeeds() {
        assertThat(TutorialRules.available(TutorialStep.BARRACKS, facts(1, 2, 0, 0, 1), WORLD))
                .isFalse();
        assertThat(TutorialRules.available(TutorialStep.BARRACKS, facts(1, 3, 0, 0, 1), WORLD))
                .isTrue();
        assertThat(TutorialRules.available(TutorialStep.SPEARMEN, facts(1, 3, 0, 0, 1), WORLD))
                .isFalse();
        assertThat(TutorialRules.available(TutorialStep.SPEARMEN, facts(1, 3, 1, 0, 1), WORLD))
                .isTrue();
        assertThat(TutorialRules.available(TutorialStep.MARKET, facts(1, 3, 0, 0, 1), WORLD))
                .isFalse();
        assertThat(TutorialRules.available(TutorialStep.MARKET, facts(1, 3, 0, 0, 2), WORLD))
                .isTrue();
    }

    @Test
    void lessonsOpenWithTheirThing() {
        WorldSettings world =
                WORLD.toBuilder().unitLevelsEnabled(true).bannersEnabled(true).build();
        TutorialFacts f = new TutorialFacts(1, 1, 0, 0, 1, 1, 1, 1, 3, 0, false, false, 0, false);
        assertThat(TutorialRules.available(TutorialStep.LESSON_UNIT_LEVELS, f, world)).isTrue();
        assertThat(TutorialRules.available(TutorialStep.LESSON_CONQUEST, f, world)).isTrue();
        assertThat(TutorialRules.available(TutorialStep.LESSON_BANNER_HALL, f, world)).isTrue();
        assertThat(TutorialRules.available(TutorialStep.LESSON_AUTOMATION, f, world)).isTrue();
        assertThat(TutorialRules.available(TutorialStep.LESSON_CONQUEST, none(), world)).isFalse();
        // the automation lesson waits for the village that unlocks it (3 by default)
        TutorialFacts two = new TutorialFacts(1, 1, 0, 0, 1, 0, 0, 0, 2, 0, false, false, 0, false);
        assertThat(TutorialRules.available(TutorialStep.LESSON_AUTOMATION, two, world)).isFalse();
    }

    @Test
    void aWorldWithoutAFeatureHasNoStepForIt() {
        WorldSettings off =
                WORLD.toBuilder()
                        .guildsEnabled(false)
                        .unitLevelsEnabled(false)
                        .bannersEnabled(false)
                        .automationVillages(0)
                        .build();
        assertThat(TutorialRules.inWorld(TutorialStep.GUILD, off)).isFalse();
        assertThat(TutorialRules.inWorld(TutorialStep.LESSON_UNIT_LEVELS, off)).isFalse();
        assertThat(TutorialRules.inWorld(TutorialStep.LESSON_BANNER_HALL, off)).isFalse();
        assertThat(TutorialRules.inWorld(TutorialStep.LESSON_AUTOMATION, off)).isFalse();
        assertThat(TutorialRules.inWorld(TutorialStep.LESSON_CONQUEST, off)).isTrue();
        assertThat(TutorialRules.inWorld(TutorialStep.WOODCUTTER, off)).isTrue();
    }

    @Test
    void onlyFourStepsRefundAndTheRefundIsThePriceOfWhatTheyAskFor() {
        assertThat(TutorialRules.refund(TutorialStep.WOODCUTTER, GAME_DATA))
                .isEqualTo(new ResourceAmounts(63, 75, 50));
        assertThat(TutorialRules.refund(TutorialStep.BARRACKS, GAME_DATA))
                .isEqualTo(new ResourceAmounts(180, 180, 100));
        assertThat(TutorialRules.refund(TutorialStep.MARKET, GAME_DATA))
                .isEqualTo(new ResourceAmounts(100, 100, 100));
        ResourceAmounts spearman = GAME_DATA.unit(de.bannerfall.gamedata.UnitType.SPEARMAN).cost();
        assertThat(TutorialRules.refund(TutorialStep.SPEARMEN, GAME_DATA))
                .isEqualTo(spearman.times(5));
        for (TutorialStep step : TutorialStep.values()) {
            boolean refunds =
                    step == TutorialStep.WOODCUTTER
                            || step == TutorialStep.BARRACKS
                            || step == TutorialStep.SPEARMEN
                            || step == TutorialStep.MARKET;
            assertThat(TutorialRules.refund(step, GAME_DATA).total() > 0)
                    .as(step.name())
                    .isEqualTo(refunds);
        }
    }

    @Test
    void theChainHasNoLoopAndTheKindsMatchTheNames() {
        for (TutorialStep step : TutorialStep.values()) {
            TutorialStep before = step.prerequisite();
            if (before != null) {
                assertThat(before.ordinal()).isLessThan(step.ordinal());
                assertThat(before.kind()).isEqualTo(TutorialStep.Kind.STEP);
            }
            assertThat(step.kind() == TutorialStep.Kind.LESSON)
                    .isEqualTo(step.name().startsWith("LESSON_"));
            if (step.kind() == TutorialStep.Kind.LESSON) {
                assertThat(step.detection()).isEqualTo(TutorialStep.Detection.ACK);
            }
        }
    }
}
