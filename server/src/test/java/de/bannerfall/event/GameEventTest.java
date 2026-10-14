package de.bannerfall.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The state changes of a single event (no database). */
class GameEventTest {

    private static final Instant CREATED = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant DUE = Instant.parse("2026-10-03T13:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-03T13:00:05Z");

    private static GameEvent pending() {
        return GameEvent.pending(GameEventKind.TEST, 7L, DUE, CREATED);
    }

    @Test
    void aNewEventIsPending() {
        GameEvent event = pending();

        assertThat(event.getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(event.getKind()).isEqualTo(GameEventKind.TEST);
        assertThat(event.getRefId()).isEqualTo(7L);
        assertThat(event.getDueAt()).isEqualTo(DUE);
        assertThat(event.getCreatedAt()).isEqualTo(CREATED);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void rescheduleMovesTheDueTime() {
        GameEvent event = pending();

        event.reschedule(DUE.plusSeconds(60));

        assertThat(event.getDueAt()).isEqualTo(DUE.plusSeconds(60));
        assertThat(event.getStatus()).isEqualTo(GameEventStatus.PENDING);
    }

    @Test
    void markDoneRecordsWhenItHappened() {
        GameEvent event = pending();

        event.markDone(NOW);

        assertThat(event.getStatus()).isEqualTo(GameEventStatus.DONE);
        assertThat(event.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void aFailedAttemptKeepsTheEventPendingUntilTheLimit() {
        GameEvent event = pending();

        event.recordFailure("first", 3, NOW);
        event.recordFailure("second", 3, NOW);

        assertThat(event.getStatus()).isEqualTo(GameEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("second");
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void theLastAllowedAttemptMakesItFailedForGood() {
        GameEvent event = pending();

        event.recordFailure("one", 3, NOW);
        event.recordFailure("two", 3, NOW);
        event.recordFailure("three", 3, NOW);

        assertThat(event.getStatus()).isEqualTo(GameEventStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getLastError()).isEqualTo("three");
        assertThat(event.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void veryLongErrorTextsAreShortened() {
        GameEvent event = pending();

        event.recordFailure("x".repeat(5000), 3, NOW);

        assertThat(event.getLastError()).hasSize(2000);
    }

    @Test
    void onlyPendingEventsCanBeChanged() {
        GameEvent done = pending();
        done.markDone(NOW);

        assertThatThrownBy(() -> done.markDone(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> done.reschedule(DUE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> done.recordFailure("late", 3, NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
