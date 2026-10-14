package de.bannerfall.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.TheftResult;
import de.bannerfall.rules.BattleInput;
import de.bannerfall.rules.BattleResult;
import de.bannerfall.rules.Combat;
import de.bannerfall.village.VillageRefDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Who sees what of a battle (02-game-rules §14), with real battles from {@link Combat}. */
class BattleReportsTest {

    private static final GameData DATA = TestGameData.load();

    private static final VillageRefDto ATTACKER = new VillageRefDto(1, "A", 10, 10, 1L, "Anna");
    private static final VillageRefDto DEFENDER = new VillageRefDto(2, "D", 20, 20, 2L, "Dirk");
    private static final VillageRefDto SUPPORTER = new VillageRefDto(3, "S", 30, 30, 3L, "Sven");
    private static final ResourceAmounts STOCK = new ResourceAmounts(300, 2000, 2000);
    private static final BattleReport.ScoutedBanner BANNER =
            new BattleReport.ScoutedBanner(BannerType.WAR, 2);
    private static final Map<BuildingType, Integer> BUILDINGS =
            Map.of(BuildingType.HEADQUARTERS, 5, BuildingType.WALL, 2);

    private static final BattleReport.LoyaltyReport SEEN_LOYALTY =
            new BattleReport.LoyaltyReport(70, 70, false);

    private static BattleReports.Battle battle(
            Map<UnitType, Integer> attacker, List<Map<UnitType, Integer>> stacks) {
        return battle(attacker, stacks, null, 0);
    }

    private static BattleReports.Battle battle(
            Map<UnitType, Integer> attacker,
            List<Map<UnitType, Integer>> stacks,
            BuildingType catapultTarget,
            int catapultLevel) {
        BattleInput input =
                new BattleInput(attacker, stacks, 2, catapultTarget, catapultLevel, 0.1);
        BattleResult result = Combat.fight(DATA, input);
        List<VillageRefDto> homes =
                stacks.size() == 1 ? List.of(DEFENDER) : List.of(DEFENDER, SUPPORTER);
        return new BattleReports.Battle(
                ATTACKER,
                DEFENDER,
                input,
                result,
                homes,
                STOCK,
                BUILDINGS,
                BANNER,
                new ResourceAmounts(1, 2, 3),
                null,
                SEEN_LOYALTY);
    }

