package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.TestGameData;
import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcTargeting.Estimate;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.rules.Production;
import de.bannerfall.rules.Storage;
import de.bannerfall.world.WorldSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** The pure warfare decisions of 09-npc-lords §6 (estimate, targets, intel age, farms, …). */
class NpcTargetingTest {

    private static final GameData DATA = TestGameData.load();
    private static final NpcTemplates TEMPLATES = TestNpcData.templates();
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    // ---- the estimate (§6.1) ----

    @Test
    void aSeenBarbarianWallCountsAsGrownSinceWhileWallsAreOn() {
        // speed 1: a growth round every 24 h, cap 5
        WorldSettings walls = WorldSettings.defaults().withBarbarianWalls(true, 0, 5, true);
        Instant seenAt = NOW.minus(Duration.ofHours(50));

        assertThat(NpcTargeting.expectedBarbarianWall(0, seenAt, NOW, walls)).isEqualTo(3);
        assertThat(NpcTargeting.expectedBarbarianWall(4, seenAt, NOW, walls)).isEqualTo(5);
        assertThat(NpcTargeting.expectedBarbarianWall(7, seenAt, NOW, walls)).isEqualTo(7);
        assertThat(NpcTargeting.expectedBarbarianWall(2, NOW, NOW, walls)).isEqualTo(2);
        // walls off: barbarian villages don't change (09 §4)
        WorldSettings off = WorldSettings.defaults().withBarbarianWalls(false, 0, 5, true);
        assertThat(NpcTargeting.expectedBarbarianWall(0, seenAt, NOW, off)).isZero();
    }

    @Test
    void theEstimateFightsAgainstTheDefendersSeenTimesTheMargin() {
        var axemen = Map.of(UnitType.AXEMAN, 100);
        var spearmen = Map.of(UnitType.SPEARMAN, 100);

        // 160 spearmen: D = 2420, A = 4000, loss (2420/4000)^1.5 = 0.4706 → a go at 0.5
        Estimate balanced = NpcTargeting.estimate(DATA, axemen, spearmen, 0, null, 0, 1.6, 0.5);
        assertThat(balanced.go()).isTrue();
        assertThat(balanced.lossRatio()).isCloseTo(0.4706, within(1e-4));

        // 200 spearmen: still a win, but losing 66 % is too much
        Estimate defensive = NpcTargeting.estimate(DATA, axemen, spearmen, 0, null, 0, 2.0, 0.5);
        assertThat(defensive.go()).isFalse();

        // 300 spearmen: D = 4520 > 4000, the attack would be lost
        Estimate lost = NpcTargeting.estimate(DATA, axemen, spearmen, 0, null, 0, 3.0, 0.5);
        assertThat(lost.go()).isFalse();
        assertThat(lost.lossRatio()).isEqualTo(1);
    }

    @Test
    void aScoutedBulwarkMakesTheDefenceStronger() {
        var axemen = Map.of(UnitType.AXEMAN, 100);
        var spearmen = Map.of(UnitType.SPEARMAN, 100);

        Estimate plain = NpcTargeting.estimate(DATA, axemen, spearmen, 5, null, 0, 1, 1, 0);
        Estimate bulwark = NpcTargeting.estimate(DATA, axemen, spearmen, 5, null, 0, 1, 1, 0.10);

        // 02 §21.2: the attacker loses 41 instead of 47 of his 100 axemen
        assertThat(plain.lossRatio()).isCloseTo(0.41, within(0.01));
        assertThat(bulwark.lossRatio()).isCloseTo(0.47, within(0.01));
    }

    @Test
    void theBulwarkTheLordExpectsIsTheScoutedOneAtTheScoutedHallsTier() {
        BannerCatalog catalog = banners();
        var bulwarkIII = new BattleReport.ScoutedBanner(BannerType.BULWARK, 3);

        assertThat(NpcTargeting.bulwark(scouted(bulwarkIII, 2), catalog)).isEqualTo(0.06);
        assertThat(NpcTargeting.bulwark(scouted(bulwarkIII, 3), catalog)).isEqualTo(0.10);
        // no hall: the banner sleeps
        assertThat(NpcTargeting.bulwark(scouted(bulwarkIII, 0), catalog)).isZero();
        // another banner, none, never scouted, no intel: nothing
        assertThat(
                        NpcTargeting.bulwark(
                                scouted(new BattleReport.ScoutedBanner(BannerType.WAR, 3), 3),
                                catalog))
                .isZero();
        assertThat(NpcTargeting.bulwark(scouted(null, 3), catalog)).isZero();
        assertThat(NpcTargeting.bulwark(intel(NOW, Map.of(), null), catalog)).isZero();
        assertThat(NpcTargeting.bulwark(null, catalog)).isZero();
    }

