package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcBanners.Fusion;
import de.bannerfall.npc.NpcBanners.Held;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.research.ResearchOrder;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rules of lord banners (09-npc-lords §14): choosing, fusing, thieves, paladin, training. */
class NpcBannersTest {

    private static final GameData DATA = TestGameData.load();
    private static final NpcTemplates TEMPLATES = TestNpcData.templates();
    private static final NpcTemplates.Template OFFENSIVE = TEMPLATES.of(NpcPersonality.OFFENSIVE);
    private static final NpcTemplates.Template DEFENSIVE = TEMPLATES.of(NpcPersonality.DEFENSIVE);
    private static final WorldSettings ON =
            WorldSettings.builder()
                    .bannersEnabled(true)
                    .npcBannersEnabled(true)
                    .paladinMaxSlots(5)
                    .build();
    private static final WorldSettings OFF = ON.toBuilder().npcBannersEnabled(false).build();
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    // ---- choosing ----

    @Test
    void theHighestEffectiveTierFlies() {
        Held war1 = new Held(1, BannerType.WAR, 1);
        Held harvest3 = new Held(2, BannerType.HARVEST, 3);

        // hall 3: tier III beats the favourite at tier I
        assertThat(NpcBanners.best(war1, List.of(harvest3), false, 3, 1, OFFENSIVE))
                .contains(harvest3);
    }

    @Test
    void theHallCapsTheTierThenTheFavouriteDecides() {
        Held war1 = new Held(1, BannerType.WAR, 1);
        Held harvest3 = new Held(2, BannerType.HARVEST, 3);

        // hall 1: both work at tier I, War is the OFFENSIVE favourite
        assertThat(NpcBanners.best(war1, List.of(harvest3), false, 1, 1, OFFENSIVE)).contains(war1);
        // the same stash with nothing flying: still War
        assertThat(NpcBanners.best(null, List.of(harvest3, war1), false, 1, 1, OFFENSIVE))
                .contains(war1);
        // DEFENSIVE puts War after Harvest
        assertThat(NpcBanners.best(war1, List.of(harvest3), false, 1, 1, DEFENSIVE))
                .contains(harvest3);
    }

    @Test
    void sameTypeAndTierGoesToTheLowerId() {
        Held a = new Held(7, BannerType.WAR, 2);
        Held b = new Held(4, BannerType.WAR, 2);

        assertThat(NpcBanners.best(null, List.of(a, b), false, 3, 1, OFFENSIVE)).contains(b);
    }

    @Test
    void bannersAndStandardsChooseApart() {
        Held war = new Held(1, BannerType.WAR, 1);
        Held axe = new Held(2, BannerType.AXE_STANDARD, 1);

        assertThat(NpcBanners.best(null, List.of(war, axe), false, 3, 1, OFFENSIVE)).contains(war);
        assertThat(NpcBanners.best(null, List.of(war, axe), true, 3, 1, OFFENSIVE)).contains(axe);
        assertThat(NpcBanners.best(null, List.of(war), true, 3, 1, OFFENSIVE)).isEmpty();
    }

    @Test
    void thePaladinLevelCapsAStandard() {
        Held axe1 = new Held(1, BannerType.AXE_STANDARD, 1);
        Held spear3 = new Held(2, BannerType.SPEAR_STANDARD, 3);

        // paladin level 1: both at tier I, Axe is the OFFENSIVE favourite
        assertThat(NpcBanners.best(axe1, List.of(spear3), true, 3, 1, OFFENSIVE)).contains(axe1);
        // paladin level 3: the tier III standard works at III
        assertThat(NpcBanners.best(axe1, List.of(spear3), true, 3, 3, OFFENSIVE)).contains(spear3);
    }

    // ---- fusing ----

    @Test
    void theThreeLeastFavouriteFuseIntoTheBestOfThem() {
        List<Held> stash =
                List.of(
                        new Held(1, BannerType.WAR, 1),
                        new Held(2, BannerType.OATH, 1),
                        new Held(3, BannerType.HARVEST, 1),
                        new Held(4, BannerType.BULWARK, 1));

        // OFFENSIVE: Oath (8), Bulwark (7), Harvest (4) are the least favourite; Harvest is best
        assertThat(NpcBanners.nextFusion(stash, OFFENSIVE, 3))
                .contains(new Fusion(List.of(2L, 4L, 3L), BannerType.HARVEST));
    }

    @Test
    void familiesAndTiersFuseApart() {
        List<Held> stash =
                List.of(
                        new Held(1, BannerType.WAR, 1),
                        new Held(2, BannerType.WAR, 1),
                        new Held(3, BannerType.AXE_STANDARD, 1),
                        new Held(4, BannerType.WAR, 2),
                        new Held(5, BannerType.WAR, 3),
                        new Held(6, BannerType.WAR, 3),
                        new Held(7, BannerType.WAR, 3));

        assertThat(NpcBanners.nextFusion(stash, OFFENSIVE, 3)).isEmpty();
    }

    @Test
    void tierOneFusesFirstThenStandards() {
        List<Held> banners =
                List.of(
                        new Held(1, BannerType.WAR, 2),
                        new Held(2, BannerType.WAR, 2),
                        new Held(3, BannerType.WAR, 2),
                        new Held(4, BannerType.AXE_STANDARD, 1),
                        new Held(5, BannerType.SIEGE_STANDARD, 1),
                        new Held(6, BannerType.RAM_STANDARD, 1));

        assertThat(NpcBanners.nextFusion(banners, OFFENSIVE, 3))
                .contains(new Fusion(List.of(5L, 6L, 4L), BannerType.AXE_STANDARD));
    }

