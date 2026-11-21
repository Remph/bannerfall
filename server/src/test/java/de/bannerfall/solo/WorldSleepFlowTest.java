package de.bannerfall.solo;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestEventHandler;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.notify.PlayerConnections;
import de.bannerfall.victory.FrozenTime;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A solo world sleeps while nobody plays and picks up exactly where it stopped (2.1,
 * 12-solo-and-allies §2, §3; 02-game-rules §28): events due while asleep, marches, build queues,
 * the victory hold time and the end date keep the time they had left; pause, play and fast forward.
 * The NPC lords' memory (rounds are events, the attack caps live in the memory) is covered by
 * {@code FrozenTimeTest}.
 */
@IntegrationTest
class WorldSleepFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired WorldSleep sleep;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestEventHandler testEvents;
    @Autowired SentMessages sent;
    @Autowired FrozenTime frozenTime;
    @Autowired PlayerConnections connections;
    @Autowired ApplicationEventPublisher publisher;

    private Host host;
    private TestPlayer alice;

    @BeforeEach
    void setUp() throws Exception {
        host = new Host(mockMvc);
        testEvents.reset();
        alice = TestPlayer.register(mockMvc);
        jdbc.sql("update world_settings set solo_mode = true").update();
        worldService.reload();
        // the last human request was now
        sleep.activity();
    }

    @AfterEach
    void putTheWorldBack() {
        jdbc.sql(
                        """
                        update world_settings set solo_mode = false, asleep_since = null,
                            paused = false, clock_speed = 1, ends_at = null, heartbeat_at = null,
                            victory_leader = null, victory_leader_since = null
                        """)
                .update();
        jdbc.sql(
                        "delete from game_event where kind in ('TEST', 'WORLD_END') and status ="
                                + " 'PENDING'")
                .update();
        worldService.reload();
    }

    @Test
    void theWorldSleepsTwoMinutesAfterTheLastHumanAndWakesOnHisFirstRequest() throws Exception {
        Instant t0 = clock.instant();
        long order = alice.enqueueOk(BuildingType.WOODCUTTER);
        Instant buildEnd = buildEnd(order);
        jdbc.sql(
                        "update village set wood = 0, clay = 0, iron = 0, resources_updated_at = ?"
                                + " where id = ?")
                .params(Timestamp.from(t0), alice.villageId)
                .update();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5151L, t0.plus(Duration.ofMinutes(10))));
        jdbc.sql(
                        "update world_settings set ends_at = ?, victory_leader = 'p1',"
                                + " victory_leader_since = ?")
                .params(
                        Timestamp.from(t0.plus(Duration.ofDays(30))),
                        Timestamp.from(t0.minus(Duration.ofHours(1))))
                .update();
        worldService.reload();
        sleep.activity();

        clock.set(t0.plus(Duration.ofSeconds(119)));
        sleep.step();
        assertThat(worldService.asleepSince()).isNull();

        Instant asleepAt = t0.plus(Duration.ofMinutes(2));
        clock.set(asleepAt);
        sleep.step();
        assertThat(worldService.asleepSince()).isEqualTo(asleepAt);

        // five hours later: nothing moved, the event due eight minutes after did not run
        Instant later = asleepAt.plus(Duration.ofHours(5));
        clock.set(later);
        processor.processDue(later);
        assertThat(testEvents.handledRefs()).doesNotContain(5151L);
        assertThat(worldService.gameTime(later)).isEqualTo(asleepAt);
        sleep.step();
        assertThat(worldService.asleepSince()).isEqualTo(asleepAt);

        // his first request wakes it: every game clock moved on by the five hours asleep
        Duration asleep = Duration.ofHours(5);
        String village = body(alice.village().andExpect(status().isOk()));
        assertThat(worldService.asleepSince()).isNull();
        assertThat(buildEnd(order)).isEqualTo(buildEnd.plus(asleep));
        assertThat(testDue(5151L)).isEqualTo(t0.plus(Duration.ofMinutes(10)).plus(asleep));
        assertThat(timestamp("select ends_at from world_settings"))
                .isEqualTo(t0.plus(Duration.ofDays(30)).plus(asleep));
        assertThat(timestamp("select victory_leader_since from world_settings"))
                .isEqualTo(t0.minus(Duration.ofHours(1)).plus(asleep));
        assertThat(sent.toPlayer(alice.playerId)).anyMatch(m -> m.contains("WORLD_TIME_MOVED"));

        // two minutes of production while awake, none for the five hours
        double wood = JsonPath.<Number>read(village, "$.resources.wood").doubleValue();
        double perHour = JsonPath.<Number>read(village, "$.production.wood").doubleValue();
        assertThat(perHour).isPositive();
        assertThat(wood).isCloseTo(perHour / 30, within(0.01));

        // the event comes after its eight minutes left, not before
        clock.set(later.plus(Duration.ofSeconds(479)));
        processor.processDue(clock.instant());
        assertThat(testEvents.handledRefs()).doesNotContain(5151L);
        clock.set(later.plus(Duration.ofMinutes(8)));
        processor.processDue(clock.instant());
        assertThat(testEvents.handledRefs()).contains(5151L);
    }

    @Test
    void aMarchKeepsTheTravelTimeItHadLeft() throws Exception {
        TestPlayer bob = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), bob.playerId)
                .update();
        jdbc.sql(
                        "update unit_stack set u_axeman = 10 where home_village_id = ? and"
                                + " location_village_id = ?")
                .params(alice.villageId, alice.villageId)
                .update();
        String command =
                body(
                        alice.command("ATTACK", coordinates(bob), "{\"AXEMAN\":10}")
                                .andExpect(status().isCreated()));
        long movement = number(command, "$.id").longValue();
        Instant arrive = Instant.parse(JsonPath.read(command, "$.arriveAt"));

        Instant asleepAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.set(asleepAt);
        sleep.step();
        assertThat(worldService.asleepSince()).isEqualTo(asleepAt);

        Duration asleep = Duration.ofHours(30);
        clock.set(asleepAt.plus(asleep));
        processor.processDue(clock.instant());
        assertThat(arriveAt(movement)).isEqualTo(arrive);

        bob.village().andExpect(status().isOk());
        assertThat(arriveAt(movement)).isEqualTo(arrive.plus(asleep));
        jdbc.sql("delete from movement where id = ?").param(movement).update();
    }

    @Test
    void aPausedWorldStaysAsleepUntilTheHostPressesPlay() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5252L, t0.plus(Duration.ofMinutes(1))));
        host.put("/api/admin/world-clock", "{\"paused\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.asleepSince").value(t0.toString()));
        assertThat(sent.toPlayer(alice.playerId)).anyMatch(m -> m.contains("WORLD_TIME_MOVED"));

        clock.set(t0.plus(Duration.ofHours(1)));
        // reading does not wake a paused world, and the client sees where its clock stands
        alice.browser
                .get("/api/world")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloMode").value(true))
                .andExpect(jsonPath("$.pausedAt").value(t0.toString()));
        assertThat(worldService.asleepSince()).isEqualTo(t0);
        // ... and changing the game waits for play
        alice.enqueue(BuildingType.WOODCUTTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_PAUSED"));
        // the two minutes without a human don't matter either
        sleep.step();
        assertThat(worldService.paused()).isTrue();

        host.put("/api/admin/world-clock", "{\"paused\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));
        assertThat(worldService.asleepSince()).isNull();
        assertThat(testDue(5252L)).isEqualTo(t0.plus(Duration.ofHours(1)).plusSeconds(60));
        alice.enqueue(BuildingType.WOODCUTTER).andExpect(status().isCreated());
    }

    @Test
    void fastForwardBringsEveryClockCloserInTenSecondSteps() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5353L, t0.plus(Duration.ofMinutes(10))));
        host.put("/api/admin/world-clock", "{\"speed\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value(4));

        clock.set(t0.plusSeconds(10));
        sleep.activity();
        sleep.step();
        assertThat(testDue(5353L)).isEqualTo(t0.plus(Duration.ofMinutes(10)).minusSeconds(30));
        sleep.step();
        assertThat(testDue(5353L)).isEqualTo(t0.plus(Duration.ofMinutes(10)).minusSeconds(60));

        host.put("/api/admin/world-clock", "{\"speed\":1}").andExpect(status().isOk());
        sleep.step();
        assertThat(testDue(5353L)).isEqualTo(t0.plus(Duration.ofMinutes(10)).minusSeconds(60));

        host.put("/api/admin/world-clock", "{\"speed\":3}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyASoloWorldHasTheSpeedControlAndSleeps() throws Exception {
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        host.put("/api/admin/world-clock", "{\"paused\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORLD_NOT_SOLO"));
        clock.advance(Duration.ofMinutes(10));
        sleep.step();
        assertThat(worldService.asleepSince()).isNull();
    }

    @Test
    void switchingSoloOffWakesTheWorldEvenWhenPaused() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5454L, t0.plus(Duration.ofMinutes(1))));
        host.put("/api/admin/world-clock", "{\"paused\":true}").andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(30));
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        sleep.step();
        assertThat(worldService.asleepSince()).isNull();
        assertThat(worldService.paused()).isFalse();
        assertThat(testDue(5454L)).isEqualTo(t0.plus(Duration.ofMinutes(31)));
    }

    @Test
    void timeTheServerWasDownIsNoGameTime() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5555L, t0.plus(Duration.ofMinutes(10))));
        sleep.step();
        assertThat(heartbeat()).isEqualTo(t0);

        // the server stops while he plays and starts again three hours later: the first event
        // round puts the world to sleep at its last heartbeat, before anything is handled
        Instant start = t0.plus(Duration.ofHours(3));
        clock.set(start);
        sleep.beforeEvents();
        assertThat(worldService.asleepSince()).isEqualTo(t0);
        assertThat(heartbeat()).isNull();
        processor.processDue(start);
        assertThat(testEvents.handledRefs()).doesNotContain(5555L);

        // his first request wakes it: the event keeps its ten minutes
        alice.village().andExpect(status().isOk());
        assertThat(worldService.asleepSince()).isNull();
        assertThat(testDue(5555L)).isEqualTo(start.plus(Duration.ofMinutes(10)));
        assertThat(heartbeat()).isEqualTo(start);
    }

    @Test
    void aFreshStartReadsTheStoredHeartbeatBeforeTheFirstEventRound() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5858L, t0.plus(Duration.ofMinutes(10))));
        sleep.step();
        assertThat(heartbeat()).isEqualTo(t0);

        // the server was killed (no clean shutdown) and starts two hours later: a new instance
        // knows no heartbeat of its own, its first check reads the stored one
        Instant start = t0.plus(Duration.ofHours(2));
        clock.set(start);
        WorldSleep restarted =
                new WorldSleep(
                        worldService,
                        frozenTime,
                        connections,
                        clock,
                        publisher,
                        jdbc,
                        transactions);
        restarted.beforeEvents();
        assertThat(worldService.asleepSince()).isEqualTo(t0);
        processor.processDue(start);
        assertThat(testEvents.handledRefs()).doesNotContain(5858L);
    }

    @Test
    void aCleanShutdownFallsAsleepAtOnce() throws Exception {
        Instant t0 = clock.instant();
        sleep.step();
        clock.set(t0.plusSeconds(5));
        sleep.shutdown();
        assertThat(worldService.asleepSince()).isEqualTo(t0.plusSeconds(5));
        assertThat(heartbeat()).isNull();
    }

    @Test
    void aSuspendFallsAsleepAtTheTickBeforeIt() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5656L, t0.plus(Duration.ofMinutes(10))));
        sleep.step();
        clock.set(t0.plusSeconds(10));
        sleep.step();
        assertThat(heartbeat()).isEqualTo(t0.plusSeconds(10));

        // the PC sleeps for an hour; the next tick finds the gap
        Instant resumed = t0.plus(Duration.ofHours(1));
        clock.set(resumed);
        sleep.step();
        assertThat(worldService.asleepSince()).isEqualTo(t0.plusSeconds(10));

        sleep.activity();
        assertThat(worldService.asleepSince()).isNull();
        assertThat(testDue(5656L))
                .isEqualTo(
                        t0.plus(Duration.ofMinutes(10)).plus(Duration.ofHours(1)).minusSeconds(10));
    }

    @Test
    void aRequestRightAfterASuspendWakesAWorldThatSleptThroughIt() throws Exception {
        Instant t0 = clock.instant();
        tx(() -> scheduler.schedule(GameEventKind.TEST, 5757L, t0.plus(Duration.ofMinutes(10))));
        sleep.step();

        // he opens the game again before the next tick: the hour suspended is still no game time
        clock.set(t0.plus(Duration.ofHours(1)));
        alice.village().andExpect(status().isOk());
        assertThat(worldService.asleepSince()).isNull();
        assertThat(testDue(5757L)).isEqualTo(t0.plus(Duration.ofMinutes(70)));
    }

    @Test
    void ticksTenSecondsApartKeepTheWorldAwakeUntilTheTwoMinutesAreOver() throws Exception {
        Instant t0 = clock.instant();
        for (int s = 0; s < 120; s += 10) {
            clock.set(t0.plusSeconds(s));
            sleep.step();
            assertThat(worldService.asleepSince()).isNull();
        }
        clock.set(t0.plusSeconds(120));
        sleep.step();
        assertThat(worldService.asleepSince()).isEqualTo(t0.plusSeconds(120));
        assertThat(heartbeat()).isNull();
    }

    @Test
    void aPausedWorldStaysPausedAfterADowntime() throws Exception {
        Instant t0 = clock.instant();
        sleep.step();
        host.put("/api/admin/world-clock", "{\"paused\":true}").andExpect(status().isOk());
        assertThat(heartbeat()).isNull();

        clock.set(t0.plus(Duration.ofHours(2)));
        sleep.beforeEvents();
        sleep.step();
        sleep.activity();
        assertThat(worldService.paused()).isTrue();
        assertThat(worldService.asleepSince()).isEqualTo(t0);
    }

    @Test
    void aWorldThatIsNotSoloKeepsNoHeartbeat() throws Exception {
        jdbc.sql("update world_settings set solo_mode = false").update();
        worldService.reload();
        sleep.step();
        assertThat(heartbeat()).isNull();
        clock.advance(Duration.ofHours(1));
        sleep.beforeEvents();
        assertThat(worldService.asleepSince()).isNull();
    }

    // ---- helpers ----

    private Instant heartbeat() {
        Timestamp beat =
                jdbc.sql("select heartbeat_at from world_settings")
                        .query(Timestamp.class)
                        .list()
                        .getFirst();
        return beat == null ? null : beat.toInstant();
    }

    private void tx(Runnable action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> action.run());
    }

    private Instant buildEnd(long orderId) {
        return timestamp("select end_at from build_order where id = " + orderId);
    }

    private Instant testDue(long ref) {
        return timestamp(
                "select due_at from game_event where kind = 'TEST' and status = 'PENDING' and"
                        + " ref_id = "
                        + ref);
    }

    private Instant arriveAt(long movementId) {
        return timestamp("select arrive_at from movement where id = " + movementId);
    }

    private Instant timestamp(String sql) {
        return jdbc.sql(sql).query(Timestamp.class).single().toInstant();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
