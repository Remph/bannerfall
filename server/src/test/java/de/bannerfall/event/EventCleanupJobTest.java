package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestEventHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Only DONE events older than a week are deleted. */
@IntegrationTest
class EventCleanupJobTest {

    @Autowired EventCleanupJob cleanup;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired TestEventHandler handler;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private TransactionTemplate transaction;

    @Autowired
    void useTransactions(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void cleanSlate() {
        jdbc.sql("delete from game_event").update();
        handler.reset();
    }

    @AfterEach
    void leaveNothingBehind() {
        jdbc.sql("delete from game_event").update();
        handler.reset();
    }

    @Test
    void deletesDoneEventsOlderThanAWeekAndNothingElse() {
        // Day 0: one event done, one failed for good.
        handler.failFor(2);
        schedule(1, clock.instant());
        schedule(2, clock.instant());
        for (int attempt = 0; attempt < EventProcessor.MAX_ATTEMPTS; attempt++) {
            processor.processDue(clock.instant());
        }
        // Day 1: a third event is done; on day 8 it is exactly a week old.
        clock.advance(Duration.ofDays(1));
        schedule(3, clock.instant());
        processor.processDue(clock.instant());
        // Day 3: a fourth event is done.
        clock.advance(Duration.ofDays(2));
        schedule(4, clock.instant());
        processor.processDue(clock.instant());
        // A fifth event is still waiting for its time.
        schedule(5, clock.instant().plus(Duration.ofDays(30)));
        // Day 8.
        clock.advance(Duration.ofDays(5));

        int deleted = cleanup.deleteOldDoneEvents();

        // Only event 1 (done 8 days ago). Event 3 is exactly 7 days old: kept. Failed and waiting
        // events stay whatever their age.
        assertThat(deleted).isEqualTo(1);
        assertThat(remainingRefs()).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
    }

    @Test
    void doesNothingWhenThereIsNothingToDelete() {
        assertThat(cleanup.deleteOldDoneEvents()).isZero();
    }

    private void schedule(long refId, Instant dueAt) {
        transaction.executeWithoutResult(
                status -> scheduler.schedule(GameEventKind.TEST, refId, dueAt));
    }

    private List<Long> remainingRefs() {
        return jdbc.sql("select ref_id from game_event order by ref_id").query(Long.class).list();
    }
}
