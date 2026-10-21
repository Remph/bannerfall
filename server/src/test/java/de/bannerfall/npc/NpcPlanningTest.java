package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcPlanning.Exchange;
import de.bannerfall.npc.NpcPlanning.Kind;
import de.bannerfall.npc.NpcPlanning.Next;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.NobleRules;
import de.bannerfall.rules.Storage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The pure economy decisions of 09-npc-lords §5 and the timing of §3. */
class NpcPlanningTest {

    private static final GameData DATA = TestGameData.load();
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static Map<BuildingType, Integer> start() {
        var levels = new EnumMap<BuildingType, Integer>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, DATA.building(type).startLevel());
        }
        return levels;
    }

    private static List<Map<BuildingType, Integer>> steps() {
        return TestNpcData.templates().of(NpcPersonality.BALANCED).build();
    }

    // ---- the next building ----

    @Test
    void theFirstStepRaisesTheLowestMineFirstAndTiesGoByEnumOrder() {
        Map<BuildingType, Integer> levels = start();

        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.WOODCUTTER);

        levels.put(BuildingType.WOODCUTTER, 2);
        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.CLAY_PIT);

        levels.put(BuildingType.CLAY_PIT, 2);
        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.IRON_MINE);

        levels.put(BuildingType.IRON_MINE, 2);
        // the first step is done: HEADQUARTERS 2
        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.HEADQUARTERS);
    }

    @Test
    void aLevelLostToCatapultsMakesItsStepCurrentAgain() {
        Map<BuildingType, Integer> levels = start();
        levels.put(BuildingType.WOODCUTTER, 2);
        levels.put(BuildingType.CLAY_PIT, 2);
        levels.put(BuildingType.IRON_MINE, 2);
        levels.put(BuildingType.HEADQUARTERS, 2);
        levels.put(BuildingType.WOODCUTTER, 3);
        levels.put(BuildingType.CLAY_PIT, 3);
        levels.put(BuildingType.IRON_MINE, 3);
        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.FARM);

        levels.put(BuildingType.HEADQUARTERS, 1); // damaged

        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isEqualTo(BuildingType.HEADQUARTERS);
    }

    @Test
    void aFullyBuiltVillageIsDone() {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, DATA.building(type).maxLevel());
        }

        assertThat(NpcPlanning.stepCandidate(steps(), levels)).isNull();
        assertThat(NpcPlanning.nextBuild(steps(), levels, levels, DATA, 1000)).isEqualTo(Next.DONE);
    }

    @Test
    void anExpensiveCandidateBecomesAWarehouseUpgrade() {
        // mines at 19, warehouse 4: the next level of a mine costs more than the warehouse holds
        Map<BuildingType, Integer> levels = start();
        levels.put(BuildingType.WOODCUTTER, 19);
        levels.put(BuildingType.CLAY_PIT, 19);
        levels.put(BuildingType.IRON_MINE, 19);
        levels.put(BuildingType.HEADQUARTERS, 5);
        levels.put(BuildingType.FARM, 4);
        levels.put(BuildingType.WAREHOUSE, 4);
        levels.put(BuildingType.BARRACKS, 2);
        levels.put(BuildingType.SMITHY, 1);
        levels.put(BuildingType.HIDING_PLACE, 2);
        List<Map<BuildingType, Integer>> steps =
                List.of(Map.of(BuildingType.WOODCUTTER, 20, BuildingType.CLAY_PIT, 20));
        int capacity = de.bannerfall.rules.Storage.warehouseCapacity(4);
        assertThat(largest(BuildRules.cost(DATA.building(BuildingType.WOODCUTTER), 20)))
                .isGreaterThan(capacity);

        Next next = NpcPlanning.nextBuild(steps, levels, levels, DATA, 1000);

        assertThat(next).isEqualTo(Next.build(BuildingType.WAREHOUSE));
    }

    @Test
    void aQueuedWarehouseUpgradeMakesTheLordWait() {
        Map<BuildingType, Integer> current = start();
        current.put(BuildingType.WOODCUTTER, 19);
        Map<BuildingType, Integer> levels = new EnumMap<>(current);
        levels.put(BuildingType.WAREHOUSE, 2); // queued
        List<Map<BuildingType, Integer>> steps = List.of(Map.of(BuildingType.WOODCUTTER, 20));

        Next next = NpcPlanning.nextBuild(steps, levels, current, DATA, 1000);

        assertThat(next.kind()).isEqualTo(Kind.WAIT);
    }

    @Test
    void withoutRoomOnTheFarmTheFarmComesFirst() {
        Map<BuildingType, Integer> levels = start();
        List<Map<BuildingType, Integer>> steps = List.of(Map.of(BuildingType.WOODCUTTER, 3));
        int extra = BuildRules.popDelta(DATA.building(BuildingType.WOODCUTTER), 2);
        int capacity = de.bannerfall.rules.Population.farmCapacity(1); // 240: 10 % = 24

        assertThat(NpcPlanning.nextBuild(steps, levels, levels, DATA, extra + 24))
                .isEqualTo(Next.build(BuildingType.WOODCUTTER));
        assertThat(NpcPlanning.nextBuild(steps, levels, levels, DATA, extra + 23))
                .isEqualTo(Next.build(BuildingType.FARM));
        assertThat(capacity).isEqualTo(240);

        Map<BuildingType, Integer> queuedFarm = new EnumMap<>(levels);
        queuedFarm.put(BuildingType.FARM, 2);
        assertThat(NpcPlanning.nextBuild(steps, queuedFarm, levels, DATA, extra + 23).kind())
                .isEqualTo(Kind.WAIT);
    }

    @Test
    void theFarmRuleIsSkippedAtTheMaxFarmLevel() {
        Map<BuildingType, Integer> levels = start();
        levels.put(BuildingType.FARM, 30);
        List<Map<BuildingType, Integer>> steps = List.of(Map.of(BuildingType.WOODCUTTER, 3));

        assertThat(NpcPlanning.nextBuild(steps, levels, levels, DATA, 0))
                .isEqualTo(Next.build(BuildingType.WOODCUTTER));
    }

    private static int largest(ResourceAmounts cost) {
        return Math.max(cost.wood(), Math.max(cost.clay(), cost.iron()));
    }

    // ---- quiet hours ----

    @Test
    void aBuildWindowAcrossMidnightHasItsEdges() {
        LocalTime from = LocalTime.of(23, 0);
        LocalTime to = LocalTime.of(7, 0);

        assertThat(quiet("2026-10-07T22:59:00+02:00", from, to)).isFalse();
        assertThat(quiet("2026-10-07T23:00:00+02:00", from, to)).isTrue();
        assertThat(quiet("2026-10-08T02:30:00+02:00", from, to)).isTrue();
        assertThat(quiet("2026-10-08T06:59:59+02:00", from, to)).isTrue();
        assertThat(quiet("2026-10-08T07:00:00+02:00", from, to)).isFalse();
        assertThat(quiet("2026-10-08T12:00:00+02:00", from, to)).isFalse();
    }

    @Test
    void aWindowInsideOneDayAndAWindowOfNoLength() {
        LocalTime from = LocalTime.of(0, 0);
        LocalTime to = LocalTime.of(6, 0);

        assertThat(quiet("2026-10-08T00:00:00+02:00", from, to)).isTrue();
        assertThat(quiet("2026-10-08T05:59:00+02:00", from, to)).isTrue();
        assertThat(quiet("2026-10-08T06:00:00+02:00", from, to)).isFalse();
        assertThat(quiet("2026-10-07T23:59:00+02:00", from, to)).isFalse();
        // from = to: off
        assertThat(quiet("2026-10-08T03:00:00+02:00", from, from)).isFalse();
    }

    @Test
    void theWindowFollowsBerlinTimeAcrossTheSwitchToWinterTime() {
        LocalTime from = LocalTime.of(23, 0);
        LocalTime to = LocalTime.of(7, 0);

        // 2026-10-25: clocks go back at 03:00 (CEST) to 02:00 (CET); 06:30 CET is 05:30 UTC
        assertThat(
                        NpcPlanning.inQuietWindow(
                                Instant.parse("2026-10-25T05:30:00Z"), from, to, BERLIN))
                .isTrue();
        assertThat(
                        NpcPlanning.inQuietWindow(
                                Instant.parse("2026-10-25T06:30:00Z"), from, to, BERLIN))
                .isFalse(); // 07:30 CET
    }

    @Test
    void theLastRoundBeforeTheQuietWindowIsTheOneTheWindowStartsAfter() {
        LocalTime from = LocalTime.of(23, 0);
        LocalTime to = LocalTime.of(7, 0);
        long thirty = 30 * 60;
        // 2026-10-07 is CEST (UTC+2): 22:40 Berlin is 20:40 UTC
        assertThat(lastRound("2026-10-07T22:40:00+02:00", from, to, thirty)).isTrue();
        assertThat(lastRound("2026-10-07T22:30:00+02:00", from, to, thirty)).isTrue(); // edge
        assertThat(lastRound("2026-10-07T22:29:59+02:00", from, to, thirty)).isFalse();
        assertThat(lastRound("2026-10-07T12:00:00+02:00", from, to, thirty)).isFalse();
        // inside the window it is no "last round" (nothing is queued at all)
        assertThat(lastRound("2026-10-07T23:10:00+02:00", from, to, thirty)).isFalse();
        // a window that starts at midnight, seen from the evening before
        assertThat(lastRound("2026-10-07T23:45:00+02:00", LocalTime.MIDNIGHT, to, thirty)).isTrue();
        // from = to is no window
        assertThat(lastRound("2026-10-07T22:59:00+02:00", from, from, thirty)).isFalse();
    }

    @Test
    void theLongestThinkDelayHasTheFullJitter() {
        assertThat(NpcPlanning.maxThinkDelaySeconds(1200, 0.25)).isEqualTo(1500);
        assertThat(NpcPlanning.maxThinkDelaySeconds(401, 0.1)).isEqualTo(442); // 441.1 up
    }

    private static boolean lastRound(
            String isoWithOffset, LocalTime from, LocalTime to, long seconds) {
        return NpcPlanning.quietWindowStartsWithin(
                java.time.OffsetDateTime.parse(isoWithOffset).toInstant(),
                from,
                to,
                BERLIN,
                seconds);
    }

    private static boolean quiet(String isoWithOffset, LocalTime from, LocalTime to) {
        return NpcPlanning.inQuietWindow(
                java.time.OffsetDateTime.parse(isoWithOffset).toInstant(), from, to, BERLIN);
    }

    // ---- round timing ----

    @Test
    void theThinkIntervalShrinksWithTheSpeed() {
        assertThat(NpcPlanning.thinkIntervalSeconds(20, 1)).isEqualTo(1200);
        assertThat(NpcPlanning.thinkIntervalSeconds(20, 3)).isEqualTo(400);
    }

    @Test
    void jitterStaysWithinTheRange() {
        assertThat(NpcPlanning.jitteredSeconds(1200, 0.25, FixedRandomSource.constant(0.0)))
                .isEqualTo(900);
        assertThat(NpcPlanning.jitteredSeconds(1200, 0.25, FixedRandomSource.constant(0.5)))
                .isEqualTo(1200);
        assertThat(NpcPlanning.jitteredSeconds(1200, 0.25, FixedRandomSource.constant(0.999999)))
                .isBetween(1499L, 1500L);
    }

    // ---- recruiting ----

    @Test
    void troopDebtIsTheMissingShare() {
        assertThat(NpcPlanning.troopDebt(0.3, 700, 300))
                .isEqualTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(NpcPlanning.troopDebt(0.3, 1000, 0)).isEqualTo(300.0);
        assertThat(NpcPlanning.troopDebt(0.3, 1000, 500)).isLessThan(0);
    }

    @Test
    void theGreedyPickTakesTheLargestGapAndBreaksTiesByEnumOrder() {
        Map<UnitType, Integer> mix = new EnumMap<>(UnitType.class);
        mix.put(UnitType.SPEARMAN, 50);
        mix.put(UnitType.AXEMAN, 50);

        // nothing owned: the largest share, tie → enum order
        assertThat(NpcPlanning.pickUnit(mix, Map.of(), EnumSet.noneOf(UnitType.class)))
                .isEqualTo(UnitType.SPEARMAN);
        // spearmen are over-represented
        assertThat(
                        NpcPlanning.pickUnit(
                                mix,
                                Map.of(UnitType.SPEARMAN, 100, UnitType.AXEMAN, 20),
                                EnumSet.noneOf(UnitType.class)))
                .isEqualTo(UnitType.AXEMAN);
        // excluded units are not picked
        assertThat(NpcPlanning.pickUnit(mix, Map.of(), EnumSet.of(UnitType.SPEARMAN)))
                .isEqualTo(UnitType.AXEMAN);
        assertThat(
                        NpcPlanning.pickUnit(
                                mix, Map.of(), EnumSet.of(UnitType.SPEARMAN, UnitType.AXEMAN)))
                .isNull();
    }

    @Test
    void repeatedPicksConvergeToTheMix() {
        Map<UnitType, Integer> mix = new EnumMap<>(UnitType.class);
        mix.put(UnitType.SPEARMAN, 30);
        mix.put(UnitType.AXEMAN, 70);
        Map<UnitType, Integer> have = new EnumMap<>(UnitType.class);
        for (int i = 0; i < 400; i++) {
            UnitType pick = NpcPlanning.pickUnit(mix, have, EnumSet.noneOf(UnitType.class));
            have.merge(pick, DATA.unit(pick).pop(), Integer::sum);
        }
        double total = have.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(have.get(UnitType.SPEARMAN) / total).isBetween(0.28, 0.32);
        assertThat(have.get(UnitType.AXEMAN) / total).isBetween(0.68, 0.72);
    }

    @Test
    void aChunkIsOnePercentOfTheFarmButAtLeastOne() {
        assertThat(NpcPlanning.chunk(240, DATA.unit(UnitType.SPEARMAN))).isEqualTo(2);
        assertThat(NpcPlanning.chunk(24, DATA.unit(UnitType.SPEARMAN))).isEqualTo(1);
        assertThat(NpcPlanning.chunk(10_000, DATA.unit(UnitType.SPEARMAN))).isEqualTo(100);
        assertThat(NpcPlanning.chunk(10_000, DATA.unit(UnitType.HEAVY_CAVALRY))).isEqualTo(16);
    }

    @Test
    void reserveAndCapacityHelpers() {
        var stock = new ResourceAmounts(500, 300, 100);

        assertThat(NpcPlanning.minusReserve(stock, new ResourceAmounts(200, 400, 0)))
                .isEqualTo(new ResourceAmounts(300, 0, 100));
        assertThat(NpcPlanning.nearCapacity(stock, 550)).isTrue();
        assertThat(NpcPlanning.nearCapacity(stock, 560)).isFalse();
        assertThat(NpcPlanning.troopPopReserve(240, 7)).isEqualTo(12 + 7);
    }

    // ---- nobles (§5.1, 5) ----

    private static final ResourceAmounts NOBLE = DATA.unit(UnitType.NOBLE).cost();

    @Test
    void aVillageThatWantsNoblesSavesForOneWithTheWarehouseAndFarmRules() {
        var levels = start();
        levels.put(BuildingType.ACADEMY, 1);
        levels.put(BuildingType.WAREHOUSE, 10);
        levels.put(BuildingType.FARM, 20);

        // a warehouse at 10 can't hold the 50,000 clay of a noble
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 10_000, NOBLE))
                .isEqualTo(Next.build(BuildingType.WAREHOUSE));
        // already queued: wait for it
        var queued = new EnumMap<>(levels);
        queued.put(BuildingType.WAREHOUSE, 11);
        assertThat(NpcPlanning.nextNoble(queued, levels, DATA, 10_000, NOBLE).kind())
                .isEqualTo(Kind.WAIT);

        // a warehouse at 20 holds it; a full farm comes first
        levels.put(BuildingType.WAREHOUSE, 20);
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 50, NOBLE))
                .isEqualTo(Next.build(BuildingType.FARM));
        // room for its 100 population and 10 % of the farm: the noble
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 10_000, NOBLE).kind())
                .isEqualTo(Kind.NOBLE);
        // a farm at its max level doesn't stop it
        levels.put(BuildingType.FARM, DATA.building(BuildingType.FARM).maxLevel());
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 50, NOBLE).kind())
                .isEqualTo(Kind.NOBLE);
    }

    @Test
    void aDearerNobleOfALordWithManyVillagesNeedsABiggerWarehouse() {
        var levels = start();
        levels.put(BuildingType.ACADEMY, 1);
        levels.put(BuildingType.WAREHOUSE, 20);
        levels.put(BuildingType.FARM, 20);
        // rank 4 at step 0.25: twice the price, 100,000 clay don't fit a warehouse at 20
        ResourceAmounts dearer = NobleRules.price(NOBLE, 0.25, 4);
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 10_000, dearer))
                .isEqualTo(Next.build(BuildingType.WAREHOUSE));
        // the highest price (7×, 350,000 clay) fits a warehouse at its max level
        levels.put(BuildingType.WAREHOUSE, DATA.building(BuildingType.WAREHOUSE).maxLevel());
        ResourceAmounts highest = NobleRules.price(NOBLE, 1, 100);
        assertThat(NpcPlanning.nextNoble(levels, levels, DATA, 10_000, highest).kind())
                .isEqualTo(Kind.NOBLE);
        assertThat(highest.clay())
                .isLessThanOrEqualTo(
                        Storage.warehouseCapacity(
                                DATA.building(BuildingType.WAREHOUSE).maxLevel()));
    }

    // ---- the market exchange (v1.3, §5.4) ----

    private static final Map<ResourceType, Integer> PLENTY =
            Map.of(ResourceType.WOOD, 5000, ResourceType.CLAY, 5000, ResourceType.IRON, 5000);

    @Test
    void theMarketClosesTheGapWithTheResourceThatHasTheMostToSpare() {
        var cost = new ResourceAmounts(1000, 1000, 1000);
        // clay is 500 short; wood has 1500 to spare, iron none
        assertThat(
                        NpcPlanning.exchangesFor(
                                cost, new ResourceAmounts(2500, 500, 1000), 5000, 1.5, PLENTY))
                .containsExactly(new Exchange(ResourceType.WOOD, ResourceType.CLAY, 500));
        // 2,000 clay from two resources: wood's 1,800 buy 1,200, iron's 1,200 of 1,500 the rest
        assertThat(
                        NpcPlanning.exchangesFor(
                                new ResourceAmounts(0, 2000, 0),
                                new ResourceAmounts(1800, 0, 1500),
                                5000,
                                1.5,
                                PLENTY))
                .containsExactly(
                        new Exchange(ResourceType.WOOD, ResourceType.CLAY, 1200),
                        new Exchange(ResourceType.IRON, ResourceType.CLAY, 800));
        // 1,100 buy 1,000 at 1.1 (counted in decimals)
        assertThat(
                        NpcPlanning.exchangesFor(
                                new ResourceAmounts(0, 1000, 0),
                                new ResourceAmounts(1100, 0, 0),
                                5000,
                                1.1,
                                PLENTY))
                .containsExactly(new Exchange(ResourceType.WOOD, ResourceType.CLAY, 1000));
    }

    @Test
    void noTradeWhenTheStockPaysOrTheGapCannotBeClosedToday() {
        var cost = new ResourceAmounts(1000, 1000, 1000);
        var stock = new ResourceAmounts(2500, 500, 1000);
        // pays already
        assertThat(NpcPlanning.exchangesFor(cost, cost, 5000, 1.5, PLENTY)).isEmpty();
        // trading off in this world
        assertThat(NpcPlanning.exchangesFor(cost, stock, 5000, 0, PLENTY)).isEmpty();
        // only 400 clay left today: half a trade doesn't pay the building
        assertThat(
                        NpcPlanning.exchangesFor(
                                cost,
                                stock,
                                5000,
                                1.5,
                                Map.of(
                                        ResourceType.WOOD,
                                        5000,
                                        ResourceType.CLAY,
                                        400,
                                        ResourceType.IRON,
                                        5000)))
                .isEmpty();
        // too little to spare: 1,400 spare wood buy only 933
        assertThat(
                        NpcPlanning.exchangesFor(
                                cost, new ResourceAmounts(2400, 0, 1000), 5000, 1.5, PLENTY))
                .isEmpty();
        // a part of the cost beyond the warehouse
        assertThat(
                        NpcPlanning.exchangesFor(
                                new ResourceAmounts(0, 6000, 0),
                                new ResourceAmounts(20000, 0, 0),
                                5000,
                                1.5,
                                PLENTY))
                .isEmpty();
    }
}
