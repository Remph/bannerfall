package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.npc.NpcTrustRules.Answer;
import de.bannerfall.npc.NpcTrustRules.Change;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rules of trust between lords and humans (2.1 S2b, 12-solo-and-allies §5). */
class NpcTrustRulesTest {

    private static final NpcTrustSettings SETTINGS =
            new NpcTrustSettings(
                    2,
                    10,
                    20,
                    1,
                    10,
                    5,
                    -40,
                    -10,
                    -15,
                    Map.of(
                            NpcPersonality.DEFENSIVE, 30,
                            NpcPersonality.BALANCED, 50,
                            NpcPersonality.OFFENSIVE, 70),
                    10,
                    24);

    @Test
    void trustDriftsTowardsZeroByTwoAGameDayAndNeverPastIt() {
        assertThat(NpcTrustRules.drifted(30, Duration.ofDays(1), 1, 2)).isEqualTo(28);
        assertThat(NpcTrustRules.drifted(-30, Duration.ofDays(1), 1, 2)).isEqualTo(-28);
        // at speed 4 a game day is 6 real hours
        assertThat(NpcTrustRules.drifted(30, Duration.ofHours(6), 4, 2)).isEqualTo(28);
        assertThat(NpcTrustRules.drifted(30, Duration.ofHours(12), 1, 2)).isEqualTo(29);
        assertThat(NpcTrustRules.drifted(3, Duration.ofDays(10), 1, 2)).isZero();
        assertThat(NpcTrustRules.drifted(-3, Duration.ofDays(10), 1, 2)).isZero();
        assertThat(NpcTrustRules.drifted(30, Duration.ofDays(-1), 1, 2)).isEqualTo(30);
    }

    @Test
    void trustStaysWithinMinusAndPlusAHundred() {
        assertThat(NpcTrustRules.plus(95, 10)).isEqualTo(100);
        assertThat(NpcTrustRules.plus(-80, -40)).isEqualTo(-100);
        assertThat(NpcTrustRules.plus(10, -15)).isEqualTo(-5);
    }

    @Test
    void aGainIsCappedByWhatTheSameKindGaveToday() {
        assertThat(NpcTrustRules.capped(10, 0, 20)).isEqualTo(10);
        assertThat(NpcTrustRules.capped(10, 15, 20)).isEqualTo(5);
        assertThat(NpcTrustRules.capped(10, 20, 20)).isZero();
        assertThat(NpcTrustRules.capped(10, 25, 20)).isZero();
    }

    @Test
    void shipmentsGiveOnePerFullThousandOfTheDayAtMostTen() {
        assertThat(NpcTrustRules.shippedGain(0, 2500, 1, 10)).isEqualTo(2);
        // small shipments add up: 600 + 600 cross the first thousand
        assertThat(NpcTrustRules.shippedGain(0, 600, 1, 10)).isZero();
        assertThat(NpcTrustRules.shippedGain(600, 600, 1, 10)).isEqualTo(1);
        assertThat(NpcTrustRules.shippedGain(9500, 5000, 1, 10)).isEqualTo(1);
        assertThat(NpcTrustRules.shippedGain(12000, 5000, 1, 10)).isZero();
    }

    @Test
    void theBarDependsOnThePersonalityAndIsHigherInALordGuild() {
        assertThat(NpcTrustRules.bar(SETTINGS, NpcPersonality.DEFENSIVE, false)).isEqualTo(30);
        assertThat(NpcTrustRules.bar(SETTINGS, NpcPersonality.BALANCED, false)).isEqualTo(50);
        assertThat(NpcTrustRules.bar(SETTINGS, NpcPersonality.OFFENSIVE, false)).isEqualTo(70);
        assertThat(NpcTrustRules.bar(SETTINGS, NpcPersonality.BALANCED, true)).isEqualTo(60);
    }

    @Test
    void aLordSaysYesAtItsBarWithRoomAndNoOtherwise() {
        assertThat(NpcTrustRules.answer(50, 50, true)).isEqualTo(Answer.YES);
        assertThat(NpcTrustRules.answer(49.6, 50, true)).isEqualTo(Answer.YES); // shown as 50
        assertThat(NpcTrustRules.answer(49.4, 50, true)).isEqualTo(Answer.NOT_ENOUGH_TRUST);
        assertThat(NpcTrustRules.answer(80, 50, false)).isEqualTo(Answer.GUILD_FULL);
        assertThat(NpcTrustRules.answer(10, 50, false)).isEqualTo(Answer.NOT_ENOUGH_TRUST);
    }

    @Test
    void anAllyLeavesOnlyBelowZero() {
        assertThat(NpcTrustRules.leaves(0)).isFalse();
        assertThat(NpcTrustRules.leaves(-0.4)).isFalse();
        assertThat(NpcTrustRules.leaves(-1)).isTrue();
    }

    @Test
    void theLastThreeChangesAreKeptNewestFirst() {
        List<Change> changes = List.of();
        for (int i = 1; i <= 4; i++) {
            changes =
                    NpcTrustRules.withChange(
                            changes, new Change(TrustReason.SHIPPED, i, "Village " + i));
        }
        assertThat(changes).extracting(Change::delta).containsExactly(4, 3, 2);
    }

    @Test
    void aGameDayIsARealDayDividedByTheSpeed() {
        assertThat(NpcTrustRules.day(1)).isEqualTo(Duration.ofDays(1));
        assertThat(NpcTrustRules.day(4)).isEqualTo(Duration.ofHours(6));
    }
}
