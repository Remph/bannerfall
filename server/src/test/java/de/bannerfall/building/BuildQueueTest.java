package de.bannerfall.building;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestGameData;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.event.GameEventRepository;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.rules.BuildRules;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The build queue over time (M4b): what happens when an order is done, and what a cancel does to
 * the orders behind it. The clock is moved by hand and the event worker is called by hand.
 */
@IntegrationTest
class BuildQueueTest {

    private static final double EPSILON = 1e-6;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired GameEventRepository events;

    private final GameData data = TestGameData.load();
    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    // ---- completion ----

    @Test
    void anUpgradeIsDoneAtTheEndOfItsOrderAndProductionUsesTheOldLevelUntilThen() throws Exception {
        // The iron mine: level 1 makes 30 per hour, level 2 makes 30 × 1.165 (02-game-rules §3).
        ResourceAmounts cost = cost(BuildingType.IRON_MINE, 2);
        long seconds = seconds(BuildingType.IRON_MINE, 2, 1);
        player.enqueueOk(BuildingType.IRON_MINE);
        Instant end = clock.instant().plusSeconds(seconds);

        // one millisecond before the end nothing is due
        clock.set(end.minusMillis(1));
        assertThat(runWorker()).isZero();
        player.village()
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(1))
                .andExpect(jsonPath("$.buildQueue.length()").value(1));

