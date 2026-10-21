package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcLearning.Lesson;
import de.bannerfall.npc.NpcLearning.Win;
import de.bannerfall.npc.NpcLearning.Wins;
import de.bannerfall.npc.NpcMemory.Lessons;
import de.bannerfall.npc.NpcMemory.Tally;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.village.VillageRefDto;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Learning lords, the pure part (09-npc-lords §15). */
class NpcLearningTest {

    private static final GameData DATA = TestGameData.load();
    private static final NpcBalanceSettings.Learning LEARNING =
            new NpcBalanceSettings.Learning(0.35, 24, 5, 72, 0.15, 3, 48);
    private static final Instant AT = Instant.parse("2026-10-20T10:00:00Z");
    private static final long LORD = 10;
    private static final long HUMAN = 20;
    private static final VillageRefDto LORD_VILLAGE = new VillageRefDto(1, "L", 0, 0, LORD, "L");
    private static final VillageRefDto HUMAN_VILLAGE = new VillageRefDto(2, "H", 3, 0, HUMAN, "H");

    /** The OFFENSIVE MID mix (offensive.yml). */
    private static final Map<UnitType, Integer> OFFENSIVE_MID =
            Map.of(
                    UnitType.SPEARMAN, 10,
                    UnitType.AXEMAN, 45,
                    UnitType.SCOUT, 5,
                    UnitType.LIGHT_CAVALRY, 35,
                    UnitType.RAM, 5);

    private static final Set<UnitType> OFFENSIVE_ATTACK =
            Set.of(
                    UnitType.AXEMAN,
                    UnitType.LIGHT_CAVALRY,
                    UnitType.HEAVY_CAVALRY,
                    UnitType.RAM,
                    UnitType.CATAPULT);

    @Test
    void anAttackCountsItsPowerByTypeAndScoutsOnlyCountAsAScouting() {
        Tally attack =
                NpcLearning.tallyOf(
                        Map.of(
                                UnitType.LIGHT_CAVALRY, 10,
                                UnitType.AXEMAN, 20,
                                UnitType.RAM, 2,
                                UnitType.SCOUT, 5),
                        AT,
                        DATA);
        assertThat(attack.cavalry()).isEqualTo(10 * 120);
        assertThat(attack.infantry()).isEqualTo(20 * 40); // scouts attack with 0
        assertThat(attack.siege()).isEqualTo(2 * 5); // rams by population
        assertThat(attack.attacks()).isEqualTo(1);
        assertThat(attack.scoutings()).isZero();

        Tally scouting = NpcLearning.tallyOf(Map.of(UnitType.SCOUT, 50), AT, DATA);
        assertThat(scouting.attacks()).isZero();
        assertThat(scouting.scoutings()).isEqualTo(1);
    }

    @Test
    void aTallyHalvesEveryHalfLifeInRealHours() {
        Tally one = NpcLearning.tallyOf(Map.of(UnitType.AXEMAN, 10), AT, DATA);
        Tally later = NpcLearning.fadedTo(one, AT.plus(Duration.ofHours(24)), 24);
        assertThat(later.attacks()).isCloseTo(0.5, within(1e-9));
        assertThat(later.infantry()).isCloseTo(200, within(1e-9));
        assertThat(NpcLearning.fadedTo(one, AT.plus(Duration.ofHours(48)), 24).attacks())
                .isCloseTo(0.25, within(1e-9));
        // never grows backwards
        assertThat(NpcLearning.fadedTo(one, AT.minusSeconds(60), 24)).isEqualTo(one);
    }

    @Test
    void addingFadesTheOlderPartToTheLaterMoment() {
        Tally first = NpcLearning.tallyOf(Map.of(UnitType.AXEMAN, 10), AT, DATA);
        Tally second =
                NpcLearning.tallyOf(
                        Map.of(UnitType.AXEMAN, 10), AT.plus(Duration.ofHours(24)), DATA);
        Tally sum = NpcLearning.plus(first, second, 24);
        assertThat(sum.at()).isEqualTo(AT.plus(Duration.ofHours(24)));
        assertThat(sum.attacks()).isCloseTo(1.5, within(1e-9));
        // a report read out of order fades the same way
        assertThat(NpcLearning.plus(second, first, 24)).isEqualTo(sum);
        assertThat(NpcLearning.plus(Tally.ZERO, first, 24)).isEqualTo(first);
    }

