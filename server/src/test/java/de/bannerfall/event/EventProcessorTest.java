package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestEventHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The event worker (01-architecture §4.6): order, failures, time, no double processing. */
@IntegrationTest
class EventProcessorTest {

    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired GameEventRepository events;
    @Autowired TestEventHandler handler;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private TransactionTemplate transaction;

    @Autowired
    void useTransactions(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    /** The shared clock only moves forward, so every test works relative to its own start. */
    private Instant start;

    @BeforeEach
    void cleanSlate() {
        // processDue handles everything that is due, so no event of another test may be left over.
        jdbc.sql("delete from game_event").update();
        handler.reset();
        start = clock.instant();
    }

    @AfterEach
    void leaveNothingBehind() {
        jdbc.sql("delete from game_event").update();
        handler.reset();
    }

    @Test
    void eventsRunInDueOrderAndEventsAtTheSameInstantInCreationOrder() {
        schedule(1, start.plusSeconds(180));
        schedule(2, start.plusSeconds(60));
        schedule(3, start.plusSeconds(120));
        schedule(4, start.plusSeconds(60)); // same instant as 2, created later

        int processed = processor.processDue(start.plusSeconds(600));

        assertThat(processed).isEqualTo(4);
        assertThat(handler.handledRefs()).containsExactly(2L, 4L, 3L, 1L);
    }

    @Test
    void eventsInTheFutureAreNotProcessed() {
        Instant due = start.plus(Duration.ofHours(1));
        long id = schedule(1, due);

        assertThat(processor.processDue(due.minusMillis(1))).isZero();
        assertThat(handler.handled()).isEmpty();
        assertThat(event(id).getStatus()).isEqualTo(GameEventStatus.PENDING);

        assertThat(processor.processDue(due)).isEqualTo(1); // exactly due counts as due
        assertThat(event(id).getStatus()).isEqualTo(GameEventStatus.DONE);
    }

    @Test
    void aHandlerSeesTheDueTimeNotTheCurrentTime() {
        Instant due = start.plusSeconds(60);
        long id = schedule(1, due);
        clock.advance(Duration.ofDays(3)); // the server "was down"

        processor.processDue(clock.instant());

        assertThat(handler.handled())
                .singleElement()
                .satisfies(handled -> assertThat(handled.dueAt()).isEqualTo(due));
        assertThat(event(id).getDueAt()).isEqualTo(due);
        assertThat(event(id).getProcessedAt()).isEqualTo(clock.instant());
    }

    @Test
    void theEventIdIsInTheMdcWhileTheEventIsHandled() {
        long id = schedule(1, start);
        List<String> seen = new CopyOnWriteArrayList<>();
        handler.beforeHandling(event -> seen.add(MDC.get(EventProcessor.MDC_KEY)));

        processor.processDue(start);

        assertThat(seen).containsExactly(Long.toString(id));
        assertThat(MDC.get(EventProcessor.MDC_KEY)).isNull();
    }

    @Test
    void theEventIdIsRemovedFromTheMdcAlsoWhenTheHandlerFails() {
        schedule(1, start);
        handler.failFor(1);

        processor.processDue(start);

        assertThat(MDC.get(EventProcessor.MDC_KEY)).isNull();
    }

    @Test
    void processingTwiceDoesNotApplyAnEventTwice() {
        long id = schedule(1, start);

        assertThat(processor.processDue(start)).isEqualTo(1);
        assertThat(processor.processDue(start)).isZero();

        assertThat(handler.handledRefs()).containsExactly(1L);
        assertThat(event(id).getStatus()).isEqualTo(GameEventStatus.DONE);
    }

    @Test
    void twoWorkersRacingForTheSameEventHandleItOnce() throws Exception {
        schedule(1, start);
        handler.beforeHandling(event -> pause(300)); // keeps the first worker's lock for a while
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> worker =
                    () -> {
                        go.await();
                        return processor.processDue(start);
                    };
            Future<Integer> first = pool.submit(worker);
            Future<Integer> second = pool.submit(worker);
            go.countDown();

            int handledByBoth = first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS);

            assertThat(handledByBoth).isEqualTo(1);
            assertThat(handler.handledRefs()).containsExactly(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingHandlerCountsAttemptsAndAfterThreeIsFailedWhileOthersStillRun() {
        long bad = schedule(1, start.plusSeconds(1));
        schedule(2, start.plusSeconds(2));
        schedule(3, start.plusSeconds(3));
        handler.failFor(1);
        Instant until = start.plusSeconds(10);

        // The events behind the failing one are handled, the failing one is tried once.
        assertThat(processor.processDue(until)).isEqualTo(2);
        assertThat(handler.handledRefs()).containsExactly(2L, 3L);
        assertThat(event(bad).getAttempts()).isEqualTo(1);
        assertThat(event(bad).getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(event(bad).getLastError()).contains("test failure for ref 1");

        assertThat(processor.processDue(until)).isZero();
        assertThat(event(bad).getAttempts()).isEqualTo(2);
        assertThat(event(bad).getStatus()).isEqualTo(GameEventStatus.PENDING);

        assertThat(processor.processDue(until)).isZero();
        GameEvent failed = event(bad);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getStatus()).isEqualTo(GameEventStatus.FAILED);
        assertThat(failed.getProcessedAt()).isEqualTo(clock.instant());

        // Given up: never tried again.
        assertThat(processor.processDue(until)).isZero();
        assertThat(event(bad).getAttempts()).isEqualTo(3);
    }

    @Test
    void aFailingHandlerLeavesNoChangesBehind() {
        long id = schedule(1, start);
        handler.failFor(1);
        // The handler does some work in its transaction (schedules another event), then fails.
        handler.beforeHandling(
                event -> scheduler.schedule(GameEventKind.TEST, 999L, start.plusSeconds(1)));

        processor.processDue(start.plusSeconds(10));

        assertThat(event(id).getAttempts()).isEqualTo(1);
        assertThat(countEventsOf(999)).isZero();
    }

    @Test
    void anEventWithoutHandlerFailsLikeAnyOther() {
        long id =
                transaction
                        .execute(
                                status ->
                                        scheduler.schedule(GameEventKind.TEST_UNHANDLED, 1L, start))
                        .getId();

        processor.processDue(start);

        GameEvent event = event(id);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("No handler for event kind TEST_UNHANDLED");
    }

    @Test
    void failingEventsAreTriedOncePerCallAndDoNotBlockTheBatch() {
        long bad1 = schedule(1, start.plusSeconds(1));
        long bad2 = schedule(2, start.plusSeconds(2));
        for (int i = 0; i < EventProcessor.BATCH_SIZE; i++) {
            schedule(100 + i, start.plusSeconds(10 + i));
        }
        handler.failFor(1);
        handler.failFor(2);

        // The two failing events fill the head of every batch; the good ones behind still run.
        int processed = processor.processDue(start.plusSeconds(1000));

        assertThat(processed).isEqualTo(EventProcessor.BATCH_SIZE);
        assertThat(event(bad1).getAttempts()).isEqualTo(1);
        assertThat(event(bad2).getAttempts()).isEqualTo(1);
    }

    @Test
    void moreEventsThanOneBatchAreAllProcessed() {
        int count = EventProcessor.BATCH_SIZE + 5;
        for (int i = 0; i < count; i++) {
            schedule(i, start.plusSeconds(i));
        }

        assertThat(processor.processDue(start.plusSeconds(1000))).isEqualTo(count);
        assertThat(handler.handled()).hasSize(count);
    }

    @Test
    void eventsScheduledByAHandlerRunInTheSameCallIfTheyAreAlreadyDue() {
        schedule(1, start.plusSeconds(10));
        handler.beforeHandling(
                event -> {
                    if (event.getRefId() == 1) {
                        scheduler.schedule(GameEventKind.TEST, 2L, start.plusSeconds(20));
                        scheduler.schedule(GameEventKind.TEST, 3L, start.plus(Duration.ofDays(1)));
                    }
                });

        int processed = processor.processDue(start.plusSeconds(60));

        assertThat(processed).isEqualTo(2);
        assertThat(handler.handledRefs()).containsExactly(1L, 2L);
        assertThat(countEventsOf(3)).isEqualTo(1); // later, still waiting
    }

    @Test
    void anEventCancelledByAnEarlierHandlerIsSkipped() {
        schedule(1, start.plusSeconds(1));
        schedule(2, start.plusSeconds(2));
        handler.beforeHandling(event -> scheduler.cancel(GameEventKind.TEST, 2));

        int processed = processor.processDue(start.plusSeconds(60));

        assertThat(processed).isEqualTo(1);
        assertThat(handler.handledRefs()).containsExactly(1L);
        assertThat(countEventsOf(2)).isZero();
    }

    @Test
    void anEventMovedLaterAfterItWasFetchedWaitsForItsNewTime() {
        Instant until = start.plusSeconds(60);
        Instant later = start.plus(Duration.ofHours(1));
        schedule(1, start.plusSeconds(1));
        long moved = schedule(2, start.plusSeconds(2)); // fetched together with 1
        handler.beforeHandling(
                event -> {
                    if (event.getRefId() == 1) {
                        scheduler.reschedule(GameEventKind.TEST, 2, later);
                    }
                });

        assertThat(processor.processDue(until)).isEqualTo(1);
        assertThat(handler.handledRefs()).containsExactly(1L);
        assertThat(event(moved).getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(event(moved).getDueAt()).isEqualTo(later);

        assertThat(processor.processDue(later)).isEqualTo(1);
        assertThat(handler.handled())
                .last()
                .satisfies(handled -> assertThat(handled.dueAt()).isEqualTo(later));
    }

    private long schedule(long refId, Instant dueAt) {
        return transaction.execute(
                status -> scheduler.schedule(GameEventKind.TEST, refId, dueAt).getId());
    }

    private GameEvent event(long id) {
        return events.findById(id).orElseThrow();
    }

    private long countEventsOf(long refId) {
        return jdbc.sql("select count(*) from game_event where ref_id = ?")
                .param(refId)
                .query(Long.class)
                .single();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
