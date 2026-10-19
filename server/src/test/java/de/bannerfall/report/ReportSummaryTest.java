package de.bannerfall.report;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleReport.BannerReport;
import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.report.BattleReport.TheftReport;
import de.bannerfall.report.ConquestReport.ConqueredBanner;
import de.bannerfall.village.VillageRefDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The banners the report list tags as gained ({@link ReportSummary#bannersGained}). */
class ReportSummaryTest {

    private static final VillageRefDto BEN = new VillageRefDto(1, "Benhausen", 48, 52, 7L, "Ben");
    private static final VillageRefDto TOM = new VillageRefDto(2, "Tomburg", 50, 50, 8L, "Tom");
    private static final ScoutedBanner WAR_2 = new ScoutedBanner(BannerType.WAR, 2);
    private static final ScoutedBanner HARVEST_1 = new ScoutedBanner(BannerType.HARVEST, 1);

    @Test
    void aBattleWithoutBannersGainsNone() {
        // also every report from before banners existed
        assertThat(battle(null).summary(ReportKind.ATTACK).bannersGained()).isEmpty();
    }

    @Test
    void aFoundAndAStolenBannerBothCount() {
        BannerReport banners =
                new BannerReport(
                        0.1,
                        null,
                        HARVEST_1,
                        new TheftReport(5, 5, 0.14, TheftResult.STOLEN, WAR_2),
                        null);

        assertThat(battle(banners).summary(ReportKind.ATTACK).bannersGained())
                .containsExactly(HARVEST_1, WAR_2);
    }

    @Test
    void aFailedTheftOrALostBannerGainsNothing() {
        BannerReport failed =
                new BannerReport(
                        null,
                        null,
                        null,
                        new TheftReport(5, 5, 0.14, TheftResult.FAILED, WAR_2),
                        null);
        BannerReport stolenFromMe = new BannerReport(null, null, null, null, WAR_2);

        assertThat(battle(failed).summary(ReportKind.ATTACK).bannersGained()).isEmpty();
        assertThat(battle(stolenFromMe).summary(ReportKind.DEFENSE).bannersGained()).isEmpty();
    }

    @Test
    void onlyTheConquerorGainsTheBannerOfAConqueredVillage() {
        ConquestReport toStash =
                new ConquestReport(
                        TOM, BEN, "Tomburg", new ConqueredBanner(BannerType.WAR, 2, true));

        assertThat(toStash.summary(ReportKind.CONQUEST).bannersGained()).containsExactly(WAR_2);
        assertThat(toStash.summary(ReportKind.VILLAGE_LOST).bannersGained()).isEmpty();
    }

    @Test
    void aBannerLeftAsleepInTheVillageIsNotGained() {
        ConquestReport asleep =
                new ConquestReport(
                        TOM, BEN, "Tomburg", new ConqueredBanner(BannerType.WAR, 2, false));

        assertThat(asleep.summary(ReportKind.CONQUEST).bannersGained()).isEmpty();
    }

    @Test
    void aBattleShowsTheLootAndTheReadersOwnLosses() {
        var defenders =
                new BattleReport.DefenderSide(
                        Map.of(UnitType.SPEARMAN, 40),
                        Map.of(UnitType.SPEARMAN, 40),
                        List.of(),
                        2,
                        2,
                        null,
                        null,
                        Map.of());
        BattleReport report = battle(new ResourceAmounts(150, 425, 425), defenders, null);

        ReportSummary attacker = report.summary(ReportKind.ATTACK);
        ReportSummary defender = report.summary(ReportKind.DEFENSE);

        assertThat(attacker.loot()).isEqualTo(new ResourceAmounts(150, 425, 425));
        assertThat(attacker.losses()).isEqualTo(Map.of(UnitType.AXEMAN, 3));
        assertThat(defender.loot()).isEqualTo(new ResourceAmounts(150, 425, 425));
        assertThat(defender.losses()).isEqualTo(Map.of(UnitType.SPEARMAN, 40));
        // the attacker who saw nothing of the defenders: his own losses only
        assertThat(battle(ResourceAmounts.ZERO, null, null).summary(ReportKind.DEFENSE).losses())
                .isEmpty();
    }

    @Test
    void fullHaulOnlyWhenTheSurvivorsCarriedAllTheyCould() {
        var full = new ResourceAmounts(500, 500, 500);

        assertThat(battle(full, null, 1500).summary(ReportKind.ATTACK).fullHaul()).isTrue();
        assertThat(battle(full, null, 1600).summary(ReportKind.ATTACK).fullHaul()).isFalse();
        // the defenders' copy never says it; reports from before 1.3 have no capacity
        assertThat(battle(full, null, 1500).summary(ReportKind.DEFENSE).fullHaul()).isFalse();
        assertThat(battle(full, null, null).summary(ReportKind.ATTACK).fullHaul()).isFalse();
        assertThat(battle(ResourceAmounts.ZERO, null, 0).summary(ReportKind.ATTACK).fullHaul())
                .isFalse();
    }

    @Test
    void anAttackWithNoblesIsMarkedInEveryCopy() {
        BattleReport withNobles =
                new BattleReport(
                        BEN,
                        TOM,
                        BattleOutcome.ATTACKER_WON,
                        0.0,
                        Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 1),
                        Map.of(UnitType.NOBLE, 1),
                        null,
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        null);

        assertThat(withNobles.summary(ReportKind.ATTACK).nobles()).isTrue();
        assertThat(withNobles.summary(ReportKind.DEFENSE).nobles()).isTrue();
        assertThat(battle(null).summary(ReportKind.ATTACK).nobles()).isFalse();
        assertThat(
                        new ConquestReport(TOM, BEN, "Tomburg", null)
                                .summary(ReportKind.CONQUEST)
                                .nobles())
                .isFalse();
    }

    @Test
    void reportsThatArentBattlesHaveNoLootOrLosses() {
        ConquestReport conquest = new ConquestReport(TOM, BEN, "Tomburg", null);
        BattleReport scouts =
                new BattleReport(
                        BEN,
                        TOM,
                        BattleOutcome.SCOUTS_ONLY,
                        null,
                        Map.of(UnitType.SCOUT, 5),
                        Map.of(UnitType.SCOUT, 2),
                        null,
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        null);

        assertThat(conquest.summary(ReportKind.CONQUEST).loot()).isNull();
        assertThat(conquest.summary(ReportKind.CONQUEST).losses()).isEmpty();
        assertThat(scouts.summary(ReportKind.SCOUT).loot()).isNull();
        assertThat(scouts.summary(ReportKind.SCOUT).losses()).isEqualTo(Map.of(UnitType.SCOUT, 2));
    }

    private static BattleReport battle(
            ResourceAmounts loot, BattleReport.DefenderSide defenders, Integer haulCapacity) {
        return new BattleReport(
                BEN,
                TOM,
                BattleOutcome.ATTACKER_WON,
                0.0,
                Map.of(UnitType.AXEMAN, 100),
                Map.of(UnitType.AXEMAN, 3),
                Map.of(),
                null,
                defenders,
                null,
                loot,
                null,
                null,
                haulCapacity);
    }

    private static BattleReport battle(BannerReport banners) {
        return new BattleReport(
                BEN,
                TOM,
                BattleOutcome.ATTACKER_WON,
                0.0,
                Map.of(UnitType.AXEMAN, 100),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                ResourceAmounts.ZERO,
                null,
                banners);
    }
}