    @Test
    void cavalryAttacksTeachSpearmenUpToTheCap() {
        Tally seen = cavalryAttacks(5);
        Lesson lesson =
                NpcLearning.lesson(seen, List.of(), Wins.NONE, OFFENSIVE_ATTACK, AT, LEARNING);

        assertThat(lesson.defenceWeight()).isCloseTo(0.35, within(1e-9));
        assertThat(lesson.attackWeight()).isZero();
        assertThat(lesson.defence()).containsOnlyKeys(UnitType.SPEARMAN);

        Map<UnitType, Integer> mix = NpcLearning.learnedMix(OFFENSIVE_MID, lesson);
        // 0.65 × 10 % + 35 % = 41.5 %; axemen 0.65 × 45 % = 29.25 %
        assertThat(mix.get(UnitType.SPEARMAN)).isEqualTo(415);
        assertThat(mix.get(UnitType.AXEMAN)).isBetween(292, 293);
        assertThat(mix.values().stream().mapToInt(Integer::intValue).sum()).isBetween(998, 1002);

        // more evidence doesn't move more than the cap
        Lesson more =
                NpcLearning.lesson(
                        cavalryAttacks(50), List.of(), Wins.NONE, OFFENSIVE_ATTACK, AT, LEARNING);
        assertThat(more.defenceWeight()).isCloseTo(0.35, within(1e-9));
    }

    @Test
    void lessEvidenceMovesLessAndInfantryTeachesSwordsmenTheTemplateLacks() {
        Tally seen = Tally.ZERO;
        for (int i = 0; i < 2; i++) {
            seen = NpcLearning.plus(seen, infantryAttack(AT), 24);
        }
        Lesson lesson =
                NpcLearning.lesson(seen, List.of(), Wins.NONE, OFFENSIVE_ATTACK, AT, LEARNING);
        assertThat(lesson.defenceWeight()).isCloseTo(0.35 * 2 / 5, within(1e-9));
        Map<UnitType, Integer> mix = NpcLearning.learnedMix(OFFENSIVE_MID, lesson);
        assertThat(mix.get(UnitType.SWORDSMAN)).isEqualTo(140);
    }

    @Test
    void scoutingsAddScoutsToTheDefenceLesson() {
        Tally seen = cavalryAttacks(5);
        for (int i = 0; i < 5; i++) {
            seen =
                    NpcLearning.plus(
                            seen, NpcLearning.tallyOf(Map.of(UnitType.SCOUT, 5), AT, DATA), 24);
        }
        Map<UnitType, Double> defence = NpcLearning.defenceMix(seen, 0.15);
        assertThat(defence.get(UnitType.SCOUT)).isCloseTo(0.075, within(1e-9));
        assertThat(defence.get(UnitType.SPEARMAN)).isCloseTo(0.925, within(1e-9));
        // scoutings alone teach nothing
        Tally onlyScouted = NpcLearning.tallyOf(Map.of(UnitType.SCOUT, 5), AT, DATA);
        assertThat(
                        NpcLearning.lesson(
                                onlyScouted, List.of(), Wins.NONE, OFFENSIVE_ATTACK, AT, LEARNING))
                .isEqualTo(Lesson.NONE);
    }

    @Test
    void guildMatesTalliesCountHalf() {
        Lesson lesson =
                NpcLearning.lesson(
                        Tally.ZERO,
                        List.of(cavalryAttacks(4)),
                        Wins.NONE,
                        OFFENSIVE_ATTACK,
                        AT,
                        LEARNING);
        assertThat(lesson.defenceWeight()).isCloseTo(0.35 * 2 / 5, within(1e-9));
    }

