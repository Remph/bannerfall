package de.bannerfall.security;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M14e: the same thing twice at the same moment, against the real PostgreSQL. Whatever wins, the
 * database ends in a legal state: nothing negative, no units made or lost, every open order and
 * movement with its event. Each race runs a few rounds, the requests released together by a latch.
 */
@IntegrationTest
class RaceTest {

    private static final int ROUNDS = 4;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final List<Long> extraVillages = new ArrayList<>();

    /** The response bodies of the last {@link #together} call, for failure messages. */
    private final List<String> lastBodies = new ArrayList<>();

    private TestPlayer alice;
    private TestPlayer bob;
    private Coordinates bobAt;
    private String aliceName;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        alice = TestPlayer.register(mockMvc);
        bob = TestPlayer.register(mockMvc);
        bobAt = Fixture.coordinates(bob);
        aliceName = Fixture.username(jdbc, alice.playerId);
        Fixture.richVillage(jdbc, alice.villageId);
        Fixture.richVillage(jdbc, bob.villageId);
        Fixture.endProtection(jdbc, bob.playerId, clock.instant().minusSeconds(1));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        jdbc.sql("delete from game_event").update();
        for (long id : extraVillages) {
            jdbc.sql("delete from unit_stack where home_village_id = ? or location_village_id = ?")
                    .params(id, id)
                    .update();
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        extraVillages.clear();
        jdbc.sql(
                        """
                        update world_settings set cancel_window_seconds = 600,
                               paladin_villages_per_slot = 4, paladin_max_slots = 5,
                               unit_levels_enabled = false
                        where id = 1
                        """)
                .update();
        worldService.reload();
    }

