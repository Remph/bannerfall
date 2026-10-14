package de.bannerfall.building;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.event.GameEventRepository;
import de.bannerfall.gamedata.BuildingType;
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
 * Moving orders in the build queue (M4c, 02-game-rules §4.3 <i>Move</i>). The default queue holds
 * three orders: the running one and two waiting ones that can swap places.
 */
@IntegrationTest
class BuildOrderMoveTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired GameEventRepository events;

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

    @Test
    void anOrderMovesInFrontAndTheWaitingOrdersAreRetimed() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.IRON_MINE);
        String before = body(player.village());
        Instant start1 = instant(before, "$.buildQueue[0].startAt");
        Instant end1 = instant(before, "$.buildQueue[0].endAt");
        long duration2 = secondsOf(before, 1);
        long duration3 = secondsOf(before, 2);
        clock.advance(Duration.ofSeconds(100)); // the first order is running

        String moved = body(player.move(third, 1).andExpect(status().isOk()));

        // the answer is the new queue: the running order stays, the iron mine comes next
        assertThat(ids(moved)).containsExactly(first, third, second);
        assertThat(instant(moved, "$[0].startAt")).isEqualTo(start1);
        assertThat(instant(moved, "$[0].endAt")).isEqualTo(end1);
        Instant end3 = end1.plusSeconds(duration3);
        assertThat(instant(moved, "$[1].startAt")).isEqualTo(end1);
        assertThat(instant(moved, "$[1].endAt")).isEqualTo(end3);
        Instant end2 = end3.plusSeconds(duration2);
        assertThat(instant(moved, "$[2].startAt")).isEqualTo(end3);
        assertThat(instant(moved, "$[2].endAt")).isEqualTo(end2);

        // the village shows the same queue; nothing was paid or refunded
        String after = body(player.village());
        assertThat(queueIds(after)).containsExactly(first, third, second);
        assertThat(number(after, "$.population.used"))
                .isEqualTo(number(before, "$.population.used"));
        assertThat(number(after, "$.buildQueue[1].targetLevel").intValue()).isEqualTo(2);

        // the events moved with the orders
        assertThat(eventsOf(first)).extracting(GameEvent::getDueAt).containsExactly(end1);
        assertThat(eventsOf(third)).extracting(GameEvent::getDueAt).containsExactly(end3);
        assertThat(eventsOf(second)).extracting(GameEvent::getDueAt).containsExactly(end2);

        // and the orders are done in their new order
        clock.set(end1);
        assertThat(runWorker()).isEqualTo(1);
        clock.set(end3);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(2))
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(1));
        clock.set(end2);
        assertThat(runWorker()).isEqualTo(1);
        player.village()
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(2))
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(2))
                .andExpect(jsonPath("$.buildQueue").isEmpty());
    }

    @Test
    void anOrderMovedBehindStartsLater() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.IRON_MINE);
        String before = body(player.village());
        Instant end1 = instant(before, "$.buildQueue[0].endAt");
        long duration3 = secondsOf(before, 2);

        String moved = body(player.move(second, 2).andExpect(status().isOk()));

        assertThat(ids(moved)).containsExactly(first, third, second);
        // unlike after a cancel, an order can get later here
        Instant newStart2 = end1.plusSeconds(duration3);
        assertThat(newStart2).isAfter(instant(before, "$.buildQueue[1].startAt"));
        assertThat(instant(moved, "$[2].startAt")).isEqualTo(newStart2);
        assertThat(eventsOf(second))
                .extracting(GameEvent::getDueAt)
                .containsExactly(instant(moved, "$[2].endAt"));
    }

    @Test
    void movingAnOrderToItsOwnPlaceChangesNothing() throws Exception {
        player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        String before = body(player.village());

        String moved = body(player.move(second, 1).andExpect(status().isOk()));

        assertThat(JsonPath.<List<Object>>read(moved, "$"))
                .isEqualTo(JsonPath.<List<Object>>read(before, "$.buildQueue"));
    }

    @Test
    void startedOrdersStayInFront() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        long third = player.enqueueOk(BuildingType.IRON_MINE);
        String before = body(player.village());

        // the running order can't move, nothing can pass it, and there is no place behind the end
        player.move(first, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
        player.move(third, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
        player.move(second, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));

        assertThat(JsonPath.<List<Object>>read(body(player.village()), "$.buildQueue"))
                .isEqualTo(JsonPath.<List<Object>>read(before, "$.buildQueue"));
    }

    @Test
    void whenTheWorkerIsBehindTheNextOrderHasStartedToo() throws Exception {
        long first = player.enqueueOk(BuildingType.WOODCUTTER);
        long second = player.enqueueOk(BuildingType.CLAY_PIT);
        player.enqueueOk(BuildingType.IRON_MINE);
        clock.set(instant(body(player.village()), "$.buildQueue[0].endAt"));

        // the first order is done (only not applied yet), the second one runs since its end
        player.move(first, 2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUILD_ORDER_NOT_FOUND"));
        player.move(second, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
    }

    @Test
    void anOrderCanNotMoveInFrontOfWhatItNeeds() throws Exception {
        setStock(900, 900, 900);
        jdbc.sql("update village set b_headquarters = 2 where id = ?")
                .param(player.villageId)
                .update();
        player.enqueueOk(BuildingType.WOODCUTTER);
        long townHall = player.enqueueOk(BuildingType.HEADQUARTERS); // to level 3
        long barracks = player.enqueueOk(BuildingType.BARRACKS); // needs Town Hall 3
        String before = body(player.village());

        // neither the barracks in front of the Town Hall nor the Town Hall behind the barracks
        player.move(barracks, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        player.move(townHall, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        assertThat(JsonPath.<List<Object>>read(body(player.village()), "$.buildQueue"))
                .isEqualTo(JsonPath.<List<Object>>read(before, "$.buildQueue"));
        assertThat(eventsOf(barracks))
                .extracting(GameEvent::getDueAt)
                .containsExactly(instant(before, "$.buildQueue[2].endAt"));
    }

    @Test
    void upgradesOfOneBuildingKeepTheirOrder() throws Exception {
        setStock(900, 900, 900);
        player.enqueueOk(BuildingType.CLAY_PIT);
        long level2 = player.enqueueOk(BuildingType.WOODCUTTER);
        long level3 = player.enqueueOk(BuildingType.WOODCUTTER);

        player.move(level3, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
        player.move(level2, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));

        player.village()
                .andExpect(jsonPath("$.buildQueue[1].id").value(level2))
                .andExpect(jsonPath("$.buildQueue[1].targetLevel").value(2))
                .andExpect(jsonPath("$.buildQueue[2].id").value(level3))
                .andExpect(jsonPath("$.buildQueue[2].targetLevel").value(3));
    }

    @Test
    void anOrderThatIsGoneCanNotBeMoved() throws Exception {
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.CLAY_PIT);
        long cancelled = player.enqueueOk(BuildingType.IRON_MINE);
        player.cancel(cancelled).andExpect(status().isNoContent());
        TestPlayer other = TestPlayer.register(mockMvc);
        other.enqueueOk(BuildingType.WOODCUTTER);
        long foreign = other.enqueueOk(BuildingType.CLAY_PIT);

        for (long id : List.of(cancelled, foreign, 987_654_321L)) {
            player.move(id, 1)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BUILD_ORDER_NOT_FOUND"));
        }
    }

    // ---- helpers ----

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(wood, clay, iron, player.villageId)
                .update();
    }

    private int runWorker() {
        return processor.processDue(clock.instant());
    }

    /** The order ids of a queue answer (a JSON array of orders). */
    private static List<Long> ids(String queueJson) {
        List<Number> ids = JsonPath.read(queueJson, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static List<Long> queueIds(String villageJson) {
        List<Number> ids = JsonPath.read(villageJson, "$.buildQueue[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    /** The duration of the n-th order of the queue in a village body. */
    private static long secondsOf(String villageJson, int index) {
        Instant start = instant(villageJson, "$.buildQueue[" + index + "].startAt");
        Instant end = instant(villageJson, "$.buildQueue[" + index + "].endAt");
        return Duration.between(start, end).toSeconds();
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