    @Test
    void bannerThievesAmongTheDefendersDefendWithThree() {
        var army = Map.of(UnitType.AXEMAN, 10); // A = 400
        // 100 thieves: D = 300 + 20 < 400; 200 thieves: D = 620
        assertThat(
                        NpcTargeting.estimate(
                                        DATA,
                                        army,
                                        Map.of(UnitType.BANNER_THIEF, 100),
                                        0,
                                        null,
                                        0,
                                        1.0,
                                        1)
                                .go())
                .isTrue();
        assertThat(
                        NpcTargeting.estimate(
                                        DATA,
                                        army,
                                        Map.of(UnitType.BANNER_THIEF, 200),
                                        0,
                                        null,
                                        0,
                                        1.0,
                                        1)
                                .go())
                .isFalse();
    }

    @Test
    void theMarginRoundsEachCountUpAndTheWallCounts() {
        var army = Map.of(UnitType.AXEMAN, 10); // A = 400
        var three = Map.of(UnitType.SPEARMAN, 3);
        // wall 5: 3 spearmen → D = (45 + 20 + 250) × 1.25 = 393.75 < 400
        assertThat(NpcTargeting.estimate(DATA, army, three, 5, null, 0, 1.0, 1).go()).isTrue();
        // × 1.1 = 3.3, rounded up to 4 → D = 412.5: lost
        assertThat(NpcTargeting.estimate(DATA, army, three, 5, null, 0, 1.1, 1).go()).isFalse();
    }

    @Test
    void scoutsAloneAreNeverAGoAndRamsLowerTheWallAfter() {
        assertThat(
                        NpcTargeting.estimate(
                                        DATA,
                                        Map.of(UnitType.SCOUT, 50),
                                        Map.of(),
                                        0,
                                        null,
                                        0,
                                        1,
                                        1)
                                .go())
                .isFalse();
        Estimate rams =
                NpcTargeting.estimate(
                        DATA,
                        Map.of(UnitType.AXEMAN, 200, UnitType.RAM, 10),
                        Map.of(),
                        3,
                        null,
                        0,
                        1.3,
                        0.5);
        assertThat(rams.go()).isTrue();
        assertThat(rams.wallAfter()).isLessThan(3);
    }

    // ---- who may be attacked (§6.4) ----

    @Test
    void defensiveOnlyAttacksVillagesBelowHalfOfItsOwn() {
        var template = TEMPLATES.of(NpcPersonality.DEFENSIVE);
        assertThat(NpcTargeting.targetAllowed(template, 1000, 5000, human(499, 99999, 3), false))
                .isTrue();
        assertThat(NpcTargeting.targetAllowed(template, 1000, 5000, human(500, 1, 3), false))
                .isFalse();
        // a grudge changes nothing for DEFENSIVE
        assertThat(NpcTargeting.targetAllowed(template, 1000, 5000, human(500, 1, 3), true))
                .isFalse();
        assertThat(NpcTargeting.targetAllowed(template, 1000, 5000, barbarian(5000), false))
                .isTrue();
    }

    @Test
    void balancedNeedsAWeakerVillageAndAWeakerHumanUnlessItHoldsAGrudge() {
        var template = TEMPLATES.of(NpcPersonality.BALANCED);
        // village 799 < 800, human 749 < 750
        assertThat(NpcTargeting.targetAllowed(template, 1000, 1000, human(799, 749, 2), false))
                .isTrue();
        assertThat(NpcTargeting.targetAllowed(template, 1000, 1000, human(800, 749, 2), false))
                .isFalse();
        assertThat(NpcTargeting.targetAllowed(template, 1000, 1000, human(799, 750, 2), false))
                .isFalse();
        // the human-player limit is for humans only
        assertThat(NpcTargeting.targetAllowed(template, 1000, 1000, lord(799, 5000), false))
                .isTrue();
        // a grudge lifts both
        assertThat(NpcTargeting.targetAllowed(template, 1000, 1000, human(5000, 9000, 2), true))
                .isTrue();
    }

