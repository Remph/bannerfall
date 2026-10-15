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
