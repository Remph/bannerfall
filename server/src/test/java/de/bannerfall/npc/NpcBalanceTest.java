package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rubber band and the production factor (09-npc-lords §8), the limits towards players (§7), the
 * memory format and CUSTOM.
 */
class NpcBalanceTest {

    private static final NpcBalanceSettings.Balance BALANCE = TestNpcData.balance().balance();
    private static final Difficulty NORMAL = BALANCE.difficulties().get(NpcDifficulty.NORMAL);

    @Test
    void withoutHumansTheRubberBandIsOff() {
        assertThat(NpcBalance.stance(5000, List.of(), NORMAL)).isEqualTo(NpcMemory.Stance.NONE);
        assertThat(NpcBalance.productionFactor(NORMAL, NpcMemory.Stance.NONE)).isEqualTo(0.9);
    }

    @Test
    void aLordFarAheadOfTheStrongestHumanIsBraked() {
        // NORMAL: lead margin 1.25
        assertThat(NpcBalance.stance(1250, List.of(1000, 200), NORMAL).braked()).isFalse();
        assertThat(NpcBalance.stance(1251, List.of(1000, 200), NORMAL).braked()).isTrue();
    }

    @Test
    void aLordFarBehindTheAverageIsBoosted() {
        // NORMAL: boost below 0.5 × the average (here 600 → 300), factor 1.25
        NpcMemory.Stance behind = NpcBalance.stance(299, List.of(1000, 200), NORMAL);
        NpcMemory.Stance fine = NpcBalance.stance(300, List.of(1000, 200), NORMAL);

        assertThat(behind.boosted()).isTrue();
        assertThat(fine.boosted()).isFalse();
        assertThat(NpcBalance.productionFactor(NORMAL, behind)).isCloseTo(0.9 * 1.25, within(1e-9));
        assertThat(NpcBalance.productionFactor(NORMAL, fine)).isEqualTo(0.9);
    }

    @Test
    void easyNeverBoostsAndTheFactorsFollowTheDifficulty() {
        Difficulty easy = BALANCE.difficulties().get(NpcDifficulty.EASY);
        Difficulty hard = BALANCE.difficulties().get(NpcDifficulty.HARD);

        assertThat(NpcBalance.stance(1, List.of(1000), easy).boosted()).isFalse();
        assertThat(NpcBalance.productionFactor(easy, NpcMemory.Stance.NONE)).isEqualTo(0.7);
        assertThat(NpcBalance.productionFactor(hard, NpcMemory.Stance.NONE)).isEqualTo(1.1);
    }

    @Test
    void customIsNormalUntilTheAdminSetsIt() {
        Difficulty custom = new Difficulty(1.5, 0, 1, 2, 1, 1, false, false, "01:00", "02:00");

        assertThat(BALANCE.difficulty(NpcDifficulty.CUSTOM, null)).isEqualTo(NORMAL);
        assertThat(BALANCE.difficulty(NpcDifficulty.CUSTOM, custom)).isEqualTo(custom);
        assertThat(BALANCE.difficulty(NpcDifficulty.HARD, custom).productionFactor())
                .isEqualTo(1.1);
    }

    @Test
    void invalidDifficultyValuesAreReported() {
        Difficulty bad = new Difficulty(0, -1, 0, 0, -1, 0, true, true, "25:00", null);

        assertThat(bad.problems("custom"))
                .anyMatch(p -> p.contains("production-factor"))
                .anyMatch(p -> p.contains("boost-below"))
                .anyMatch(p -> p.contains("attack caps"))
                .anyMatch(p -> p.contains("HH:mm"));
        assertThat(NORMAL.problems("normal")).isEmpty();
    }

    @Test
    void theMemoryRoundTripsAndOldMemoryStaysReadable() {
        var converter = new NpcMemoryConverter();
        NpcMemory memory =
                NpcMemory.empty()
                        .withLastReportId(42)
                        .withLedger(7, new NpcMemory.Ledger(1500.5, 300))
                        .withStance(new NpcMemory.Stance(true, false));

        String json = converter.convertToDatabaseColumn(memory);

        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(memory);
        // '{}' is what the migration's default writes; unknown properties are ignored
        assertThat(converter.convertToEntityAttribute("{}")).isEqualTo(NpcMemory.empty());
        assertThat(
                        converter
                                .convertToEntityAttribute(
                                        "{\"later\":{\"1\":2},\"lastReportId\":5}")
                                .lastReportId())
                .isEqualTo(5);
    }