    @Test
    void theSameUnitsSentTwiceLeaveOnce() throws Exception {
        Browser second = secondSession();
        for (int round = 0; round < ROUNDS; round++) {
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);
            String command = commandJson("ATTACK", bobAt, "{\"SPEARMAN\":100}");
            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(alice.villagePath() + "/commands", command),
                            () -> second.post(alice.villagePath() + "/commands", command));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(homeSpearmen()).isZero();
            assertThat(spearmenOnTheWay()).isEqualTo(100 * (round + 1));
        }
    }

    @Test
    void theSameUnitsDismissedTwiceLeaveTheHomeStackOnce() throws Exception {
        Browser second = secondSession();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from dismiss_order where village_id = ?")
                    .param(alice.villageId)
                    .update();
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);
            String order = "{\"unit\":\"SPEARMAN\",\"count\":100}";

            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.post(
                                            alice.villagePath() + "/dismiss-orders", order),
                            () -> second.post(alice.villagePath() + "/dismiss-orders", order));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(homeSpearmen()).isZero();
            // one order of 100: nothing was made or lost twice
            assertThat(
                            jdbc.sql(
                                            "select coalesce(sum(count - completed_count), 0)"
                                                    + " from dismiss_order where village_id = ?")
                                    .param(alice.villageId)
                                    .query(Long.class)
                                    .single())
                    .isEqualTo(100);
        }
    }

    @Test
    void theSameUnitsDismissedAndSentLeaveOnlyOnce() throws Exception {
        Browser second = secondSession();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from dismiss_order where village_id = ?")
                    .param(alice.villageId)
                    .update();
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);
            String send = commandJson("ATTACK", bobAt, "{\"SPEARMAN\":100}");
            String dismiss = "{\"unit\":\"SPEARMAN\",\"count\":100}";

            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(alice.villagePath() + "/commands", send),
                            () -> second.post(alice.villagePath() + "/dismiss-orders", dismiss));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(homeSpearmen()).isZero();
        }
    }

    @Test
    void theSameFarmTargetSentTwiceLeavesTheUnitsOnce() throws Exception {
        Browser second = secondSession();
        for (int round = 0; round < ROUNDS; round++) {
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);
            String farm = alice.villagePath() + "/farm-targets";
            long entry = farmOk(bobAt, "{\"SPEARMAN\":100}");

            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(farm + "/" + entry + "/send", ""),
                            () -> second.post(farm + "/" + entry + "/send", ""));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(homeSpearmen()).isZero();
            assertThat(spearmenOnTheWay()).isEqualTo(100 * (round + 1));
            alice.browser.delete(farm + "/" + entry).andExpect(status().isNoContent());
        }
    }

    @Test
    void sendAllTwiceAtOnceLeavesTheUnitsOnce() throws Exception {
        Browser second = secondSession();
        String farm = alice.villagePath() + "/farm-targets";
        long entry = farmOk(bobAt, "{\"SPEARMAN\":100}");
        // "send all" leaves players out while the sender is protected
        Fixture.endProtection(jdbc, alice.playerId, clock.instant().minusSeconds(1));
        for (int round = 0; round < ROUNDS; round++) {
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);

            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(farm + "/send-all", ""),
                            () -> second.post(farm + "/send-all", ""));

            // both runs answer: one sent the entry, the other stopped at it
            assertThat(statuses).containsExactly(200, 200);
            assertThat(homeSpearmen()).isZero();
            assertThat(spearmenOnTheWay()).isEqualTo(100 * (round + 1));
        }
        alice.browser.delete(farm + "/" + entry).andExpect(status().isNoContent());
    }

    @Test
    void twoTargetsAddedAtTheLastFreeSlotFillItOnce() throws Exception {
        Browser second = secondSession();
        List<Fixture.Spot> spots = Fixture.barbarianSpots(jdbc, 51);
        try {
            Fixture.fillFarmList(jdbc, alice.villageId, spots.subList(0, 49));
            String farm = alice.villagePath() + "/farm-targets";
            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(farm, addJson(spots.get(49).at())),
                            () -> second.post(farm, addJson(spots.get(50).at())));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(farmEntries()).isEqualTo(50);
        } finally {
            Fixture.removeSpots(jdbc, spots);
        }
    }

    @Test
    void theSameTargetAddedTwiceIsOnTheListOnce() throws Exception {
        Browser second = secondSession();
        String farm = alice.villagePath() + "/farm-targets";
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from farm_target where village_id = ?")
                    .param(alice.villageId)
                    .update();
            List<Integer> statuses =
                    together(
                            () -> alice.browser.post(farm, addJson(bobAt)),
                            () -> second.post(farm, addJson(bobAt)));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(farmEntries()).isEqualTo(1);
        }
    }

    @Test
    void twoRecruitOrdersCantSpendTheSameResources() throws Exception {
        Browser second = secondSession();
        var cost = gameData.unit(UnitType.SPEARMAN).cost();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from recruit_order where village_id = ?")
                    .param(alice.villageId)
                    .update();
            // enough for exactly one order of 10
            jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                    .params(cost.wood() * 15, cost.clay() * 15, cost.iron() * 15, alice.villageId)
                    .update();
            String order = "{\"unit\":\"SPEARMAN\",\"count\":10}";
            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.post(
                                            alice.villagePath() + "/recruit-orders", order),
                            () -> second.post(alice.villagePath() + "/recruit-orders", order));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(resources(alice.villageId)).allMatch(amount -> amount >= 0);
            assertThat(
                            jdbc.sql("select count(*) from recruit_order where village_id = ?")
                                    .param(alice.villageId)
                                    .query(Long.class)
                                    .single())
                    .isEqualTo(1);
        }
    }

    @Test
    void twoRecruitAllRequestsCantSpendTheSameResources() throws Exception {
        Browser second = secondSession();
        var cost = gameData.unit(UnitType.SPEARMAN).cost();
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from recruit_order where village_id = ?")
                    .param(alice.villageId)
                    .update();
            // enough for one "recruit all" of 15, not for two
            jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                    .params(cost.wood() * 20, cost.clay() * 20, cost.iron() * 20, alice.villageId)
                    .update();
            String body = "{\"orders\":[{\"unit\":\"SPEARMAN\",\"count\":15}]}";
            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.post(
                                            alice.villagePath() + "/recruit-orders/all", body),
                            () -> second.post(alice.villagePath() + "/recruit-orders/all", body));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(resources(alice.villageId)).allMatch(amount -> amount >= 0);
        }
    }

    @Test
    void aCancelRacingTheArrivalEndsOneWayOrTheOther() throws Exception {
        allowCancelUntilArrival();
        Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 0);
        for (int round = 0; round < ROUNDS; round++) {
            // Added, not set: a RETURN of an earlier round may already be home again (it walks as
            // long as it was on the way, so it lands before this round's arrival).
            jdbc.sql(
                            "update unit_stack set u_spearman = u_spearman + 10"
                                    + " where home_village_id = ? and location_village_id = ?")
                    .params(alice.villageId, alice.villageId)
                    .update();
            String sent =
                    body(
                            alice.command("SUPPORT", bobAt, "{\"SPEARMAN\":10}")
                                    .andExpect(status().isCreated()));
            long movement = number(sent, "$.id").longValue();
            Instant arrival = Instant.parse(com.jayway.jsonpath.JsonPath.read(sent, "$.arriveAt"));
            clock.set(arrival.minusSeconds(1));

            List<Integer> statuses =
                    together(
                            () -> alice.cancelMovement(movement),
                            () -> {
                                processor.processDue(arrival);
                                return null;
                            });

            boolean cancelled = statuses.getFirst() == 200;
            if (!cancelled) {
                assertThat(statuses.getFirst()).isIn(404, 409);
            }
            // cancelled: walking home; arrived: standing at Bob's, nothing on the way
            assertThat(movementKind(movement)).isEqualTo(cancelled ? "RETURN" : null);
            assertThat(spearmenAt(bob.villageId) - spearmenAtBefore(round))
                    .isEqualTo(cancelled ? 0 : 10);
            assertThat(totalSpearmen()).isEqualTo(10 + 10L * round);
            if (cancelled) {
                assertEveryMovementHasItsEvent();
            }
            // the next round starts from home: what is still walking stays walking
            clock.set(arrival.plusSeconds(1));
            arrivedOrReturning.add(cancelled ? 0 : 10);
        }
    }

    @Test
    void aWithdrawRacingAnArrivalLosesNoUnits() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from unit_stack where home_village_id = ? and location_village_id = ?")
                    .params(alice.villageId, bob.villageId)
                    .update();
            jdbc.sql("delete from movement where home_village_id = ?")
                    .param(alice.villageId)
                    .update();
            jdbc.sql("delete from game_event").update();
            Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 10);
            long stack = Fixture.supportStack(jdbc, alice.villageId, bob.villageId, 10);
            String sent =
                    body(
                            alice.command("SUPPORT", bobAt, "{\"SPEARMAN\":10}")
                                    .andExpect(status().isCreated()));
            Instant arrival = Instant.parse(com.jayway.jsonpath.JsonPath.read(sent, "$.arriveAt"));
            clock.set(arrival);

            List<Integer> statuses =
                    together(
                            () -> alice.returnStack(stack, "{\"SPEARMAN\":10}"),
                            () -> {
                                processor.processDue(arrival);
                                return null;
                            });

            assertThat(statuses.getFirst()).isEqualTo(201);
            // 10 walk home, 10 stand at Bob's (in the old stack or a new one)
            assertThat(spearmenAt(bob.villageId)).isEqualTo(10);
            assertThat(spearmenOnTheWay()).isEqualTo(10);
            assertThat(homeSpearmen()).isZero();
            assertEveryMovementHasItsEvent();
        }
    }

    @Test
    void anOfferAcceptedTwiceAtOnceIsTradedOnce() throws Exception {
        TestPlayer carol = TestPlayer.register(mockMvc);
        Fixture.richVillage(jdbc, carol.villageId);
        for (int round = 0; round < ROUNDS; round++) {
            long offer =
                    number(
                                    body(
                                            alice.browser
                                                    .post(
                                                            alice.villagePath() + "/offers",
                                                            """
                                                            {"offerResource":"WOOD","offerAmount":1000,
                                                             "wantResource":"CLAY","wantAmount":1000,
                                                             "count":1}\
                                                            """)
                                                    .andExpect(status().isCreated())),
                                    "$.id")
                            .longValue();
            int bobClay = resources(bob.villageId).get(1);
            int carolClay = resources(carol.villageId).get(1);

            List<Integer> statuses = together(() -> accept(bob, offer), () -> accept(carol, offer));

            assertThat(statuses).contains(201);
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
            assertThat(statuses.stream().filter(s -> s != 201).findFirst().orElseThrow())
                    .isIn(404, 409);
            assertThat(
                            jdbc.sql("select count(*) from trade_offer where id = ?")
                                    .param(offer)
                                    .query(Long.class)
                                    .single())
                    .isZero();
            int paid =
                    (bobClay - resources(bob.villageId).get(1))
                            + (carolClay - resources(carol.villageId).get(1));
            assertThat(paid).isEqualTo(1000);
        }
    }

    @Test
    void buildingCancellingAndMovingAtOnceKeepsTheQueueWhole() throws Exception {
        Browser second = secondSession();
        Browser third = secondSession();
        for (int round = 0; round < ROUNDS; round++) {
            long first = alice.enqueueOk(BuildingType.WOODCUTTER);
            long middle = alice.enqueueOk(BuildingType.CLAY_PIT);
            long last = alice.enqueueOk(BuildingType.IRON_MINE);
            String path = alice.villagePath() + "/build-orders/";

            together(
                    () -> alice.browser.delete(path + middle),
                    () -> second.post(path + last + "/move", "{\"position\":1}"),
                    () ->
                            third.post(
                                    alice.villagePath() + "/build-orders",
                                    "{\"building\":\"WAREHOUSE\"}"));

            assertThat(resources(alice.villageId)).allMatch(amount -> amount >= 0);
            List<Instant[]> queue =
                    jdbc.sql(
                                    """
                                    select b.start_at, b.end_at,
                                           (select count(*) from game_event e
                                            where e.kind = 'BUILD_COMPLETE' and e.ref_id = b.id
                                              and e.status = 'PENDING' and e.due_at = b.end_at)
                                    from build_order b where b.village_id = ?
                                    order by b.end_at, b.id
                                    """)
                            .param(alice.villageId)
                            .query(
                                    (rs, row) -> {
                                        assertThat(rs.getLong(3))
                                                .as("the order's event at its end")
                                                .isEqualTo(1);
                                        return new Instant[] {
                                            rs.getTimestamp(1).toInstant(),
                                            rs.getTimestamp(2).toInstant()
                                        };
                                    })
                            .list();
            // back to back: each order starts when the one in front of it ends
            for (int i = 1; i < queue.size(); i++) {
                assertThat(queue.get(i)[0]).isEqualTo(queue.get(i - 1)[1]);
            }
            assertThat(queue).hasSizeBetween(2, 4);
            jdbc.sql("delete from build_order where village_id = ?")
                    .param(alice.villageId)
                    .update();
            jdbc.sql("delete from game_event").update();
            Fixture.richVillage(jdbc, alice.villageId);
            assertThat(first).isPositive();
        }
    }

    // ---- helpers ----

    // ---- 1.2 write paths across two villages of one player (the 1.2.0 audit, item 4) ----

    @Test
    void twoVillagesRecruitingTheLastPaladinGetOne() throws Exception {
        setPaladinSlots(4, 5); // two villages: one slot
        Browser second = secondSession();
        long other = addVillage();
        String paladin = "{\"unit\":\"PALADIN\",\"count\":1}";
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from recruit_order where village_id in (?, ?)")
                    .params(alice.villageId, other)
                    .update();

            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.post(
                                            alice.villagePath() + "/recruit-orders", paladin),
                            () -> second.post(villagePath(other) + "/recruit-orders", paladin));

            assertThat(statuses).as(lastBodies.toString()).containsExactlyInAnyOrder(201, 409);
            assertThat(
                            jdbc.sql(
                                            "select coalesce(sum(count), 0) from recruit_order"
                                                    + " where village_id in (?, ?) and unit = ?")
                                    .params(alice.villageId, other, "PALADIN")
                                    .query(Long.class)
                                    .single())
                    .isEqualTo(1);
        }
    }

    @Test
    void oneBannerEquippedInTwoVillagesFliesInOne() throws Exception {
        Browser second = secondSession();
        long other = addVillage();
        setLevel(alice.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(other, BuildingType.BANNER_HALL, 1);
        for (int round = 0; round < ROUNDS; round++) {
            long banner =
                    jdbc.sql(
                                    """
                                    insert into banner (owner_id, type, tier, origin, created_at)
                                    values (?, 'HARVEST', 1, 'GRANTED', now()) returning id
                                    """)
                            .param(alice.playerId)
                            .query(Long.class)
                            .single();

            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.put(
                                            villagePath(alice.villageId) + "/banner/" + banner, ""),
                            () -> second.put(villagePath(other) + "/banner/" + banner, ""));

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(
                            jdbc.sql("select equipped_village_id from banner where id = ?")
                                    .param(banner)
                                    .query(Long.class)
                                    .single())
                    .isIn(alice.villageId, other);
            assertThat(
                            jdbc.sql(
                                            "select count(*) from banner_event"
                                                    + " where banner_id = ? and kind = 'EQUIPPED'")
                                    .param(banner)
                                    .query(Long.class)
                                    .single())
                    .isEqualTo(1);
            // the next round starts with empty halls
            jdbc.sql("update banner set equipped_village_id = null where id = ?")
                    .param(banner)
                    .update();
        }
    }

    @Test
    void theSameResearchStartedInTwoVillagesRunsOnceAndCostsOnce() throws Exception {
        setUnitLevels(true);
        Browser second = secondSession();
        long other = addVillage();
        for (long village : List.of(alice.villageId, other)) {
            setLevel(village, BuildingType.SMITHY, 10);
            setLevel(village, BuildingType.BARRACKS, 1);
        }
        String spearman = "{\"unit\":\"SPEARMAN\"}";
        for (int round = 0; round < ROUNDS; round++) {
            jdbc.sql("delete from research_order where player_id = ?")
                    .param(alice.playerId)
                    .update();
            setStock(alice.villageId, 20_000);
            setStock(other, 20_000);

            List<Integer> statuses =
                    together(
                            () ->
                                    alice.browser.post(
                                            alice.villagePath() + "/research-orders", spearman),
                            () -> second.post(villagePath(other) + "/research-orders", spearman));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(
                            jdbc.sql(
                                            "select count(*) from research_order"
                                                    + " where player_id = ? and unit = 'SPEARMAN'")
                                    .param(alice.playerId)
                                    .query(Long.class)
                                    .single())
                    .isEqualTo(1);
            // only the village whose research runs has paid: the refused one was rolled back
            long paid =
                    List.of(alice.villageId, other).stream()
                            .filter(village -> resources(village).getFirst() < 20_000)
                            .count();
            assertThat(paid).isEqualTo(1);
        }
    }

    private final List<Integer> arrivedOrReturning = new ArrayList<>();

    private long spearmenAtBefore(int round) {
        return arrivedOrReturning.stream().limit(round).mapToLong(Integer::longValue).sum();
    }

    private static String villagePath(long villageId) {
        return "/api/villages/" + villageId;
    }

    /**
     * A second village for Alice, rich like her first: a free field on a row of its own, handed
     * over by SQL and deleted again after the test.
     */
    private long addVillage() {
        int row = TestWorldConfiguration.MAP_SIZE - 16;
        for (int x = 20; x < TestWorldConfiguration.MAP_SIZE; x++) {
            Long id =
                    jdbc.sql(
                                    """
                                    insert into village (player_id, name, x, y, wood, clay, iron,
                                                         resources_updated_at, loyalty_updated_at,
                                                         points, created_at)
                                    values (:player, 'second', :x, :y, 0, 0, 0, now(), now(), 0, now())
                                    on conflict (x, y) do nothing
                                    returning id
                                    """)
                            .param("player", alice.playerId)
                            .param("x", x)
                            .param("y", row)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                extraVillages.add(id);
                // every village has its home stack (a recruit order with 201 reads it)
                jdbc.sql(
                                "insert into unit_stack (home_village_id, location_village_id)"
                                        + " values (?, ?)")
                        .params(id, id)
                        .update();
                Fixture.richVillage(jdbc, id);
                setLevel(id, BuildingType.HEADQUARTERS, 1);
                return id;
            }
        }
        throw new IllegalStateException("no free field for a second village");
    }

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, villageId)
                .update();
    }

    /** The stock as of the test clock, so a catch-up adds no production. */
    private void setStock(long villageId, int each) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(each, each, each, Timestamp.from(clock.instant()), villageId)
                .update();
    }

    private void setPaladinSlots(int villagesPerSlot, int maxSlots) {
        jdbc.sql("update world_settings set paladin_villages_per_slot = ?, paladin_max_slots = ?")
                .params(villagesPerSlot, maxSlots)
                .update();
        worldService.reload();
    }

    private void setUnitLevels(boolean enabled) {
        jdbc.sql("update world_settings set unit_levels_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void allowCancelUntilArrival() {
        jdbc.sql("update world_settings set cancel_window_seconds = 86400 where id = 1").update();
        worldService.reload();
    }

    private Browser secondSession() throws Exception {
        Browser browser = Browser.open(mockMvc);
        browser.login(aliceName, Browser.PASSWORD).andExpect(status().isOk());
        return browser;
    }

    private ResultActions accept(TestPlayer player, long offer) throws Exception {
        return player.browser.post(
                "/api/offers/" + offer + "/accept",
                "{\"villageId\":" + player.villageId + ",\"count\":1}");
    }

    /**
     * Runs the actions at the same moment; the HTTP status of each (null for an action that returns
     * no {@link ResultActions}), in the order given.
     */
    @SafeVarargs
    private List<Integer> together(Callable<ResultActions>... actions) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResultActions>> futures = new ArrayList<>();
        for (Callable<ResultActions> action : actions) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return action.call();
                            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        lastBodies.clear();
        for (Future<ResultActions> future : futures) {
            ResultActions result = future.get(30, TimeUnit.SECONDS);
            statuses.add(result == null ? null : result.andReturn().getResponse().getStatus());
            lastBodies.add(
                    result == null ? null : result.andReturn().getResponse().getContentAsString());
        }
        return statuses;
    }

    private static String addJson(Coordinates at) {
        return "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":1}}"
                .formatted(at.x(), at.y());
    }

    private long farmOk(Coordinates at, String units) throws Exception {
        String answer =
                body(
                        alice.browser
                                .post(
                                        alice.villagePath() + "/farm-targets",
                                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":%s}"
                                                .formatted(at.x(), at.y(), units))
                                .andExpect(status().isCreated()));
        return number(answer, "$.id").longValue();
    }

    private long farmEntries() {
        return jdbc.sql("select count(*) from farm_target where village_id = ?")
                .param(alice.villageId)
                .query(Long.class)
                .single();
    }

    private static String commandJson(String kind, Coordinates at, String units) {
        return "{\"kind\":\"%s\",\"target\":{\"x\":%d,\"y\":%d},\"units\":%s}"
                .formatted(kind, at.x(), at.y(), units);
    }

    private long homeSpearmen() {
        return jdbc.sql(
                        "select u_spearman from unit_stack where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(alice.villageId, alice.villageId)
                .query(Long.class)
                .single();
    }

    private long spearmenOnTheWay() {
        return jdbc.sql(
                        "select coalesce(sum(u_spearman), 0) from movement where home_village_id ="
                                + " ?")
                .param(alice.villageId)
                .query(Long.class)
                .single();
    }

    /** Alice's spearmen standing in that village. */
    private long spearmenAt(long villageId) {
        return jdbc.sql(
                        "select coalesce(sum(u_spearman), 0) from unit_stack"
                                + " where home_village_id = ? and location_village_id = ?")
                .params(alice.villageId, villageId)
                .query(Long.class)
                .single();
    }

    private long totalSpearmen() {
        return jdbc.sql(
                                "select coalesce(sum(u_spearman), 0) from unit_stack"
                                        + " where home_village_id = ?")
                        .param(alice.villageId)
                        .query(Long.class)
                        .single()
                + spearmenOnTheWay();
    }

    private String movementKind(long movement) {
        return jdbc.sql("select kind from movement where id = ?")
                .param(movement)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private void assertEveryMovementHasItsEvent() {
        assertThat(
                        jdbc.sql(
                                        """
                                        select count(*) from movement m where m.home_village_id = ?
                                        and not exists (select 1 from game_event e
                                            where e.kind = 'MOVEMENT_ARRIVE' and e.ref_id = m.id
                                              and e.status = 'PENDING' and e.due_at = m.arrive_at)
                                        """)
                                .param(alice.villageId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    /** wood, clay, iron rounded down. */
    private List<Integer> resources(long villageId) {
        return jdbc.sql("select floor(wood), floor(clay), floor(iron) from village where id = ?")
                .param(villageId)
                .query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2), rs.getInt(3)))
                .single();
    }

    @SuppressWarnings("unused")
    private static Timestamp at(Instant instant) {
        return Timestamp.from(instant);
    }
}
