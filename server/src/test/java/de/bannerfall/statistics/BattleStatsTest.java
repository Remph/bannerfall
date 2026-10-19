package de.bannerfall.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BattleResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** What a battle adds to the statistics (02-game-rules §24). */
class BattleStatsTest {

    private static final long ATTACKER = 1;
    private static final long OWNER = 2;
    private static final long FRIEND = 3;

    /** Spearmen 1, axemen 1, scouts 2, light cavalry 4, nobles 100. */
    private static final ToIntFunction<UnitType> POP =
            type ->
                    switch (type) {
                        case SCOUT -> 2;
                        case LIGHT_CAVALRY -> 4;
                        case NOBLE -> 100;
                        default -> 1;
                    };

    private static BattleResult.MainBattle main(boolean attackerWon) {
        return new BattleResult.MainBattle(1, 1, 0, attackerWon, 0, 0);
    }

    private static BattleResult result(
            BattleResult.MainBattle main,
            Map<UnitType, Integer> attackerLosses,
            List<Map<UnitType, Integer>> defenderLosses) {
        return new BattleResult(null, main, attackerLosses, Map.of(), defenderLosses, 0, 0);
    }

    @Test
    void aWonAttackOnBarbariansCountsOnlyForTheAttacker() {
        var stats =
                BattleStats.of(
                        ATTACKER,
                        null,
                        List.of(Map.of(UnitType.SPEARMAN, 10)),
                        Arrays.asList((Long) null),
                        result(
                                main(true),
                                Map.of(UnitType.AXEMAN, 3),
                                List.of(Map.of(UnitType.SPEARMAN, 10))),
                        250,
                        POP);

        assertThat(stats).containsOnlyKeys(ATTACKER);
        assertThat(stats.get(ATTACKER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.ATTACKS_WON, 1L,
                                Stat.KILLED_ATTACKING, 10L,
                                Stat.UNITS_LOST, 3L,
                                Stat.LOOT_TAKEN, 250L));
    }

    @Test
    void theDefendersShareTheKilledAttackersByWhatTheyBrought() {
        // the owner brought 10 population, his friend 30: 1/4 and 3/4 of the 40 killed
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(
                                Map.of(UnitType.SPEARMAN, 10),
                                Map.of(UnitType.SPEARMAN, 10, UnitType.LIGHT_CAVALRY, 5)),
                        List.of(OWNER, FRIEND),
                        result(
                                main(false),
                                Map.of(UnitType.AXEMAN, 40),
                                List.of(
                                        Map.of(UnitType.SPEARMAN, 2),
                                        Map.of(UnitType.LIGHT_CAVALRY, 1))),
                        0,
                        POP);

        assertThat(stats.get(ATTACKER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.ATTACKS_LOST, 1L,
                                Stat.UNITS_LOST, 40L,
                                Stat.KILLED_ATTACKING, 6L));
        assertThat(stats.get(OWNER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.DEFENCES_WON, 1L,
                                Stat.KILLED_DEFENDING, 10L,
                                Stat.UNITS_LOST, 2L));
        assertThat(stats.get(FRIEND))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.DEFENCES_WON, 1L,
                                Stat.KILLED_DEFENDING, 30L,
                                Stat.UNITS_LOST, 4L));
    }

    @Test
    void theRestOfTheShareGoesToTheBiggestStackAndNothingIsLost() {
        // 7 killed, brought 1 : 1 : 1 → 2 each, the rest (1) to the owner's stack (first on a tie)
        long other = 4;
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(
                                Map.of(UnitType.SPEARMAN, 1),
                                Map.of(UnitType.SPEARMAN, 1),
                                Map.of(UnitType.SPEARMAN, 1)),
                        List.of(OWNER, FRIEND, other),
                        result(
                                main(false),
                                Map.of(UnitType.AXEMAN, 7),
                                List.of(Map.of(), Map.of(), Map.of())),
                        0,
                        POP);

        assertThat(stats.get(OWNER)).containsEntry(Stat.KILLED_DEFENDING, 3L);
        assertThat(stats.get(FRIEND)).containsEntry(Stat.KILLED_DEFENDING, 2L);
        assertThat(stats.get(other)).containsEntry(Stat.KILLED_DEFENDING, 2L);
    }

    @Test
    void inAnEmptyVillageTheOwnerGetsTheKillsAndALostDefence() {
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(Map.of()),
                        List.of(OWNER),
                        result(main(true), Map.of(UnitType.AXEMAN, 4), List.of(Map.of())),
                        100,
                        POP);

        assertThat(stats.get(OWNER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.DEFENCES_LOST, 1L,
                                Stat.KILLED_DEFENDING, 4L,
                                Stat.LOOT_LOST, 100L));
        assertThat(stats.get(ATTACKER)).containsEntry(Stat.ATTACKS_WON, 1L);
    }

    @Test
    void scoutsAloneCountKillsButNoWonOrLostBattle() {
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(Map.of(UnitType.SCOUT, 3)),
                        List.of(OWNER),
                        result(null, Map.of(UnitType.SCOUT, 2), List.of(Map.of(UnitType.SCOUT, 1))),
                        0,
                        POP);

        assertThat(stats.get(ATTACKER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Stat.UNITS_LOST, 4L, Stat.KILLED_ATTACKING, 2L));
        assertThat(stats.get(OWNER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Stat.KILLED_DEFENDING, 4L, Stat.UNITS_LOST, 2L));
    }

    @Test
    void theAttackersOwnSupportThereIsLostButNeitherKilledNorADefence() {
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(Map.of(UnitType.SPEARMAN, 5), Map.of(UnitType.SPEARMAN, 5)),
                        List.of(OWNER, ATTACKER),
                        result(
                                main(true),
                                Map.of(),
                                List.of(
                                        Map.of(UnitType.SPEARMAN, 5),
                                        Map.of(UnitType.SPEARMAN, 5))),
                        0,
                        POP);

        assertThat(stats.get(ATTACKER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Stat.ATTACKS_WON, 1L,
                                Stat.KILLED_ATTACKING, 5L,
                                Stat.UNITS_LOST, 5L));
        assertThat(stats.get(OWNER))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(Stat.DEFENCES_LOST, 1L, Stat.UNITS_LOST, 5L));
    }

    @Test
    void anEmptySupportStackIsNoDefenderAndAnOwnVillageCountsForNothing() {
        var stats =
                BattleStats.of(
                        ATTACKER,
                        OWNER,
                        List.of(Map.of(UnitType.SPEARMAN, 5), Map.of()),
                        List.of(OWNER, FRIEND),
                        result(
                                main(false),
                                Map.of(UnitType.AXEMAN, 2),
                                List.of(Map.of(), Map.of())),
                        0,
                        POP);
        assertThat(stats).doesNotContainKey(FRIEND);

        assertThat(
                        BattleStats.of(
                                ATTACKER,
                                ATTACKER,
                                List.of(Map.of(UnitType.SPEARMAN, 5)),
                                List.of(ATTACKER),
                                result(
                                        main(false),
                                        Map.of(UnitType.AXEMAN, 2),
                                        List.of(Map.of(UnitType.SPEARMAN, 1))),
                                0,
                                POP))
                .isEmpty();
    }
}