    @Test
    void theCustomValuesRoundTripWithTheirTimes() {
        var converter = new NpcDifficultyConverter();

        String json = converter.convertToDatabaseColumn(NORMAL);

        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(NORMAL);
        assertThat(json).contains("23:00").doesNotContain("[23");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void theLedgerDecays() {
        var ledger = new NpcMemory.Ledger(1000, 500).decayed(0.99);

        assertThat(ledger.build()).isCloseTo(990, within(1e-9));
        assertThat(ledger.troops()).isCloseTo(495, within(1e-9));
    }

    // ---- limits towards players (§7) ----

    private static final Difficulty HARD = BALANCE.difficulties().get(NpcDifficulty.HARD);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void theCapsCountTheLast24RealHours() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        var sent =
                List.of(
                        new NpcMemory.SentAttack(1, now.minus(Duration.ofHours(24))),
                        new NpcMemory.SentAttack(1, now.minus(Duration.ofHours(24)).plusSeconds(1)),
                        new NpcMemory.SentAttack(1, now),
                        new NpcMemory.SentAttack(2, now));

        assertThat(NpcBalance.sentTo(sent, 1, now)).isEqualTo(2);
        assertThat(NpcBalance.sentTo(sent, 2, now)).isEqualTo(1);
        assertThat(NpcBalance.sentTo(sent, 3, now)).isZero();
    }

    @Test
    void oneLordPerPlayerAndAllLordsPerHuman() {
        // NORMAL: 4 per player and lord, 8 per human for all lords
        assertThat(NpcBalance.capsAllow(NORMAL, 3, true, 3, 1)).isTrue();
        assertThat(NpcBalance.capsAllow(NORMAL, 4, true, 4, 1)).isFalse();
        assertThat(NpcBalance.capsAllow(NORMAL, 3, true, 7, 1)).isTrue();
        assertThat(NpcBalance.capsAllow(NORMAL, 0, true, 8, 1)).isFalse();
        // the per-human cap doesn't apply to an NPC lord's villages
        assertThat(NpcBalance.capsAllow(NORMAL, 0, false, 99, 1)).isTrue();
        // a scouting asks for room for the attack after it
        assertThat(NpcBalance.capsAllow(NORMAL, 3, true, 3, 2)).isFalse();
    }

    @Test
    void noArrivalAtAHumanVillageBetween23And7Berlin() {
        var balance = new NpcBalance(TestNpcData.balance(), null);
        assertThat(balance.quietArrival(NORMAL, berlin(22, 59))).isFalse();
        assertThat(balance.quietArrival(NORMAL, berlin(23, 0))).isTrue();
        assertThat(balance.quietArrival(NORMAL, berlin(3, 0))).isTrue();
        assertThat(balance.quietArrival(NORMAL, berlin(6, 59))).isTrue();
        assertThat(balance.quietArrival(NORMAL, berlin(7, 0))).isFalse();
        // HARD has no quiet hours
        assertThat(balance.quietArrival(HARD, berlin(23, 0))).isFalse();
    }

    @Test
    void aHumansLastVillageIsSparedOnNormalButNotOnHard() {
        var last = NpcTargetingTest.human(500, 500, 1);
        var oneOfTwo = NpcTargetingTest.human(500, 500, 2);
        var lord = NpcTargetingTest.lord(500, 500);

        assertThat(NpcBalance.spared(NORMAL, last)).isTrue();
        assertThat(NpcBalance.spared(NORMAL, oneOfTwo)).isFalse();
        assertThat(NpcBalance.spared(NORMAL, lord)).isFalse();
        assertThat(NpcBalance.spared(HARD, last)).isFalse();
    }

    @Test
    void aLordsLastVillageIsSparedByOtherLordsOnEveryDifficulty() {
        var lastOfLord = new NpcWorld.MapVillage(2, 0, 0, 78L, true, 500, 100, 500, 1, null);

        assertThat(NpcBalance.spared(NORMAL, lastOfLord)).isTrue();
        assertThat(NpcBalance.spared(HARD, lastOfLord)).isTrue();
        assertThat(NpcBalance.spared(HARD, NpcTargetingTest.lord(500, 500))).isFalse();
    }

    private static Instant berlin(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 7, hour, minute).atZone(BERLIN).toInstant();
    }
}