    @Test
    void winsTeachTheirMixAmongTheLordsAttackUnits() {
        List<Win> raw =
                List.of(
                        new Win(
                                AT,
                                Map.of(
                                        UnitType.LIGHT_CAVALRY, 100,
                                        UnitType.AXEMAN, 300,
                                        UnitType.SPEARMAN, 10,
                                        UnitType.SCOUT, 50,
                                        UnitType.NOBLE, 1,
                                        UnitType.PALADIN, 1)),
                        new Win(AT, Map.of(UnitType.SCOUT, 20))); // scouts only: no win
        Wins wins = NpcLearning.winsOf(raw, AT, 24, DATA);
        assertThat(wins.count()).isEqualTo(1);
        assertThat(wins.population())
                .doesNotContainKeys(UnitType.NOBLE, UnitType.PALADIN)
                .containsEntry(UnitType.LIGHT_CAVALRY, 400.0);

        Map<UnitType, Double> attack = NpcLearning.attackMix(wins, OFFENSIVE_ATTACK);
        // spearmen are no attack unit of the lord: 400 cavalry, 300 axes, 100 scouts
        assertThat(attack).doesNotContainKey(UnitType.SPEARMAN);
        assertThat(attack.get(UnitType.LIGHT_CAVALRY)).isCloseTo(0.5, within(1e-9));
        assertThat(attack.get(UnitType.SCOUT)).isCloseTo(0.125, within(1e-9));

        Wins aged = NpcLearning.winsOf(raw, AT.plus(Duration.ofHours(24)), 24, DATA);
        assertThat(aged.count()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void bothLessonsShareTheCapInProportionToTheirEvidence() {
        Wins wins = new Wins(5, Map.of(UnitType.LIGHT_CAVALRY, 1000.0));
        Lesson both =
                NpcLearning.lesson(
                        cavalryAttacks(5), List.of(), wins, OFFENSIVE_ATTACK, AT, LEARNING);
        assertThat(both.defenceWeight()).isCloseTo(0.175, within(1e-9));
        assertThat(both.attackWeight()).isCloseTo(0.175, within(1e-9));

        Wins few = new Wins(1, Map.of(UnitType.LIGHT_CAVALRY, 1000.0));
        Lesson mostlyDefence =
                NpcLearning.lesson(
                        cavalryAttacks(5), List.of(), few, OFFENSIVE_ATTACK, AT, LEARNING);
        assertThat(mostlyDefence.defenceWeight() + mostlyDefence.attackWeight())
                .isCloseTo(0.35, within(1e-9));
        assertThat(mostlyDefence.attackWeight()).isCloseTo(0.35 * 0.2 / 1.2, within(1e-9));

        // wins with none of the lord's attack units teach nothing
        Wins foreign = new Wins(5, Map.of(UnitType.SPEARMAN, 1000.0));
        assertThat(
                        NpcLearning.lesson(
                                Tally.ZERO, List.of(), foreign, OFFENSIVE_ATTACK, AT, LEARNING))
                .isEqualTo(Lesson.NONE);
    }

    @Test
    void winsOnlyChangeProportionsAmongTheUnitsTheLordSends() {
        Wins wins = new Wins(5, Map.of(UnitType.LIGHT_CAVALRY, 1000.0));
        Lesson lesson =
                NpcLearning.lesson(Tally.ZERO, List.of(), wins, OFFENSIVE_ATTACK, AT, LEARNING);
        assertThat(lesson.attackWeight()).isCloseTo(0.35, within(1e-9));

        Map<UnitType, Integer> mix = NpcLearning.learnedMix(OFFENSIVE_MID, lesson);
        // spearmen are no attack unit: they keep their 10 %; the attack part (90 %) leans to
        // light cavalry: 0.65 × 35 % + 0.35 × 90 % = 54.25 %, axemen 0.65 × 45 % = 29.25 %
        assertThat(mix.get(UnitType.SPEARMAN)).isEqualTo(100);
        assertThat(mix.get(UnitType.LIGHT_CAVALRY)).isBetween(542, 543);
        assertThat(mix.get(UnitType.AXEMAN)).isBetween(292, 293);
        assertThat(mix.get(UnitType.SCOUT) + mix.get(UnitType.RAM)).isBetween(64, 66);
        assertThat(mix.values().stream().mapToInt(Integer::intValue).sum()).isBetween(998, 1002);
    }

    @Test
    void aDefensiveLordKeepsItsInfantryAgainstTheWins() {
        // DEFENSIVE MID (defensive.yml): it attacks with spearmen and swordsmen too
        Map<UnitType, Integer> defensiveMid =
                Map.of(
                        UnitType.SPEARMAN, 45,
                        UnitType.SWORDSMAN, 35,
                        UnitType.SCOUT, 5,
                        UnitType.LIGHT_CAVALRY, 5,
                        UnitType.HEAVY_CAVALRY, 10);
        Set<UnitType> defensiveAttack =
                Set.of(
                        UnitType.SPEARMAN,
                        UnitType.SWORDSMAN,
                        UnitType.LIGHT_CAVALRY,
                        UnitType.HEAVY_CAVALRY,
                        UnitType.RAM);
        Wins wins = new Wins(5, Map.of(UnitType.LIGHT_CAVALRY, 800.0, UnitType.SPEARMAN, 200.0));
        Lesson lesson =
                NpcLearning.lesson(Tally.ZERO, List.of(), wins, defensiveAttack, AT, LEARNING);
        assertThat(lesson.attack()).containsOnlyKeys(UnitType.LIGHT_CAVALRY);

        Map<UnitType, Integer> mix = NpcLearning.learnedMix(defensiveMid, lesson);
        assertThat(mix.get(UnitType.SPEARMAN)).isEqualTo(450);
        assertThat(mix.get(UnitType.SWORDSMAN)).isEqualTo(350);
        // the cavalry part (20 %) leans to light cavalry: 0.65 × 5 % + 0.35 × 20 % = 10.25 %
        assertThat(mix.get(UnitType.LIGHT_CAVALRY)).isBetween(102, 103);
    }

    @Test
    void withoutALessonTheTemplateStays() {
        assertThat(NpcLearning.learnedMix(OFFENSIVE_MID, Lesson.NONE)).isSameAs(OFFENSIVE_MID);
    }

    @Test
    void aDefenceReportTeachesTheLessonsAndASiegeAttackSetsAWallGoal() {
        var memory = NpcMemory.empty().draft();
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.DEFENSE,
                AT,
                onTheLord(Map.of(UnitType.AXEMAN, 50, UnitType.RAM, 4), 4),
                DATA,
                1,
                48,
                LEARNING);
        assertThat(memory.lessons.seen().attacks()).isEqualTo(1);
        assertThat(NpcLearning.wallGoal(memory.lessons, 1, AT)).isEqualTo(7);
        assertThat(memory.lessons.walls().get(1L).until()).isEqualTo(AT.plus(Duration.ofHours(48)));

        // a later siege attack that left the wall lower keeps the goal and extends it
        Instant later = AT.plus(Duration.ofHours(10));
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.DEFENSE,
                later,
                onTheLord(Map.of(UnitType.CATAPULT, 2), 1),
                DATA,
                1,
                48,
                LEARNING);
        assertThat(NpcLearning.wallGoal(memory.lessons, 1, later)).isEqualTo(7);
        assertThat(memory.lessons.walls().get(1L).until())
                .isEqualTo(later.plus(Duration.ofHours(48)));

        // the goal runs out, and pruning forgets it
        Instant end = later.plus(Duration.ofHours(48));
        assertThat(NpcLearning.wallGoal(memory.lessons, 1, end)).isNull();
        assertThat(NpcLearning.prune(memory.lessons, end).walls()).isEmpty();

        // without the learning numbers nothing is learned (the old entry point)
        var plain = NpcMemory.empty().draft();
        NpcIntel.learn(
                plain,
                LORD,
                ReportKind.DEFENSE,
                AT,
                onTheLord(Map.of(UnitType.RAM, 4), 4),
                DATA,
                1,
                48);
        assertThat(plain.lessons).isEqualTo(Lessons.NONE);
    }