    @Test
    void whenNoAttackerSurvivesHisReportShowsOnlyHisOwnSide() {
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 20, UnitType.SCOUT, 50),
                        List.of(Map.of(UnitType.SWORDSMAN, 100)));
        assertThat(battle.result().scouting().succeeded()).isTrue();

        BattleReport report = BattleReports.forAttacker(battle);

        assertThat(BattleReports.attackerKind(battle.result())).isEqualTo(ReportKind.ATTACK);
        assertThat(report.outcome()).isEqualTo(BattleOutcome.DEFENDER_WON);
        assertThat(report.attackerLosses())
                .isEqualTo(Map.of(UnitType.AXEMAN, 20, UnitType.SCOUT, 50));
        // dead scouts tell nothing, even though they outnumbered the defending ones
        assertThat(report.defenderSide()).isNull();
        assertThat(report.scouting()).isNull();
        assertThat(report.luck()).isEqualTo(0.1);
        // nobody came back, so the attacker learned nothing about the loyalty either
        assertThat(report.loyalty()).isNull();
    }

    @Test
    void theDefendersAlwaysSeeEverythingButTheScoutedData() {
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 20, UnitType.SCOUT, 50),
                        List.of(Map.of(UnitType.SWORDSMAN, 100)));

        BattleReport report = BattleReports.forDefenders(battle);

        assertThat(report.attackerUnits())
                .isEqualTo(Map.of(UnitType.AXEMAN, 20, UnitType.SCOUT, 50));
        assertThat(report.attackerLosses()).isEqualTo(report.attackerUnits());
        assertThat(report.defenderSide().units()).isEqualTo(Map.of(UnitType.SWORDSMAN, 100));
        assertThat(report.scouting())
                .isEqualTo(new BattleReport.ScoutingReport(true, null, null, null));
        assertThat(report.loot()).isEqualTo(new ResourceAmounts(1, 2, 3));
    }

    @Test
    void survivingScoutsBringTheScoutedDataHome() {
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 5),
                        List.of(Map.of(UnitType.SPEARMAN, 50), Map.of(UnitType.SPEARMAN, 10)));

        BattleReport report = BattleReports.forAttacker(battle);

        assertThat(report.outcome()).isEqualTo(BattleOutcome.ATTACKER_WON);
        // the attacker who came back knows the loyalty; the defenders' copy only has what nobles
        // did
        assertThat(report.loyalty()).isEqualTo(SEEN_LOYALTY);
        assertThat(BattleReports.forDefenders(battle).loyalty()).isNull();
        assertThat(report.scouting())
                .isEqualTo(new BattleReport.ScoutingReport(true, STOCK, BUILDINGS, BANNER));
        assertThat(report.defenderSide().units()).isEqualTo(Map.of(UnitType.SPEARMAN, 60));
        assertThat(report.defenderSide().losses()).isEqualTo(Map.of(UnitType.SPEARMAN, 60));
    }

    @Test
    void theAttackerSeesTheTotalsButNotWhoSupports() {
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 100),
                        List.of(Map.of(UnitType.SPEARMAN, 50), Map.of(UnitType.SWORDSMAN, 10)));

        BattleReport.DefenderSide attackerView = BattleReports.forAttacker(battle).defenderSide();
        BattleReport.DefenderSide defenderView = BattleReports.forDefenders(battle).defenderSide();

        assertThat(attackerView.units())
                .isEqualTo(Map.of(UnitType.SPEARMAN, 50, UnitType.SWORDSMAN, 10));
        assertThat(attackerView.losses()).isEqualTo(defenderView.losses());
        assertThat(attackerView.stacks()).isEmpty();
        assertThat(defenderView.stacks())
                .extracting(BattleReport.DefenderStack::home)
                .containsExactly(DEFENDER, SUPPORTER);
    }

    @Test
    void aSurvivorWithoutSuccessfulScoutingShowsTheDefendersButNoScoutedData() {
        // 2 attacking scouts against 3 defending ones: scouting fails, the axemen win
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 2),
                        List.of(Map.of(UnitType.SPEARMAN, 10, UnitType.SCOUT, 3)));

        BattleReport report = BattleReports.forAttacker(battle);

        assertThat(report.scouting())
                .isEqualTo(new BattleReport.ScoutingReport(false, null, null, null));
        assertThat(report.defenderSide().units())
                .isEqualTo(Map.of(UnitType.SPEARMAN, 10, UnitType.SCOUT, 3));
    }

    @Test
    void scoutsThatAllDieBringNothingHome() {
        var battle = battle(Map.of(UnitType.SCOUT, 3), List.of(Map.of(UnitType.SCOUT, 3)));

        BattleReport report = BattleReports.forAttacker(battle);

        assertThat(BattleReports.attackerKind(battle.result())).isEqualTo(ReportKind.SCOUT);
        assertThat(report.outcome()).isEqualTo(BattleOutcome.SCOUTS_ONLY);
        assertThat(report.luck()).isNull();
        assertThat(report.attackerLosses()).isEqualTo(Map.of(UnitType.SCOUT, 3));
        assertThat(report.defenderSide()).isNull();
        assertThat(report.scouting()).isNull();
        assertThat(BattleReports.forDefenders(battle).scouting())
                .isEqualTo(new BattleReport.ScoutingReport(false, null, null, null));
    }

    @Test
    void stacksWithoutUnitsAreLeftOutAndTheOthersKeepTheirHomes() {
        var battle =
                battle(
                        Map.of(UnitType.AXEMAN, 20),
                        List.of(Map.of(), Map.of(UnitType.SPEARMAN, 100)));

        BattleReport.DefenderSide side = BattleReports.forDefenders(battle).defenderSide();

        assertThat(side.stacks()).hasSize(1);
        assertThat(side.stacks().get(0).home()).isEqualTo(SUPPORTER);
        assertThat(side.stacks().get(0).units()).isEqualTo(Map.of(UnitType.SPEARMAN, 100));
        assertThat(side.stacks().get(0).losses()).isEqualTo(side.losses());
    }

    @Test
    void wallAndCatapultLevelsAreInTheDefenderSide() {
        var withCatapults =
                battle(
                        Map.of(UnitType.AXEMAN, 100, UnitType.CATAPULT, 20),
                        List.of(Map.of()),
                        BuildingType.SMITHY,
                        4);
        var without = battle(Map.of(UnitType.AXEMAN, 100), List.of(Map.of()));

        BattleReport report = BattleReports.forAttacker(withCatapults);
        BattleReport.DefenderSide side = report.defenderSide();
        assertThat(report.catapultTarget()).isEqualTo(BuildingType.SMITHY);
        assertThat(side.wallBefore()).isEqualTo(2);
        assertThat(side.wallAfter()).isEqualTo(2);
        assertThat(side.catapultLevelBefore()).isEqualTo(4);
        // 20 catapults: 4 → 3 (12), 3 → 2 needs 10, only 8 left
        assertThat(side.catapultLevelAfter()).isEqualTo(3);

        BattleReport.DefenderSide noCatapults = BattleReports.forAttacker(without).defenderSide();
        assertThat(BattleReports.forAttacker(without).catapultTarget()).isNull();
        assertThat(noCatapults.catapultLevelBefore()).isNull();
        assertThat(noCatapults.catapultLevelAfter()).isNull();
    }

    @Test
    void theBannerBlockShowsEachSideWhatItMayKnow() {
        var plain =
                battle(
                        Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 5),
                        List.of(Map.of(UnitType.SPEARMAN, 10)));
        var found = new BattleReport.ScoutedBanner(BannerType.HARVEST, 1);
        var theft = new BattleReport.TheftReport(5, 5, 0.13, TheftResult.STOLEN, BANNER);
        var battle =
                new BattleReports.Battle(
                        plain.attacker(),
                        plain.defender(),
                        plain.input(),
                        plain.result(),
                        plain.stackHomes(),
                        plain.stockBefore(),
                        plain.buildingsBefore(),
                        plain.bannerBefore(),
                        plain.loot(),
                        plain.loyalty(),
                        plain.loyaltySeen(),
                        new BattleReports.Banners(0.06, 0.10, found, null, theft));

        // no scouts: the attacker sees his War, his find and his thieves, not the Bulwark
        assertThat(BattleReports.forAttacker(battle).banners())
                .isEqualTo(new BattleReport.BannerReport(0.06, null, found, theft, null));
        // the defenders see both bonuses and what was stolen, not the attacker's find
        assertThat(BattleReports.forDefenders(battle).banners())
                .isEqualTo(new BattleReport.BannerReport(0.06, 0.10, null, null, BANNER));
        // a battle without banners has no block at all
        assertThat(BattleReports.forAttacker(plain).banners()).isNull();
        assertThat(BattleReports.forDefenders(plain).banners()).isNull();
    }

    @Test
    void everyStackNeedsItsHomeVillage() {
        BattleInput input =
                new BattleInput(Map.of(UnitType.AXEMAN, 1), List.of(Map.of()), 0, null, 0, 0);
        BattleResult result = Combat.fight(DATA, input);

        assertThatThrownBy(
                        () ->
                                new BattleReports.Battle(
                                        ATTACKER,
                                        DEFENDER,
                                        input,
                                        result,
                                        List.of(),
                                        STOCK,
                                        BUILDINGS,
                                        null,
                                        ResourceAmounts.ZERO,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