        // exactly at the end it is done
        clock.set(end);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(2))
                .andExpect(jsonPath("$.buildQueue").isEmpty());

        // one more hour is produced at the new level, everything before it at the old one
        clock.set(end.plus(Duration.ofHours(1)));
        double newRate = 30 * 1.165;
        double ironAtEnd = 500 - cost.iron() + 30.0 * seconds / 3600.0;
        player.village()
                .andExpect(jsonPath("$.production.iron", closeTo(newRate, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(ironAtEnd + newRate, EPSILON)))
                // wood paid for the upgrade, the woodcutter itself did not change
                .andExpect(
                        jsonPath(
                                "$.resources.wood",
                                closeTo(
                                        500 - cost.wood() + 30.0 * (seconds + 3600) / 3600.0,
                                        EPSILON)));
    }

    @Test
    void processingLateGivesTheSameResultAsProcessingOnTime() throws Exception {
        ResourceAmounts cost = cost(BuildingType.IRON_MINE, 2);
        long seconds = seconds(BuildingType.IRON_MINE, 2, 1);
        player.enqueueOk(BuildingType.IRON_MINE);
        Instant end = clock.instant().plusSeconds(seconds);

        // The worker was down: nobody looked at the village, two hours after the end it catches up.
        clock.set(end.plus(Duration.ofHours(2)));
        assertThat(runWorker()).isEqualTo(1);

        double newRate = 30 * 1.165;
        double ironAtEnd = 500 - cost.iron() + 30.0 * seconds / 3600.0;
        player.village()
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(2))
                .andExpect(jsonPath("$.resources.iron", closeTo(ironAtEnd + 2 * newRate, EPSILON)));
    }

    @Test
    void ordersAreDoneOneAfterAnother() throws Exception {
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.CLAY_PIT);
        String queue = body(player.village());
        Instant end1 = instant(queue, "$.buildQueue[0].endAt");
        Instant end2 = instant(queue, "$.buildQueue[1].endAt");

        clock.set(end1);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(2))
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(1))
                .andExpect(jsonPath("$.buildQueue.length()").value(1));

        clock.set(end2);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(2))
                .andExpect(jsonPath("$.buildQueue").isEmpty());
    }

    @Test
    void pointsAreRecalculatedWhenAnOrderIsDone() throws Exception {
        long seconds = seconds(BuildingType.WOODCUTTER, 2, 1);
        player.village().andExpect(jsonPath("$.points").value(39));
        player.browser.get("/api/me").andExpect(jsonPath("$.points").value(39));
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.village().andExpect(jsonPath("$.points").value(39));

        clock.advance(Duration.ofSeconds(seconds));
        assertThat(runWorker()).isEqualTo(1);

        // woodcutter level 1 is worth 6 points, level 2 another 7 (02-game-rules §4.2)
        player.village().andExpect(jsonPath("$.points").value(46));
        player.browser.get("/api/me").andExpect(jsonPath("$.points").value(46));
    }

    @Test
    void theTownHallLevelWhenQueueingSetsTheTime() throws Exception {
        long slow = seconds(BuildingType.CLAY_PIT, 2, 1);
        long fast = seconds(BuildingType.CLAY_PIT, 3, 2);
        assertThat(fast).isLessThan(seconds(BuildingType.CLAY_PIT, 3, 1));

        player.enqueueOk(BuildingType.HEADQUARTERS);
        player.enqueueOk(BuildingType.CLAY_PIT); // Town Hall is still 1
        String queue = body(player.village());
        assertThat(secondsOf(queue, 1)).isEqualTo(slow);

        // once the Town Hall is level 2 the next clay pit upgrade is faster
        clock.set(instant(queue, "$.buildQueue[1].endAt"));
        assertThat(runWorker()).isEqualTo(2);
        player.enqueueOk(BuildingType.CLAY_PIT);
        assertThat(secondsOf(body(player.village()), 0)).isEqualTo(fast);
    }

    // ---- cancelling ----

    @Test
    void cancellingRefundsEverythingAndMovesTheLaterOrdersUp() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.IRON_MINE);
        String queue = body(player.village());
        long duration2 = secondsOf(queue, 1);
        long duration3 = secondsOf(queue, 2);

        clock.advance(Duration.ofSeconds(100)); // the first order is running
        String beforeCancel = body(player.village());
        // Town Hall 5 + woodcutter 5 + clay pit 10 + iron mine 10, plus +1 +2 +2 for the queue
        assertThat(number(beforeCancel, "$.population.used").intValue()).isEqualTo(35);

        player.cancel(first).andExpect(status().isNoContent());
        String after = body(player.village());

        ResourceAmounts refund = cost(BuildingType.WOODCUTTER, 2);
        assertThat(stock(after, "wood"))
                .isCloseTo(stock(beforeCancel, "wood") + refund.wood(), within(EPSILON));
        assertThat(stock(after, "clay"))
                .isCloseTo(stock(beforeCancel, "clay") + refund.clay(), within(EPSILON));
        assertThat(stock(after, "iron"))
                .isCloseTo(stock(beforeCancel, "iron") + refund.iron(), within(EPSILON));
        // the population of the cancelled upgrade is free again
        assertThat(number(after, "$.population.used").intValue()).isEqualTo(34);

        // the second order starts now, the third right after it
        Instant now = clock.instant();
        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(2);
        assertThat(number(after, "$.buildQueue[0].id").longValue()).isEqualTo(second);
        assertThat(instant(after, "$.buildQueue[0].startAt")).isEqualTo(now);
        Instant end2 = instant(after, "$.buildQueue[0].endAt");
        assertThat(end2).isEqualTo(now.plusSeconds(duration2));
        assertThat(number(after, "$.buildQueue[1].id").longValue()).isEqualTo(third);
        assertThat(instant(after, "$.buildQueue[1].startAt")).isEqualTo(end2);
        Instant end3 = instant(after, "$.buildQueue[1].endAt");
        assertThat(end3).isEqualTo(end2.plusSeconds(duration3));

        // the events moved with the orders, the cancelled order's event is gone
        assertThat(eventsOf(first)).isEmpty();
        assertThat(eventsOf(second)).extracting(GameEvent::getDueAt).containsExactly(end2);
        assertThat(eventsOf(third)).extracting(GameEvent::getDueAt).containsExactly(end3);

        // and they fire at their new time, which is earlier than the old one
        clock.set(end2.minusMillis(1));
        assertThat(runWorker()).isZero();
        clock.set(end2);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(1)) // cancelled: not upgraded
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(2))
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(1));
        clock.set(end3);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(2))
                .andExpect(jsonPath("$.buildQueue").isEmpty());
    }

    @Test
    void cancellingAMiddleOrderMovesOnlyTheOrdersBehindIt() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.IRON_MINE);
        String queue = body(player.village());
        Instant start1 = instant(queue, "$.buildQueue[0].startAt");
        Instant end1 = instant(queue, "$.buildQueue[0].endAt");
        long duration3 = secondsOf(queue, 2);

        clock.advance(Duration.ofSeconds(100));
        player.cancel(second).andExpect(status().isNoContent());
        String after = body(player.village());

        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(2);
        // the running order is untouched
        assertThat(number(after, "$.buildQueue[0].id").longValue()).isEqualTo(first);
        assertThat(instant(after, "$.buildQueue[0].startAt")).isEqualTo(start1);
        assertThat(instant(after, "$.buildQueue[0].endAt")).isEqualTo(end1);
        // the third one follows it directly
        assertThat(number(after, "$.buildQueue[1].id").longValue()).isEqualTo(third);
        assertThat(instant(after, "$.buildQueue[1].startAt")).isEqualTo(end1);
        Instant end3 = end1.plusSeconds(duration3);
        assertThat(instant(after, "$.buildQueue[1].endAt")).isEqualTo(end3);
        assertThat(eventsOf(first)).extracting(GameEvent::getDueAt).containsExactly(end1);
        assertThat(eventsOf(second)).isEmpty();
        assertThat(eventsOf(third)).extracting(GameEvent::getDueAt).containsExactly(end3);
    }

    @Test
    void cancellingTheLastOrderOnlyRefunds() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        String queue = body(player.village());
        Instant end1 = instant(queue, "$.buildQueue[0].endAt");

        player.cancel(second).andExpect(status().isNoContent());

        String after = body(player.village());
        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(1);
        assertThat(instant(after, "$.buildQueue[0].endAt")).isEqualTo(end1);
        // woodcutter 2 stays paid, clay pit 2 is back
        player.village()
                .andExpect(jsonPath("$.resources.wood", closeTo(500 - 63, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(500 - 75, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(500 - 50, EPSILON)));
        assertThat(eventsOf(first)).hasSize(1);
        assertThat(eventsOf(second)).isEmpty();
    }

    @Test
    void cancellingAnUpgradeAlsoCancelsTheLaterUpgradesOfThatBuilding() throws Exception {
        setStock(900, 900, 900);
        long first = player.enqueueOk(BuildingType.WOODCUTTER); // to level 2
        long clayPit = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.WOODCUTTER); // to level 3
        clock.advance(Duration.ofSeconds(100)); // the first order is running
        String before = body(player.village());

        player.cancel(first).andExpect(status().isNoContent());

        // level 3 can't come without level 2: that order is cancelled too, both are refunded
        String after = body(player.village());
        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(1);
        assertThat(number(after, "$.buildQueue[0].id").longValue()).isEqualTo(clayPit);
        assertThat(instant(after, "$.buildQueue[0].startAt")).isEqualTo(clock.instant());
        ResourceAmounts refund =
                cost(BuildingType.WOODCUTTER, 2).plus(cost(BuildingType.WOODCUTTER, 3));
        assertRefunded(before, after, refund);
        assertThat(eventsOf(first)).isEmpty();
        assertThat(eventsOf(third)).isEmpty();

        // the woodcutter can be queued again, from level 2
        player.enqueue(BuildingType.WOODCUTTER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetLevel").value(2));
    }

    @Test
    void cancellingATownHallUpgradeAlsoCancelsWhatOnlyItMadePossible() throws Exception {
        setStock(900, 900, 900);
        jdbc.sql("update village set b_headquarters = 2 where id = ?")
                .param(player.villageId)
                .update();
        long townHall = player.enqueueOk(BuildingType.HEADQUARTERS); // to level 3
        long barracks = player.enqueueOk(BuildingType.BARRACKS); // needs Town Hall 3
        long woodcutter = player.enqueueOk(BuildingType.WOODCUTTER); // needs nothing
        String before = body(player.village());

        player.cancel(townHall).andExpect(status().isNoContent());

        String after = body(player.village());
        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(1);
        assertThat(number(after, "$.buildQueue[0].id").longValue()).isEqualTo(woodcutter);
        assertThat(instant(after, "$.buildQueue[0].startAt")).isEqualTo(clock.instant());
        ResourceAmounts refund =
                cost(BuildingType.HEADQUARTERS, 3).plus(cost(BuildingType.BARRACKS, 1));
        assertRefunded(before, after, refund);
        assertThat(eventsOf(barracks)).isEmpty();
        assertThat(eventsOf(woodcutter)).hasSize(1);
    }

    @Test
    void anOrderThatIsDueCanNotBeCancelledEvenBeforeTheWorkerHasRun() throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        Instant end = instant(body(player.village()), "$.buildQueue[0].endAt");
        clock.set(end); // done right now, the worker has not run yet
        String before = body(player.village());

        player.cancel(order)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILD_ORDER_NOT_FOUND"));

        // no refund, the order is still waiting for the worker, which then completes it
        String after = body(player.village());
        assertThat(stock(after, "wood")).isCloseTo(stock(before, "wood"), within(EPSILON));
        assertThat(number(after, "$.buildQueue.length()").intValue()).isEqualTo(1);
        assertThat(runWorker()).isEqualTo(1);
        player.village().andExpect(jsonPath("$.buildings.WOODCUTTER").value(2));
    }

    @Test
    void whatARefundCanNotStoreIsLost() throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER); // 63 wood
        // while the order runs, the warehouse (1000) is full again
        jdbc.sql("update village set wood = 1000 where id = ?").param(player.villageId).update();
        player.village().andExpect(jsonPath("$.resources.wood", closeTo(1000, EPSILON)));

        player.cancel(order).andExpect(status().isNoContent());

        // the refund only fills up to the capacity, the 63 wood above it are lost (02 §4.3)
        player.village()
                .andExpect(jsonPath("$.storageCapacity").value(1000))
                .andExpect(jsonPath("$.resources.wood", closeTo(1000, EPSILON)));
    }

    @Test
    void anOrderThatIsGoneCanNotBeCancelled() throws Exception {
        long done = player.enqueueOk(BuildingType.WOODCUTTER);
        clock.advance(Duration.ofSeconds(seconds(BuildingType.WOODCUTTER, 2, 1)));
        assertThat(runWorker()).isEqualTo(1);
        long cancelled = player.enqueueOk(BuildingType.CLAY_PIT);
        player.cancel(cancelled).andExpect(status().isNoContent());
        TestPlayer other = TestPlayer.register(mockMvc);
        long foreign = other.enqueueOk(BuildingType.WOODCUTTER);

        for (long id : List.of(done, cancelled, foreign, 987_654_321L)) {
            player.cancel(id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BUILD_ORDER_NOT_FOUND"));
        }
        // the other player's order is still there
        other.village().andExpect(jsonPath("$.buildQueue.length()").value(1));
    }

    // ---- helpers ----

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(wood, clay, iron, player.villageId)
                .update();
    }

    private static void assertRefunded(String before, String after, ResourceAmounts refund) {
        assertThat(stock(after, "wood"))
                .isCloseTo(stock(before, "wood") + refund.wood(), within(EPSILON));
        assertThat(stock(after, "clay"))
                .isCloseTo(stock(before, "clay") + refund.clay(), within(EPSILON));
        assertThat(stock(after, "iron"))
                .isCloseTo(stock(before, "iron") + refund.iron(), within(EPSILON));
    }

    private int runWorker() {
        return processor.processDue(clock.instant());
    }

    private ResourceAmounts cost(BuildingType type, int level) {
        return BuildRules.cost(data.building(type), level);
    }

    private long seconds(BuildingType type, int level, int hqLevel) {
        return BuildRules.buildSeconds(data.building(type), level, hqLevel, 1.0);
    }

    /** The duration of the n-th order of the queue in a village body. */
    private static long secondsOf(String villageJson, int index) {
        Instant start = instant(villageJson, "$.buildQueue[" + index + "].startAt");
        Instant end = instant(villageJson, "$.buildQueue[" + index + "].endAt");
        return Duration.between(start, end).toSeconds();
    }

    private static double stock(String villageJson, String resource) {
        return number(villageJson, "$.resources." + resource).doubleValue();
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }

    private List<GameEvent> eventsOf(long orderId) {
        return events.findAll().stream()
                .filter(e -> e.getKind() == GameEventKind.BUILD_COMPLETE)
                .filter(e -> e.getRefId() != null && e.getRefId() == orderId)
                .toList();
    }
}