    // ---- thieves and the paladin ----

    @Test
    void thievesFillUpToTheTemplateWithAHall() {
        assertThat(NpcBanners.thievesWanted(OFFENSIVE, ON, 1, 3)).isEqualTo(7);
        assertThat(NpcBanners.thievesWanted(OFFENSIVE, ON, 1, 12)).isZero();
        assertThat(NpcBanners.thievesWanted(OFFENSIVE, ON, 0, 0)).isZero();
        assertThat(NpcBanners.thievesWanted(OFFENSIVE, OFF, 1, 0)).isZero();
        assertThat(NpcBanners.thievesWanted(DEFENSIVE, ON, 3, 0)).isZero();
    }

    @Test
    void thievesGoOnlyWhereTheIntelSawABanner() {
        Map<UnitType, Integer> home = Map.of(UnitType.BANNER_THIEF, 10, UnitType.AXEMAN, 100);
        Intel withBanner = intel(new ScoutedBanner(BannerType.HARVEST, 1));

        assertThat(NpcBanners.retaliationExtras(OFFENSIVE, ON, withBanner, home))
                .containsExactlyEntriesOf(Map.of(UnitType.BANNER_THIEF, 10));
        assertThat(NpcBanners.retaliationExtras(OFFENSIVE, ON, intel(null), home)).isEmpty();
        assertThat(NpcBanners.retaliationExtras(OFFENSIVE, OFF, withBanner, home)).isEmpty();
    }

    @Test
    void thePaladinJoinsForOffensiveAndBalancedOnly() {
        Map<UnitType, Integer> home = Map.of(UnitType.PALADIN, 1, UnitType.BANNER_THIEF, 0);

        assertThat(NpcBanners.retaliationExtras(OFFENSIVE, ON, intel(null), home))
                .containsExactlyEntriesOf(Map.of(UnitType.PALADIN, 1));
        assertThat(
                        NpcBanners.retaliationExtras(
                                TEMPLATES.of(NpcPersonality.BALANCED), ON, intel(null), home))
                .containsKey(UnitType.PALADIN);
        assertThat(NpcBanners.retaliationExtras(DEFENSIVE, ON, intel(null), home)).isEmpty();
        assertThat(NpcBanners.retaliationExtras(OFFENSIVE, OFF, intel(null), home)).isEmpty();
        assertThat(
                        NpcBanners.retaliationExtras(
                                OFFENSIVE,
                                ON.toBuilder().paladinMaxSlots(0).build(),
                                intel(null),
                                home))
                .isEmpty();
    }

    // ---- Hall training ----

    @Test
    void thePaladinTrainsUpToTheHallLevel() {
        NpcBanners rules = rules();

        assertThat(rules.paladinLevelToTrain(ON, 1, 1, List.of(), NOW)).isZero();
        assertThat(rules.paladinLevelToTrain(ON, 1, 2, List.of(), NOW)).isEqualTo(2);
        assertThat(rules.paladinLevelToTrain(ON, 1, 3, List.of(), NOW)).isEqualTo(2);
        List<ResearchOrder> level2 = List.of(done(UnitType.PALADIN, 2, 1));
        assertThat(rules.paladinLevelToTrain(ON, 1, 2, level2, NOW)).isZero();
        assertThat(rules.paladinLevelToTrain(ON, 1, 3, level2, NOW)).isEqualTo(3);
        List<ResearchOrder> level3 =
                List.of(done(UnitType.PALADIN, 2, 1), done(UnitType.PALADIN, 3, 1));
        assertThat(rules.paladinLevelToTrain(ON, 1, 3, level3, NOW)).isZero();
        assertThat(rules.paladinLevelToTrain(OFF, 1, 3, List.of(), NOW)).isZero();
    }

    @Test
    void oneTrainingAtATime() {
        NpcBanners rules = rules();

        // the paladin trains in another village: not twice
        assertThat(
                        rules.paladinLevelToTrain(
                                ON, 1, 3, List.of(running(UnitType.PALADIN, 2, 9)), NOW))
                .isZero();
        // this village's hall trains thieves
        assertThat(
                        rules.paladinLevelToTrain(
                                ON, 1, 3, List.of(running(UnitType.BANNER_THIEF, 2, 1)), NOW))
                .isZero();
        // thieves in another village's hall don't block it
        assertThat(
                        rules.paladinLevelToTrain(
                                ON, 1, 3, List.of(running(UnitType.BANNER_THIEF, 2, 9)), NOW))
                .isEqualTo(2);
    }

    private static NpcBanners rules() {
        return new NpcBanners(null, null, null, null, null, null, DATA);
    }

    private static ResearchOrder done(UnitType unit, int level, long villageId) {
        return ResearchOrder.start(
                1, villageId, unit, level, 60, ResourceAmounts.ZERO, NOW.minusSeconds(3600));
    }

    private static ResearchOrder running(UnitType unit, int level, long villageId) {
        return ResearchOrder.start(
                1, villageId, unit, level, 7200, ResourceAmounts.ZERO, NOW.minusSeconds(60));
    }

    private static Intel intel(ScoutedBanner banner) {
        return new Intel(NOW, 2L, Map.of(), 0, Map.of(), null, null, false, null, 0, null, banner);
    }
}
