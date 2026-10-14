package de.bannerfall.building;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.event.GameEventRepository;
import de.bannerfall.event.GameEventStatus;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRepository;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The BUILD_COMPLETE handler in the awkward cases: the order is gone, the event is later than the
 * order, and the two races between the event worker and a cancelling request (01-architecture §4.6:
 * the worker locks event → village, a request locks village → event).
 */
@IntegrationTest
class BuildCompleteHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired GameEventRepository events;
    @Autowired VillageRepository villages;
    @Autowired VillageLocker locker;
    @Autowired BuildService buildService;

    private TransactionTemplate transaction;
    private TestPlayer player;

    @Autowired
    void useTransactions(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void registerPlayer() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void anEventForAnOrderThatIsGoneIsHandledWithoutChangingAnything() throws Exception {
        long missingOrder = 987_654_321L;
        long eventId =
                transaction
                        .execute(
                                status ->
                                        scheduler.schedule(
                                                GameEventKind.BUILD_COMPLETE,
                                                missingOrder,
                                                clock.instant()))
                        .getId();

        assertThat(processor.processDue(clock.instant())).isEqualTo(1);

        assertThat(events.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(GameEventStatus.DONE);
        player.village()
                .andExpect(jsonPath("$.points").value(39))
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(1));
    }

    /**
     * Normally the event is due exactly when the order ends. If a re-timing could not move the
     * event (the worker held it), the order says earlier than the event, and the order's end is
     * when the village is brought up to date.
     */
    @Test
    void theEndOfTheOrderCountsWhenTheEventIsLater() throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        Instant end = instant(body(player.village()), "$.buildQueue[0].endAt");
        Instant late = end.plus(Duration.ofHours(1));
        transaction.executeWithoutResult(
                status -> scheduler.reschedule(GameEventKind.BUILD_COMPLETE, order, late));

        clock.set(late);
        assertThat(processor.processDue(clock.instant())).isEqualTo(1);

        Village village = villages.findById(player.villageId).orElseThrow();
        assertThat(village.getBuildings().get(BuildingType.WOODCUTTER)).isEqualTo(2);
        // brought up to the end of the order, not to the later time of the event
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(end);
    }

    /**
     * The worker holds the event row while the request cancels the order: the cancel must neither
     * wait for the event nor fail. The event stays, and its handler finds the order gone.
     */
    @Test
    void aCancelThatMeetsTheWorkerDoesNotWaitAndTheHandlerFindsTheOrderGone() throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        Instant end = instant(body(player.village()), "$.buildQueue[0].endAt");
        long eventId = eventIdOf(order);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        // plays the worker: holds the event row and does not let go
        Future<?> worker =
                pool.submit(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            events.findByIdForUpdate(eventId).orElseThrow();
                                            locked.countDown();
                                            awaitQuietly(release);
                                        }));
        try {
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            // would wait for the whole 20 seconds (and fail) if the cancel waited for the event
            assertTimeoutPreemptively(
                    Duration.ofSeconds(20),
                    () -> player.cancel(order).andExpect(status().isNoContent()));
        } finally {
            release.countDown();
            worker.get(30, TimeUnit.SECONDS);
            pool.shutdownNow();
        }

        // the cancel could not remove the event
        assertThat(events.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(GameEventStatus.PENDING);
        player.village()
                .andExpect(jsonPath("$.buildQueue").isEmpty())
                .andExpect(jsonPath("$.resources.wood", closeTo(500, 1e-9)));

        // when it comes due the handler finds no order and the event is simply done
        clock.set(end);
        assertThat(processor.processDue(clock.instant())).isEqualTo(1);
        assertThat(events.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(GameEventStatus.DONE);
        player.village()
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(1))
                .andExpect(jsonPath("$.points").value(39));
    }

    /**
     * The other direction: the worker has the event and waits for the village, which a request
     * holds that tries to cancel the order. The order is due, so it is done and the cancel is
     * refused; once the request lets go of the village the handler completes the order.
     */
    @Test
    void aCancelThatHoldsTheVillageWhileTheWorkerWaitsIsRefusedAndTheOrderIsDone()
            throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        Instant end = instant(body(player.village()), "$.buildQueue[0].endAt");
        long eventId = eventIdOf(order);
        clock.set(end);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> worker =
                    transaction.execute(
                            status -> {
                                // the "request": holds the village lock ...
                                locker.lockOwned(player.villageId, player.playerId);
                                Future<Integer> started =
                                        pool.submit(() -> processor.processDue(clock.instant()));
                                // ... until the worker holds the event and waits for the village
                                waitUntilSomebodyWaitsForALock();
                                // ... and tries to cancel the order: too late, it is due
                                assertThatThrownBy(
                                                () ->
                                                        buildService.cancel(
                                                                player.villageId,
                                                                order,
                                                                player.playerId))
                                        .isInstanceOf(NotFoundException.class);
                                // the failed cancel marked the transaction; end it quietly
                                status.setRollbackOnly();
                                return started;
                            });

            assertThat(worker.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(events.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(GameEventStatus.DONE);
        player.village()
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(2))
                .andExpect(jsonPath("$.buildQueue").isEmpty());
    }

    // ---- helpers ----

    private long eventIdOf(long orderId) {
        return events.findAll().stream()
                .filter(e -> e.getKind() == GameEventKind.BUILD_COMPLETE)
                .filter(e -> e.getRefId() != null && e.getRefId() == orderId)
                .map(GameEvent::getId)
                .findFirst()
                .orElseThrow();
    }

    /** Polls PostgreSQL until a session waits for a lock (here: the worker, for the village). */
    private void waitUntilSomebodyWaitsForALock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            long waiting =
                    jdbc.sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock'")
                            .query(Long.class)
                            .single();
            if (waiting > 0) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the worker", e);
            }
        }
        throw new AssertionError("The worker never waited for the village lock");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
