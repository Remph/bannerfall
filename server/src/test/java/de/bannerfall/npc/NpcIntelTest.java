package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.TestGameData;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.village.VillageRefDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reports into memory and pruning (09-npc-lords §4, §6.3, §6.7). */
class NpcIntelTest {

    private static final GameData DATA = TestGameData.load();
    private static final long LORD = 10;
    private static final long HUMAN = 20;
    private static final Instant AT = Instant.parse("2026-10-07T10:00:00Z");

    private static final VillageRefDto LORD_VILLAGE = new VillageRefDto(1, "L", 0, 0, LORD, "L");
    private static final VillageRefDto TARGET = new VillageRefDto(2, "H", 3, 0, HUMAN, "H");

    @Test
    void aWonAttackWithScoutsTeachesUnitsWallBuildingsAndWhatTheLootLeft() {
        var memory = NpcMemory.empty().draft();
        var report =
                battle(
                        Map.of(UnitType.AXEMAN, 10, UnitType.SCOUT, 5),
                        Map.of(UnitType.AXEMAN, 1),
                        new BattleReport.DefenderSide(
                                Map.of(UnitType.SPEARMAN, 3),
                                Map.of(UnitType.SPEARMAN, 3),
                                List.of(),
                                2,
                                1,
                                null,
                                null),
                        new BattleReport.ScoutingReport(
                                true,
                                new ResourceAmounts(500, 600, 700),
                                Map.of(BuildingType.WALL, 2, BuildingType.WAREHOUSE, 5),
                                null),
                        new ResourceAmounts(45, 45, 45),
                        BattleOutcome.ATTACKER_WON);

        NpcIntel.learn(memory, LORD, ReportKind.ATTACK, AT, report, DATA, 1, 48);

        Intel intel = memory.intel.get(2L);
        assertThat(intel.at()).isEqualTo(AT);
        assertThat(intel.ownerId()).isEqualTo(HUMAN);
        assertThat(intel.units()).isEmpty(); // all three died
        assertThat(intel.showedDefenders()).isFalse();
        assertThat(intel.wall()).isEqualTo(1);
        assertThat(intel.buildings()).containsEntry(BuildingType.WALL, 1);
        assertThat(intel.resources()).isEqualTo(new ResourceAmounts(455, 555, 655));
        // 9 axemen carry 135: the loot of 135 filled them
        assertThat(intel.loot().total()).isEqualTo(135);
        assertThat(intel.lootFull()).isTrue();
        assertThat(intel.outcome()).isEqualTo(BattleOutcome.ATTACKER_WON);
    }

    @Test
    void aSuccessfulScoutRemembersTheBannerUntilTheNextSuccessfulScout() {
        var memory = NpcMemory.empty().draft();
        var bulwark = new BattleReport.ScoutedBanner(BannerType.BULWARK, 2);
        var side =
                new BattleReport.DefenderSide(
                        Map.of(UnitType.SPEARMAN, 3), Map.of(), List.of(), 2, 2, null, null);
        Map<BuildingType, Integer> buildings =
                Map.of(BuildingType.WALL, 2, BuildingType.BANNER_HALL, 2);

        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.ATTACK,
                AT,
                battle(
                        Map.of(UnitType.SCOUT, 5),
                        Map.of(),
                        side,
                        new BattleReport.ScoutingReport(
                                true, ResourceAmounts.ZERO, buildings, bulwark),
                        ResourceAmounts.ZERO,
                        BattleOutcome.SCOUTS_ONLY),
                DATA,
                1,
                48);
        assertThat(memory.intel.get(2L).banner()).isEqualTo(bulwark);
        assertThat(memory.intel.get(2L).buildings()).containsEntry(BuildingType.BANNER_HALL, 2);

