package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestEventHandler;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Scheduling, rescheduling and cancelling events (01-architecture §4.6). */
@IntegrationTest
class EventSchedulerTest {

    @Autowired EventScheduler scheduler;
    @Autowired EventProcessor processor;
    @Autowired GameEventRepository events;
    @Autowired TestEventHandler handler;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private TransactionTemplate transaction;

    @Autowired
    void useTransactions(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    private Instant start;

    @BeforeEach
    void cleanSlate() {
        jdbc.sql("delete from game_event").update();
        handler.reset();
        start = clock.instant();
    }

    @AfterEach
    void leaveNothingBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void scheduleStoresAPendingEvent() {
        Instant due = start.plusSeconds(90);

        GameEvent saved =
                transaction.execute(status -> scheduler.schedule(GameEventKind.TEST, 42L, due));

        GameEvent loaded = events.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getKind()).isEqualTo(GameEventKind.TEST);
        assertThat(loaded.getRefId()).isEqualTo(42L);
        assertThat(loaded.getDueAt()).isEqualTo(due);
        assertThat(loaded.getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(loaded.getAttempts()).isZero();
        assertThat(loaded.getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void anEventNeedsNoSubject() {
        GameEvent saved =
                transaction.execute(
                        status -> scheduler.schedule(GameEventKind.BARBARIAN_GROWTH, null, start));

        assertThat(events.findById(saved.getId()).orElseThrow().getRefId()).isNull();
    }

    @Test
    void schedulingNeedsTheCallersTransaction() {
        assertThatThrownBy(() -> scheduler.schedule(GameEventKind.TEST, 1L, start))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> scheduler.reschedule(GameEventKind.TEST, 1, start))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> scheduler.cancel(GameEventKind.TEST, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void anEventIsRolledBackTogetherWithTheTransactionItWasScheduledIn() {
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            scheduler.schedule(GameEventKind.TEST, 77L, start);
                                            throw new IllegalStateException("boom");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countEventsOf(77)).isZero();
    }

    @Test
    void rescheduleMovesOnlyTheEventsOfThatKindAndSubject() {
        Instant due = start.plusSeconds(60);
        Instant later = start.plusSeconds(120);
        long target = schedule(GameEventKind.TEST, 1, due);
        long otherSubject = schedule(GameEventKind.TEST, 2, due);
        long otherKind = schedule(GameEventKind.MOVEMENT_ARRIVE, 1, due);

        boolean moved = inTransaction(() -> scheduler.reschedule(GameEventKind.TEST, 1, later));

        assertThat(moved).isTrue();
        assertThat(event(target).getDueAt()).isEqualTo(later);
        assertThat(event(otherSubject).getDueAt()).isEqualTo(due);
        assertThat(event(otherKind).getDueAt()).isEqualTo(due);
    }

    @Test
    void cancelRemovesOnlyTheEventsOfThatKindAndSubject() {
        long target = schedule(GameEventKind.TEST, 1, start);
        long otherSubject = schedule(GameEventKind.TEST, 2, start);
        long otherKind = schedule(GameEventKind.MOVEMENT_ARRIVE, 1, start);

        boolean cancelled = inTransaction(() -> scheduler.cancel(GameEventKind.TEST, 1));

        assertThat(cancelled).isTrue();
        assertThat(events.findById(target)).isEmpty();
        assertThat(events.findById(otherSubject)).isPresent();
        assertThat(events.findById(otherKind)).isPresent();
    }

    @Test
    void rescheduleAndCancelAnswerFalseWhenNothingIsPending() {
        boolean moved = inTransaction(() -> scheduler.reschedule(GameEventKind.TEST, 5, start));
        boolean cancelled = inTransaction(() -> scheduler.cancel(GameEventKind.TEST, 5));

        assertThat(moved).isFalse();
        assertThat(cancelled).isFalse();
    }

    @Test
    void anEventThatIsAlreadyDoneIsNeitherMovedNorRemoved() {
        long id = schedule(GameEventKind.TEST, 1, start);
        processor.processDue(start);

        boolean moved =
                inTransaction(
                        () -> scheduler.reschedule(GameEventKind.TEST, 1, start.plusSeconds(60)));
        boolean cancelled = inTransaction(() -> scheduler.cancel(GameEventKind.TEST, 1));

        assertThat(moved).isFalse();
        assertThat(cancelled).isFalse();
        GameEvent event = event(id);
        assertThat(event.getStatus()).isEqualTo(GameEventStatus.DONE);
        assertThat(event.getDueAt()).isEqualTo(start);
    }

    /**
     * The worker holds an event's row lock while its handler waits for the village lock; a request
     * that holds the village lock must not wait for the event (deadlock), see {@link
     * EventScheduler}. Here a second transaction plays the worker.
     */
    @Test
    void anEventTheWorkerHoldsRightNowIsLeftAloneAndNobodyWaitsForIt() throws Exception {
        long id = schedule(GameEventKind.TEST, 1, start);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> worker =
                pool.submit(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            events.findByIdForUpdate(id).orElseThrow();
                                            locked.countDown();
                                            awaitQuietly(release);
                                        }));
        try {
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            // Should the code wait for the lock, this fails after 3 s instead of hanging.
            boolean moved =
                    inTransaction(
                            () -> {
                                jdbc.sql("set local lock_timeout = '3s'").update();
                                return scheduler.reschedule(
                                        GameEventKind.TEST, 1, start.plusSeconds(60));
                            });
            boolean cancelled =
                    inTransaction(
                            () -> {
                                jdbc.sql("set local lock_timeout = '3s'").update();
                                return scheduler.cancel(GameEventKind.TEST, 1);
                            });

            assertThat(moved).isFalse();
            assertThat(cancelled).isFalse();
        } finally {
            release.countDown();
            worker.get(30, TimeUnit.SECONDS);
            pool.shutdownNow();
        }
        assertThat(event(id).getDueAt()).isEqualTo(start);

        // Once the worker has let go, the event can be moved again.
        boolean movedNow =
                inTransaction(
                        () -> scheduler.reschedule(GameEventKind.TEST, 1, start.plusSeconds(60)));
        assertThat(movedNow).isTrue();
        assertThat(event(id).getDueAt()).isEqualTo(start.plusSeconds(60));
    }

    private long schedule(GameEventKind kind, long refId, Instant dueAt) {
        return inTransaction(() -> scheduler.schedule(kind, refId, dueAt).getId());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
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

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