    @Test
    void offensiveMayAttackAnyone() {
        var template = TEMPLATES.of(NpcPersonality.OFFENSIVE);
        assertThat(NpcTargeting.targetAllowed(template, 100, 100, human(9000, 90000, 9), false))
                .isTrue();
    }

    // ---- intel age (§4) ----

    @Test
    void intelIsFreshForSixHoursDividedBySpeed() {
        Intel sixHours = intel(NOW.minus(Duration.ofHours(6)), Map.of(), null);
        Intel older = intel(NOW.minus(Duration.ofHours(6)).minusSeconds(1), Map.of(), null);
        assertThat(NpcTargeting.isFresh(sixHours, NOW, 6, 1)).isTrue();
        assertThat(NpcTargeting.isFresh(older, NOW, 6, 1)).isFalse();
        // speed 3: two hours
        assertThat(
                        NpcTargeting.isFresh(
                                intel(NOW.minus(Duration.ofHours(2)), Map.of(), null), NOW, 6, 3))
                .isTrue();
        assertThat(
                        NpcTargeting.isFresh(
                                intel(NOW.minus(Duration.ofMinutes(121)), Map.of(), null),
                                NOW,
                                6,
                                3))
                .isFalse();
    }

    @Test
    void emptyBarbarianIntelStaysUsableButPlayerAndDefendedIntelMustBeFresh() {
        Instant old = NOW.minus(Duration.ofHours(20));
        Intel empty = intel(old, Map.of(), BattleOutcome.ATTACKER_WON);
        Intel defended = intel(old, Map.of(UnitType.SPEARMAN, 5), BattleOutcome.ATTACKER_WON);
        Intel lost = intel(old, Map.of(), BattleOutcome.DEFENDER_WON);

        assertThat(NpcTargeting.usable(empty, true, NOW, 6, 1)).isSameAs(empty);
        assertThat(NpcTargeting.usable(empty, false, NOW, 6, 1)).isNull();
        assertThat(NpcTargeting.usable(defended, true, NOW, 6, 1)).isNull();
        assertThat(NpcTargeting.usable(lost, true, NOW, 6, 1)).isNull();
        assertThat(NpcTargeting.usable(intel(NOW, null, null), true, NOW, 6, 1)).isNull();
        assertThat(NpcTargeting.usable(null, true, NOW, 6, 1)).isNull();
    }

    // ---- scouting (§6.3) ----

    @Test
    void scoutsAreTwiceTheScoutsSeenPlusOneAndDoubleWithEachFailure() {
        assertThat(NpcTargeting.scoutCount(null)).isEqualTo(5);
        assertThat(NpcTargeting.scoutCount(intel(NOW, null, null))).isEqualTo(5);
        assertThat(NpcTargeting.scoutCount(intel(NOW, Map.of(), null))).isEqualTo(3);
        assertThat(NpcTargeting.scoutCount(intel(NOW, Map.of(UnitType.SCOUT, 4), null)))
                .isEqualTo(9);
        Intel failedOnce =
                new Intel(NOW, null, Map.of(), 0, null, null, null, false, null, 1, null, null);
        assertThat(NpcTargeting.scoutCount(failedOnce)).isEqualTo(6);
    }

    @Test
    void scoutsTheLordKnowsStandThereCountLikeScoutsSeen() {
        assertThat(NpcTargeting.scoutCount(null, 46)).isEqualTo(93);
        assertThat(NpcTargeting.scoutCount(null, 1)).isEqualTo(5);
        assertThat(NpcTargeting.scoutCount(intel(NOW, Map.of(), null), 1)).isEqualTo(3);
        assertThat(NpcTargeting.scoutCount(intel(NOW, Map.of(UnitType.SCOUT, 4), null), 10))
                .isEqualTo(21);
        assertThat(NpcTargeting.scoutCount(intel(NOW, Map.of(UnitType.SCOUT, 4), null), 2))
                .isEqualTo(9);
    }

    // ---- farming (§6.2) ----