        // an attack without scouts learns nothing about the banner: it is kept
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.ATTACK,
                AT.plusSeconds(60),
                battle(
                        Map.of(UnitType.AXEMAN, 10),
                        Map.of(),
                        side,
                        null,
                        ResourceAmounts.ZERO,
                        BattleOutcome.ATTACKER_WON),
                DATA,
                1,
                48);
        assertThat(memory.intel.get(2L).banner()).isEqualTo(bulwark);

        // the next successful scout sees no banner: none flies there any more
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.ATTACK,
                AT.plusSeconds(120),
                battle(
                        Map.of(UnitType.SCOUT, 5),
                        Map.of(),
                        side,
                        new BattleReport.ScoutingReport(
                                true, ResourceAmounts.ZERO, buildings, null),
                        ResourceAmounts.ZERO,
                        BattleOutcome.SCOUTS_ONLY),
                DATA,
                1,
                48);
        assertThat(memory.intel.get(2L).banner()).isNull();
    }

    @Test
    void anAttackNobodyCameBackFromIsALossWithUnknownUnits() {
        var memory = NpcMemory.empty().draft();
        var report =
                battle(
                        Map.of(UnitType.AXEMAN, 10),
                        Map.of(UnitType.AXEMAN, 10),
                        null,
                        null,
                        null,
                        BattleOutcome.DEFENDER_WON);

        NpcIntel.learn(memory, LORD, ReportKind.ATTACK, AT, report, DATA, 1, 48);

        Intel intel = memory.intel.get(2L);
        assertThat(intel.units()).isNull();
        assertThat(intel.outcome()).isEqualTo(BattleOutcome.DEFENDER_WON);
    }

    @Test
    void twoFailedScoutingsLeaveTheVillageAloneForADayDividedBySpeed() {
        var memory = NpcMemory.empty().draft();
        var failed =
                battle(
                        Map.of(UnitType.SCOUT, 5),
                        Map.of(UnitType.SCOUT, 5),
                        null,
                        null,
                        null,
                        BattleOutcome.SCOUTS_ONLY);

        NpcIntel.learn(memory, LORD, ReportKind.SCOUT, AT, failed, DATA, 3, 48);
        assertThat(memory.intel.get(2L).scoutFailures()).isEqualTo(1);
        assertThat(memory.intel.get(2L).leftAloneUntil()).isNull();
        assertThat(NpcTargeting.scoutCount(memory.intel.get(2L))).isEqualTo(10);

        Instant later = AT.plus(Duration.ofHours(1));
        NpcIntel.learn(memory, LORD, ReportKind.SCOUT, later, failed, DATA, 3, 48);
        assertThat(memory.intel.get(2L).scoutFailures()).isEqualTo(2);
        assertThat(memory.intel.get(2L).leftAloneUntil())
                .isEqualTo(later.plus(Duration.ofHours(8)));

        // a scouting that works resets it
        var worked =
                battle(
                        Map.of(UnitType.SCOUT, 20),
                        Map.of(UnitType.SCOUT, 2),
                        new BattleReport.DefenderSide(
                                Map.of(UnitType.SCOUT, 4), Map.of(), List.of(), 0, 0, null, null),
                        new BattleReport.ScoutingReport(
                                true,
                                new ResourceAmounts(1, 2, 3),
                                Map.of(BuildingType.WALL, 0),
                                null),
                        null,
                        BattleOutcome.SCOUTS_ONLY);
        NpcIntel.learn(memory, LORD, ReportKind.SCOUT, later.plusSeconds(60), worked, DATA, 3, 48);
        Intel intel = memory.intel.get(2L);
        assertThat(intel.scoutFailures()).isZero();
        assertThat(intel.leftAloneUntil()).isNull();
        assertThat(intel.units()).isEqualTo(Map.of(UnitType.SCOUT, 4));
        assertThat(NpcTargeting.scoutCount(intel)).isEqualTo(9);
    }

    @Test
    void anAttackOnTheLordIsAGrudgeAndASupportFightingElsewhereIsNot() {
        var memory = NpcMemory.empty().draft();
        var onTheLord =
                new BattleReport(
                        TARGET,
                        LORD_VILLAGE,
                        BattleOutcome.SCOUTS_ONLY,
                        null,
                        Map.of(UnitType.SCOUT, 3),
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null);

        NpcIntel.learn(memory, LORD, ReportKind.DEFENSE, AT, onTheLord, DATA, 2, 48);

        assertThat(memory.grudges)
                .containsEntry(
                        HUMAN, new NpcMemory.Grudge(AT.plus(Duration.ofHours(24)), 2, false, 3));

        var elsewhere =
                new BattleReport(
                        TARGET,
                        new VillageRefDto(5, "X", 9, 9, 99L, "X"),
                        BattleOutcome.DEFENDER_WON,
                        0.0,
                        Map.of(UnitType.AXEMAN, 3),
                        Map.of(UnitType.AXEMAN, 3),
                        null,
                        null,
                        null,
                        null,
                        null);
        var other = NpcMemory.empty().draft();
        NpcIntel.learn(other, LORD, ReportKind.DEFENSE, AT, elsewhere, DATA, 1, 48);
        assertThat(other.grudges).isEmpty();
    }

    @Test
    void theGrudgeKeepsTheMostScoutsThatWentHomeToTheSourceVillage() {
        var memory = NpcMemory.empty().draft();
        NpcIntel.learn(
                memory, LORD, ReportKind.DEFENSE, AT, scoutsOnTheLord(TARGET, 50, 4), DATA, 1, 48);
        assertThat(memory.grudges.get(HUMAN).scouts()).isEqualTo(46);

        // a smaller scouting from the same village: the 46 are still there
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.DEFENSE,
                AT.plusSeconds(60),
                scoutsOnTheLord(TARGET, 10, 0),
                DATA,
                1,
                48);
        assertThat(memory.grudges.get(HUMAN).scouts()).isEqualTo(46);

        // from another village: only what came home there counts
        var other = new VillageRefDto(3, "H2", 6, 0, HUMAN, "H");
        NpcIntel.learn(
                memory,
                LORD,
                ReportKind.DEFENSE,
                AT.plusSeconds(120),
                scoutsOnTheLord(other, 6, 6),
                DATA,
                1,
                48);
        assertThat(memory.grudges.get(HUMAN))
                .satisfies(
                        g -> {
                            assertThat(g.sourceVillageId()).isEqualTo(3);
                            assertThat(g.scouts()).isZero();
                        });
    }

    @Test
    void aLordTakesOverTheActiveGrudgesOfItsGuildMatesNearby() {
        long mate = 11;
        long otherHuman = 21;
        long farHuman = 22;
        var source = new MapVillage(2, 10, 0, HUMAN, false, 300, 100, 900, 2, null);
        var far = new MapVillage(3, 40, 0, farHuman, false, 300, 100, 900, 1, null);
        var moved = new MapVillage(4, 5, 0, otherHuman, false, 300, 100, 900, 1, null);
        Map<Long, MapVillage> villages = Map.of(2L, source, 3L, far, 4L, moved);
        var until = AT.plus(Duration.ofHours(10));
        Map<Long, NpcMemory.Grudge> mates =
                Map.of(
                        HUMAN,
                        new NpcMemory.Grudge(until, 2, true, 7),
                        farHuman,
                        new NpcMemory.Grudge(until, 3, false, 0),
                        // its source village belongs to someone else now
                        otherHuman,
                        new NpcMemory.Grudge(until, 2, false, 0),
                        // a grudge against me or another mate is never taken over
                        LORD,
                        new NpcMemory.Grudge(until, 1, false, 0),
                        12L,
                        new NpcMemory.Grudge(until, 1, false, 0));
        var memory = NpcMemory.empty().draft();

        NpcIntel.shareGrudges(
                memory,
                LORD,
                Set.of(mate, 12L),
                List.of(mates),
                List.of(new Coordinates(0, 0)),
                villages,
                20,
                AT);

        // the same end, source and scouts; not yet retaliated, marked shared
        assertThat(memory.grudges)
                .containsOnlyKeys(HUMAN)
                .containsEntry(HUMAN, new NpcMemory.Grudge(until, 2, false, 7, true));
    }

    @Test
    void aSharedGrudgeIsNeitherPassedOnNorRenewedAndMatesAreForgiven() {
        var source = new MapVillage(2, 10, 0, HUMAN, false, 300, 100, 900, 2, null);
        Map<Long, MapVillage> villages = Map.of(2L, source);
        var memory = NpcMemory.empty().draft();
        var own = new NpcMemory.Grudge(AT.plus(Duration.ofHours(2)), 2, true, 0);
        memory.grudges.put(HUMAN, own);
        memory.grudges.put(11L, new NpcMemory.Grudge(AT.plus(Duration.ofHours(2)), 9, false, 0));
        var newer = new NpcMemory.Grudge(AT.plus(Duration.ofHours(20)), 2, false, 0);

        NpcIntel.shareGrudges(
                memory,
                LORD,
                Set.of(11L),
                List.of(Map.of(HUMAN, newer)),
                List.of(new Coordinates(0, 0)),
                villages,
                20,
                AT);

        // my own grudge stays as it is; the one against my new guild mate is dropped
        assertThat(memory.grudges).containsOnlyKeys(HUMAN).containsEntry(HUMAN, own);

        // a copy is not copied on, and an expired grudge is not taken over
        var other = NpcMemory.empty().draft();
        NpcIntel.shareGrudges(
                other,
                LORD,
                Set.of(11L),
                List.of(
                        Map.of(
                                HUMAN,
                                new NpcMemory.Grudge(
                                        AT.plus(Duration.ofHours(5)), 2, false, 0, true),
                                77L,
                                new NpcMemory.Grudge(AT, 2, false, 0))),
                List.of(new Coordinates(0, 0)),
                villages,
                20,
                AT);
        assertThat(other.grudges).isEmpty();
    }

    private static BattleReport scoutsOnTheLord(VillageRefDto from, int sent, int lost) {
        return new BattleReport(
                from,
                LORD_VILLAGE,
                BattleOutcome.SCOUTS_ONLY,
                null,
                Map.of(UnitType.SCOUT, sent),
                Map.of(UnitType.SCOUT, lost),
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void pruningForgetsWhatIsOldOrNoLongerTrue() {
        Instant now = AT.plus(Duration.ofDays(8));
        var memory = NpcMemory.empty().draft();
        memory.intel.put(2L, intel(now.minus(Duration.ofHours(1)), HUMAN)); // kept
        memory.intel.put(3L, intel(now.minus(Duration.ofHours(1)), HUMAN)); // owner changed
        memory.intel.put(4L, intel(now.minus(Duration.ofDays(7)).minusSeconds(1), null)); // old
        memory.grudges.put(HUMAN, new NpcMemory.Grudge(now, 2, false, 0)); // expires now
        memory.grudges.put(21L, new NpcMemory.Grudge(now.plusSeconds(1), 2, false, 0));
        memory.attacksSent.add(new NpcMemory.SentAttack(HUMAN, now.minus(Duration.ofHours(24))));
        memory.attacksSent.add(
                new NpcMemory.SentAttack(HUMAN, now.minus(Duration.ofHours(24)).plusSeconds(1)));
        memory.farmed.put(2L, now.minus(Duration.ofDays(2)));
        memory.farmed.put(4L, now.minus(Duration.ofHours(1)));
        memory.supported.add(new NpcMemory.Supported(100, 1));
        memory.supported.add(new NpcMemory.Supported(101, 1));
        memory.conquest.put(1L, new NpcMemory.ConquestTarget(4, now.plusSeconds(60))); // kept
        memory.conquest.put(6L, new NpcMemory.ConquestTarget(2, now.plusSeconds(60))); // lost
        memory.conquest.put(7L, new NpcMemory.ConquestTarget(8, now.plusSeconds(60))); // taken

        Map<Long, MapVillage> villages =
                Map.of(
                        1L, village(1, LORD),
                        2L, village(2, HUMAN),
                        3L, village(3, 30L),
                        4L, village(4, null),
                        6L, village(6, HUMAN),
                        7L, village(7, LORD),
                        8L, village(8, LORD));

        NpcIntel.prune(memory, LORD, now, 1, villages, Set.of(101L));

        assertThat(memory.intel).containsOnlyKeys(2L);
        assertThat(memory.grudges).containsOnlyKeys(21L);
        assertThat(memory.attacksSent).hasSize(1);
        assertThat(memory.farmed).containsOnlyKeys(4L);
        assertThat(memory.supported).containsExactly(new NpcMemory.Supported(101, 1));
        assertThat(memory.conquest).containsOnlyKeys(1L);
    }

    @Test
    void memoryWithIntelSurvivesTheJsonColumn() {
        var memory = NpcMemory.empty().draft();
        memory.intel.put(2L, intel(AT, HUMAN));
        memory.grudges.put(HUMAN, new NpcMemory.Grudge(AT, 2, true, 7));
        memory.attacksSent.add(new NpcMemory.SentAttack(HUMAN, AT));
        memory.farmed.put(2L, AT);
        memory.supported.add(new NpcMemory.Supported(5, 1));
        memory.conquest.put(1L, new NpcMemory.ConquestTarget(2, AT));
        NpcMemory built = memory.build();
        var converter = new NpcMemoryConverter();

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(built)))
                .isEqualTo(built);
    }

    // ---- helpers ----

    // ---- losses against the ledger (09 §5.2, balancing round 2) ----

    @Test
    void theLordsAttackLossesComeOffItsTroopSpending() {
        var memory = NpcMemory.empty().draft();
        memory.ledger.put(1L, new NpcMemory.Ledger(100_000, 50_000));
        double axe = DATA.unit(UnitType.AXEMAN).cost().total();
        var report =
                battle(
                        Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 1),
                        Map.of(UnitType.AXEMAN, 40, UnitType.NOBLE, 1),
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        BattleOutcome.DEFENDER_WON);

        NpcIntel.learn(memory, LORD, ReportKind.ATTACK, AT, report, DATA, 1, 48);

        // the noble isn't troop spending, so only the axemen count; building stays
        assertThat(memory.ledger.get(1L))
                .isEqualTo(new NpcMemory.Ledger(100_000, 50_000 - 40 * axe));
    }

    @Test
    void troopSpendingNeverGoesBelowZero() {
        var memory = NpcMemory.empty().draft();
        memory.ledger.put(1L, new NpcMemory.Ledger(100_000, 1_000));
        var report =
                battle(
                        Map.of(UnitType.AXEMAN, 100),
                        Map.of(UnitType.AXEMAN, 100),
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        BattleOutcome.DEFENDER_WON);

        NpcIntel.countLosses(memory, LORD, ReportKind.ATTACK, report, DATA);

        assertThat(memory.ledger.get(1L)).isEqualTo(new NpcMemory.Ledger(100_000, 0));
    }

    @Test
    void defenceLossesCountForEachOfTheLordsOwnStacksOnly() {
        var memory = NpcMemory.empty().draft();
        memory.ledger.put(2L, new NpcMemory.Ledger(80_000, 40_000));
        memory.ledger.put(3L, new NpcMemory.Ledger(60_000, 30_000));
        double spear = DATA.unit(UnitType.SPEARMAN).cost().total();
        var attacked = new VillageRefDto(2, "L2", 3, 0, LORD, "L");
        var ownSupport = new VillageRefDto(3, "L3", 5, 0, LORD, "L");
        var humanSupport = new VillageRefDto(4, "H2", 6, 0, HUMAN, "H");
        var side =
                new BattleReport.DefenderSide(
                        Map.of(UnitType.SPEARMAN, 70),
                        Map.of(UnitType.SPEARMAN, 70),
                        List.of(
                                new BattleReport.DefenderStack(
                                        attacked,
                                        Map.of(UnitType.SPEARMAN, 50),
                                        Map.of(UnitType.SPEARMAN, 50)),
                                new BattleReport.DefenderStack(
                                        ownSupport,
                                        Map.of(UnitType.SPEARMAN, 10),
                                        Map.of(UnitType.SPEARMAN, 10)),
                                new BattleReport.DefenderStack(
                                        humanSupport,
                                        Map.of(UnitType.SPEARMAN, 10),
                                        Map.of(UnitType.SPEARMAN, 10))),
                        2,
                        2,
                        null,
                        null);
        var report =
                new BattleReport(
                        new VillageRefDto(9, "H", 9, 9, HUMAN, "H"),
                        attacked,
                        BattleOutcome.ATTACKER_WON,
                        0.0,
                        Map.of(UnitType.AXEMAN, 500),
                        Map.of(UnitType.AXEMAN, 20),
                        null,
                        side,
                        null,
                        ResourceAmounts.ZERO,
                        null);

        NpcIntel.countLosses(memory, LORD, ReportKind.DEFENSE, report, DATA);

        assertThat(memory.ledger.get(2L).troops()).isEqualTo(40_000 - 50 * spear);
        assertThat(memory.ledger.get(3L).troops()).isEqualTo(30_000 - 10 * spear);
        // the human's support and the attacker's losses are none of the lord's spending
        assertThat(memory.ledger).doesNotContainKeys(4L, 9L);
    }

    @Test
    void aVillageWithoutALedgerIsLeftAlone() {
        var memory = NpcMemory.empty().draft();
        var report =
                battle(
                        Map.of(UnitType.AXEMAN, 10),
                        Map.of(UnitType.AXEMAN, 10),
                        null,
                        null,
                        ResourceAmounts.ZERO,
                        BattleOutcome.DEFENDER_WON);

        NpcIntel.countLosses(memory, LORD, ReportKind.ATTACK, report, DATA);

        assertThat(memory.ledger).isEmpty();
    }

    private static BattleReport battle(
            Map<UnitType, Integer> units,
            Map<UnitType, Integer> losses,
            BattleReport.DefenderSide side,
            BattleReport.ScoutingReport scouting,
            ResourceAmounts loot,
            BattleOutcome outcome) {
        return new BattleReport(
                LORD_VILLAGE,
                TARGET,
                outcome,
                outcome == BattleOutcome.SCOUTS_ONLY ? null : 0.0,
                units,
                losses,
                null,
                side,
                scouting,
                loot,
                null);
    }

    private static Intel intel(Instant at, Long owner) {
        return new Intel(
                at,
                owner,
                Map.of(UnitType.SPEARMAN, 1),
                3,
                Map.of(BuildingType.WALL, 3),
                new ResourceAmounts(1, 2, 3),
                new ResourceAmounts(4, 5, 6),
                true,
                BattleOutcome.ATTACKER_WON,
                1,
                null,
                new BattleReport.ScoutedBanner(BannerType.BULWARK, 2));
    }

    private static MapVillage village(long id, Long owner) {
        return new MapVillage(id, 0, 0, owner, false, 100, 100, 100, 1, null);
    }
}
