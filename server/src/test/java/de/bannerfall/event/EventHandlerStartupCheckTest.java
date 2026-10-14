package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The check that runs once at startup, here called by hand against the test database. */
@IntegrationTest
class EventHandlerStartupCheckTest {

    @Autowired EventHandlerStartupCheck check;
    @Autowired EventScheduler scheduler;
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
    }

    @AfterEach
    void leaveNothingBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void passesWhenNothingIsWaiting() {
        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void passesWhenEveryWaitingKindHasAHandler() {
        schedule(GameEventKind.TEST);

        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void stopsTheServerWhenWaitingEventsHaveNoHandler() {
        schedule(GameEventKind.TEST);
        schedule(GameEventKind.TEST_UNHANDLED);

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                // only the kind without handler is named, not TEST
                .hasMessageContaining("[TEST_UNHANDLED]");
    }

    @Test
    void eventsThatAreNotWaitingAnymoreDoNotMatter() {
        schedule(GameEventKind.TEST_UNHANDLED);
        jdbc.sql("update game_event set status = 'DONE', processed_at = now()").update();

        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void namesAWaitingKindThisVersionDoesNotKnow() {
        jdbc.sql(
                        """
                        insert into game_event (kind, due_at, status, attempts, created_at)
                        values ('FROM_THE_FUTURE', now(), 'PENDING', 0, now())
                        """)
                .update();

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FROM_THE_FUTURE");
    }

    private void schedule(GameEventKind kind) {
        transaction.executeWithoutResult(status -> scheduler.schedule(kind, 1L, clock.instant()));
    }
}
