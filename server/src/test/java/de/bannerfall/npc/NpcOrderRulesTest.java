package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The rules an ally weighs an order with (2.1 S3, 12-solo-and-allies §8). */
class NpcOrderRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    static final NpcOrderSettings SETTINGS =
            new NpcOrderSettings(
                    Map.of(
                            NpcPersonality.DEFENSIVE, 0.3,
                            NpcPersonality.BALANCED, 0.6,
                            NpcPersonality.OFFENSIVE, 0.8),
                    2.0,
                    0.2,
                    20,
                    30,
                    60,
                    2,
                    5);

    @Test
    void belowATrustOf20ItSaysNo() {
        assertThat(NpcOrderRules.refuses(19.4, SETTINGS)).isTrue();
        assertThat(NpcOrderRules.refuses(19.5, SETTINGS)).isFalse(); // shown as 20
        assertThat(NpcOrderRules.refuses(-50, SETTINGS)).isTrue();
        assertThat(NpcOrderRules.refuses(64, SETTINGS)).isFalse();
    }

    @Test
    void itIsBoundToItselfAndItsGuild() {
        Set<Long> mates = Set.of(2L, 3L);
        assertThat(NpcOrderRules.bound(1L, 1, mates)).isTrue();
        assertThat(NpcOrderRules.bound(3L, 1, mates)).isTrue();
        assertThat(NpcOrderRules.bound(4L, 1, mates)).isFalse();
        assertThat(NpcOrderRules.bound(null, 1, mates)).isFalse(); // a barbarian village
    }

    @Test
    void anOrderedSupportTakesItsShareDoubledAtLeastAFifth() {
        assertThat(NpcOrderRules.supportShare(0.5, SETTINGS)).isEqualTo(1.0); // DEFENSIVE
        assertThat(NpcOrderRules.supportShare(0.25, SETTINGS)).isEqualTo(0.5); // BALANCED
        assertThat(NpcOrderRules.supportShare(0.0, SETTINGS)).isEqualTo(0.2); // OFFENSIVE
        assertThat(NpcOrderRules.supportShare(0.7, SETTINGS)).isEqualTo(1.0); // never above all
    }

    @Test
    void anOrderedAttackTakesThePersonalitysShareOfTheAttackUnits() {
        assertThat(NpcOrderRules.attackShare(NpcPersonality.OFFENSIVE, SETTINGS)).isEqualTo(0.8);
        assertThat(NpcOrderRules.attackShare(NpcPersonality.DEFENSIVE, SETTINGS)).isEqualTo(0.3);
        Map<UnitType, Integer> army =
                NpcOrderRules.army(
                        Map.of(UnitType.AXEMAN, 301, UnitType.SPEARMAN, 100, UnitType.RAM, 3),
                        List.of(UnitType.AXEMAN, UnitType.RAM),
                        0.6);
        assertThat(army)
                .containsExactlyInAnyOrderEntriesOf(Map.of(UnitType.AXEMAN, 180, UnitType.RAM, 1));
    }

    @Test
    void landTogetherPlansTheMarchToLandHalfAMinuteAfterTheHumansAttack() {
        Instant own = NOW.plusSeconds(3 * 3600);
        NpcOrderRules.Timing timing = NpcOrderRules.landTogether(NOW, own, 3600, SETTINGS);
        assertThat(timing)
                .isEqualTo(
                        new NpcOrderRules.Timing.Later(
                                own.plusSeconds(30).minusSeconds(3600), own.plusSeconds(30)));
    }

    @Test
    void landTogetherLeavesNowWhenItStillLandsWithinTheMinute() {
        Instant own = NOW.plusSeconds(3600);
        // the aim (own + 30 s) is 10 s in the past, but now + travel lands own + 40 s
        assertThat(NpcOrderRules.landTogether(NOW, own, 3640, SETTINGS))
                .isInstanceOf(NpcOrderRules.Timing.Now.class);
        // own + 60 s: still within
        assertThat(NpcOrderRules.landTogether(NOW, own, 3660, SETTINGS))
                .isInstanceOf(NpcOrderRules.Timing.Now.class);
    }

    @Test
    void landTogetherIsTooLateBeyondTheMinute() {
        Instant own = NOW.plusSeconds(600);
        assertThat(NpcOrderRules.landTogether(NOW, own, 661, SETTINGS))
                .isInstanceOf(NpcOrderRules.Timing.TooLate.class);
        assertThat(NpcOrderRules.landTogether(NOW, own, 3 * 3600, SETTINGS))
                .isInstanceOf(NpcOrderRules.Timing.TooLate.class);
    }

    @Test
    void resourcesOnlyAboveItsNextGoal() {
        assertThat(
                        NpcOrderRules.surplus(
                                new ResourceAmounts(5000, 800, 3000),
                                new ResourceAmounts(1000, 1200, 0)))
                .isEqualTo(new ResourceAmounts(4000, 0, 3000));
    }

    @Test
    void aSendFailsForWantOfRoomOnlyWhenItsSurplusFillsAMerchant() {
        assertThat(NpcOrderRules.wouldSend(new ResourceAmounts(600, 400, 0), 1)).isTrue();
        assertThat(NpcOrderRules.wouldSend(new ResourceAmounts(600, 300, 0), 1)).isFalse();
        assertThat(NpcOrderRules.wouldSend(new ResourceAmounts(5000, 0, 0), 0)).isFalse();
    }

    @Test
    void aPlannedMarchGoesOnlyWithAllItsUnits() {
        Map<UnitType, Integer> planned = Map.of(UnitType.AXEMAN, 340, UnitType.RAM, 2);
        assertThat(NpcOrderRules.stillHome(planned, Map.of(UnitType.AXEMAN, 400, UnitType.RAM, 2)))
                .isEqualTo(planned);
        assertThat(NpcOrderRules.stillHome(planned, Map.of(UnitType.AXEMAN, 339, UnitType.RAM, 5)))
                .isNull();
    }

    @Test
    void invalidSettingsStopTheStart() {
        assertThatThrownBy(
                        () ->
                                new NpcOrderSettings(
                                        Map.of(NpcPersonality.OFFENSIVE, 0.8),
                                        2.0,
                                        1.5,
                                        20,
                                        90,
                                        60,
                                        2,
                                        0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attack-share.DEFENSIVE")
                .hasMessageContaining("support-min-share")
                .hasMessageContaining("land-after-seconds")
                .hasMessageContaining("candidates");
    }

    @Test
    void theKindsATargetTakes() {
        assertThat(NpcOrderSheet.kinds(7L, 7, true))
                .containsExactly(NpcOrderKind.SUPPORT, NpcOrderKind.SEND);
        assertThat(NpcOrderSheet.kinds(8L, 7, true)).containsExactly(NpcOrderKind.SUPPORT);
        assertThat(NpcOrderSheet.kinds(8L, 7, false))
                .containsExactly(NpcOrderKind.ATTACK, NpcOrderKind.HOLD);
        assertThat(NpcOrderSheet.kinds(null, 7, false))
                .containsExactly(NpcOrderKind.ATTACK, NpcOrderKind.HOLD);
    }
}