    @Test
    void theWallGoalStopsAtTheWallsMaxLevel() {
        Lessons lessons =
                NpcLearning.learn(
                        Lessons.NONE, AT, onTheLord(Map.of(UnitType.RAM, 4), 19), DATA, LEARNING);
        assertThat(NpcLearning.wallGoal(lessons, 1, AT)).isEqualTo(20);
    }

    @Test
    void theWallGoalGoesInFrontOfTheStepsWhileItIsAboveTheWallAndTheWallCanBeBuilt() {
        List<Map<BuildingType, Integer>> steps = List.of(Map.of(BuildingType.FARM, 10));
        var wall = DATA.building(BuildingType.WALL);
        Map<BuildingType, Integer> levels = levels(5, 1);

        assertThat(NpcLearning.withWallGoal(steps, 8, levels, wall))
                .containsExactly(Map.of(BuildingType.WALL, 8), Map.of(BuildingType.FARM, 10));
        assertThat(NpcLearning.withWallGoal(steps, 5, levels, wall)).isSameAs(steps);
        assertThat(NpcLearning.withWallGoal(steps, null, levels, wall)).isSameAs(steps);
        // no barracks yet: the wall can't be built, the template goes on
        assertThat(NpcLearning.withWallGoal(steps, 8, levels(0, 0), wall)).isSameAs(steps);
    }