    @Test
    void theExpectedLootIsWhatWasLeftPlusTheProductionSinceCappedAboveTheHidingPlace() {
        Map<BuildingType, Integer> buildings =
                Map.of(
                        BuildingType.WOODCUTTER, 1,
                        BuildingType.CLAY_PIT, 1,
                        BuildingType.IRON_MINE, 1,
                        BuildingType.WAREHOUSE, 1,
                        BuildingType.HIDING_PLACE, 1);
        Intel scouted =
                new Intel(
                        NOW.minus(Duration.ofHours(1)),
                        null,
                        Map.of(),
                        0,
                        buildings,
                        new ResourceAmounts(100, 100, 100),
                        null,
                        false,
                        BattleOutcome.SCOUTS_ONLY,
                        0,
                        null,
                        null);
        int capacity = Storage.warehouseCapacity(1);
        int each =
                (int)
                        Math.max(
                                0,
                                Math.floor(Math.min(capacity, 100 + Production.perHour(1, 1, 1)))
                                        - Storage.hidingCapacity(1));
        assertThat(NpcTargeting.expectedLoot(scouted, NOW, 1)).isEqualTo(3 * each);

        // ten days later it is full: capped at the warehouse
        assertThat(NpcTargeting.expectedLoot(scouted, NOW.plus(Duration.ofDays(10)), 1))
                .isEqualTo(3 * (capacity - Storage.hidingCapacity(1)));
    }

    @Test
    void withoutScoutedResourcesTheLastLootDecidesAndAFirstProbeExpects500() {
        var loot = new ResourceAmounts(100, 100, 100);
        Intel full = new Intel(NOW, null, Map.of(), 0, null, null, loot, true, null, 0, null, null);
        Intel notFull =
                new Intel(NOW, null, Map.of(), 0, null, null, loot, false, null, 0, null, null);
        assertThat(NpcTargeting.expectedLoot(full, NOW, 1)).isEqualTo(450);
        assertThat(NpcTargeting.expectedLoot(notFull, NOW, 1)).isEqualTo(300);
        assertThat(NpcTargeting.expectedLoot(null, NOW, 1)).isEqualTo(500);
    }

    @Test
    void aFarmArmyCarriesTheLootAndGrowsUntilTheEstimateIsAGo() {
        var available = Map.of(UnitType.LIGHT_CAVALRY, 10, UnitType.AXEMAN, 50);
        var order = List.of(UnitType.LIGHT_CAVALRY, UnitType.AXEMAN);

        // 200 to carry: 3 light cavalry (240)
        assertThat(NpcTargeting.farmArmy(DATA, available, order, 200, a -> true))
                .isEqualTo(Map.of(UnitType.LIGHT_CAVALRY, 3));
        // attack 1000 needed: 3 → 6 → 10 light cavalry (1200)
        assertThat(
                        NpcTargeting.farmArmy(
                                DATA,
                                available,
                                order,
                                200,
                                a -> NpcTargeting.attackOf(DATA, a) >= 1000))
                .isEqualTo(Map.of(UnitType.LIGHT_CAVALRY, 10));
        // more than everything: skipped
        assertThat(
                        NpcTargeting.farmArmy(
                                DATA,
                                available,
                                order,
                                200,
                                a -> NpcTargeting.attackOf(DATA, a) >= 100_000))
                .isNull();
        // nothing that carries
        assertThat(NpcTargeting.farmArmy(DATA, Map.of(UnitType.SCOUT, 9), order, 100, a -> true))
                .isNull();
    }

    // ---- conquest (§6.6) ----

    @Test
    void theEscortAttacksWithThreeTimesTheEmptyVillagesDefence() {
        assertThat(NpcTargeting.escortAttackNeeded(0)).isEqualTo(60);
        assertThat(NpcTargeting.escortAttackNeeded(2)).isCloseTo(396, within(1e-9));
        var order = List.of(UnitType.AXEMAN, UnitType.LIGHT_CAVALRY);
        assertThat(NpcTargeting.escort(DATA, Map.of(UnitType.AXEMAN, 50), order, 0))
                .isEqualTo(Map.of(UnitType.AXEMAN, 2));
        assertThat(NpcTargeting.escort(DATA, Map.of(UnitType.AXEMAN, 50), order, 2))
                .isEqualTo(Map.of(UnitType.AXEMAN, 10));
        assertThat(
                        NpcTargeting.escort(
                                DATA,
                                Map.of(UnitType.AXEMAN, 1, UnitType.LIGHT_CAVALRY, 5),
                                order,
                                0))
                .isEqualTo(Map.of(UnitType.AXEMAN, 1, UnitType.LIGHT_CAVALRY, 1));
        assertThat(NpcTargeting.escort(DATA, Map.of(UnitType.AXEMAN, 9), order, 2)).isNull();
    }