    @Test
    void theAdminSeesTheLargestChangesFirst() {
        Lesson lesson =
                NpcLearning.lesson(
                        cavalryAttacks(5), List.of(), Wins.NONE, OFFENSIVE_ATTACK, AT, LEARNING);
        var changes =
                NpcLearning.changes(OFFENSIVE_MID, NpcLearning.learnedMix(OFFENSIVE_MID, lesson));
        assertThat(changes).hasSize(4);
        // spearmen 10 % -> about 41 %, axemen 45 % -> 29 %, light cavalry 35 % -> 23 %
        assertThat(changes.get(0).unit()).isEqualTo(UnitType.SPEARMAN);
        assertThat(changes.get(0).points()).isBetween(31, 32);
        assertThat(changes.get(1)).isEqualTo(new NpcLordDto.MixChange(UnitType.AXEMAN, -16));
        assertThat(changes.get(2)).isEqualTo(new NpcLordDto.MixChange(UnitType.LIGHT_CAVALRY, -12));
    }

    @Test
    void memoryWithLessonsSurvivesTheDatabaseAndOldMemoryReadsWithout() {
        var converter = new NpcMemoryConverter();
        var draft = NpcMemory.empty().draft();
        draft.lessons =
                NpcLearning.learn(
                        Lessons.NONE, AT, onTheLord(Map.of(UnitType.RAM, 4), 2), DATA, LEARNING);
        NpcMemory memory = draft.build();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(memory)))
                .isEqualTo(memory);
        assertThat(converter.convertToEntityAttribute("{\"lastReportId\":5}").lessons())
                .isEqualTo(Lessons.NONE);
    }

    private static Map<BuildingType, Integer> levels(int wall, int barracks) {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, 0);
        }
        levels.put(BuildingType.WALL, wall);
        levels.put(BuildingType.BARRACKS, barracks);
        return levels;
    }

    private static Tally cavalryAttacks(int n) {
        Tally seen = Tally.ZERO;
        for (int i = 0; i < n; i++) {
            seen =
                    NpcLearning.plus(
                            seen,
                            NpcLearning.tallyOf(Map.of(UnitType.LIGHT_CAVALRY, 50), AT, DATA),
                            24);
        }
        return seen;
    }

    private static Tally infantryAttack(Instant at) {
        return NpcLearning.tallyOf(Map.of(UnitType.AXEMAN, 100), at, DATA);
    }

    private static BattleReport onTheLord(Map<UnitType, Integer> units, int wallAfter) {
        return new BattleReport(
                HUMAN_VILLAGE,
                LORD_VILLAGE,
                BattleOutcome.DEFENDER_WON,
                0.0,
                units,
                Map.of(),
                null,
                new BattleReport.DefenderSide(
                        Map.of(), Map.of(), List.of(), wallAfter + 1, wallAfter, null, null),
                null,
                null,
                null);
    }
}