    @Test
    void noblesNeededAndTheConquestScore() {
        assertThat(NpcTargeting.noblesNeeded(100)).isEqualTo(4);
        assertThat(NpcTargeting.noblesNeeded(51)).isEqualTo(3);
        assertThat(NpcTargeting.noblesNeeded(25)).isEqualTo(1);
        assertThat(NpcTargeting.noblesNeeded(0)).isEqualTo(1);
        assertThat(NpcTargeting.conquestScore(1000, 10)).isEqualTo(500);
        assertThat(NpcTargeting.conquestScore(1000, 0)).isEqualTo(1000);
    }

    // ---- defence (§6.5) ----

    @Test
    void anArmyFasterThanNineAndAHalfMinutesPerFieldIsOnlyScouts() {
        Instant depart = NOW;
        // 10 fields in 90 minutes = 9 min/field: scouts
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(90 * 60), 10, 1, 1, 0))
                .isTrue();
        // 100 minutes = 10 min/field: light cavalry could be in it
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(100 * 60), 10, 1, 1, 0))
                .isFalse();
        // speed 3: 30 minutes for 10 fields is 9 min/field
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(30 * 60), 10, 3, 1, 0))
                .isTrue();
    }

    /** Code review 1.2, D5: with Haste banners about, the threshold drops by the strongest one. */
    @Test
    void hastedCavalryIsNoLongerTakenForScouts() {
        Instant depart = NOW;
        // light cavalry under a 15 % Haste: 10 fields in 85 minutes, 8.5 min/field
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(85 * 60), 10, 1, 1, 0))
                .isTrue();
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(85 * 60), 10, 1, 1, 0.15))
                .isFalse();
        // hasted scouts: 9 × 0.85 = 7.65 min/field, still scouts
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(459), 1, 1, 1, 0.15))
                .isTrue();
        // unhasted scouts (9 min/field) now count as a threat, the safe side
        assertThat(NpcTargeting.onlyScouts(depart, depart.plusSeconds(90 * 60), 10, 1, 1, 0.15))
                .isFalse();
    }

    @Test
    void theHomeGuardStaysHomeButScoutsAndNoblesDont() {
        assertThat(
                        NpcTargeting.available(
                                Map.of(UnitType.SPEARMAN, 10, UnitType.SCOUT, 5, UnitType.NOBLE, 1),
                                0.4))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 6, UnitType.SCOUT, 5, UnitType.NOBLE, 1));
        assertThat(NpcTargeting.available(Map.of(UnitType.AXEMAN, 1), 0.15)).isEmpty();
        assertThat(
                        NpcTargeting.shareOf(
                                Map.of(UnitType.SPEARMAN, 11, UnitType.AXEMAN, 10),
                                List.of(UnitType.SPEARMAN, UnitType.SWORDSMAN),
                                0.5))
                .isEqualTo(Map.of(UnitType.SPEARMAN, 5));
    }

    // ---- helpers ----

    static MapVillage human(int villagePoints, int playerPoints, int villages) {
        return new MapVillage(
                1, 0, 0, 77L, false, villagePoints, 100, playerPoints, villages, null);
    }

    static MapVillage lord(int villagePoints, int playerPoints) {
        return new MapVillage(2, 0, 0, 78L, true, villagePoints, 100, playerPoints, 2, null);
    }

    static MapVillage barbarian(int points) {
        return new MapVillage(3, 0, 0, null, false, points, 100, 0, 0, null);
    }

    private static Intel intel(Instant at, Map<UnitType, Integer> units, BattleOutcome outcome) {
        return new Intel(at, null, units, 0, null, null, null, false, outcome, 0, null, null);
    }

    private static Intel scouted(BattleReport.ScoutedBanner banner, int hall) {
        return new Intel(
                NOW,
                null,
                Map.of(),
                0,
                Map.of(BuildingType.BANNER_HALL, hall),
                null,
                null,
                false,
                null,
                0,
                null,
                banner);
    }

    /** The real {@code gamedata/banners.yml}, bound like the application does. */
    private static BannerCatalog banners() {
        try {
            var sources =
                    new YamlPropertySourceLoader()
                            .load("banners", new ClassPathResource("gamedata/banners.yml"));
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bind("banners", BannerCatalog.class)
                    .get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
