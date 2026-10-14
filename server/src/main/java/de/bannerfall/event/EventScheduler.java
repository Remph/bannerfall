package de.bannerfall.event;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, moves and removes events. All methods run inside the caller's transaction (there must be
 * one), so an event is committed or rolled back together with the thing it belongs to: a build
 * order and its BUILD_COMPLETE event either both exist or neither.
 *
 * <p>{@link #reschedule} and {@link #cancel} never wait for the worker. If the worker is processing
 * the event right now, it holds the event's row lock and then waits for the village lock that the
 * caller already holds: waiting here as well would be a deadlock (lock order event → village in the
 * worker, village → event in the caller). Instead such an event is left alone and the methods
 * answer {@code false}. For a cancel that is fine: the handler finds its row gone and returns (see
 * {@link GameEventHandler}). A caller that reschedules must treat {@code false} as "too late, it
 * already happens at the old time".
 */
@Service
public class EventScheduler {

    private final GameEventRepository events;
    private final Clock clock;

    EventScheduler(GameEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /** Schedules a new event. {@code refId} may be null for events without a subject. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GameEvent schedule(GameEventKind kind, Long refId, Instant dueAt) {
        return events.save(GameEvent.pending(kind, refId, dueAt, clock.instant()));
    }

    /**
     * Moves the pending events of this kind and subject to a new time.
     *
     * @return false if there was nothing to move: the event is done, was never scheduled, or the
     *     worker holds it right now
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reschedule(GameEventKind kind, long refId, Instant newDueAt) {
        List<GameEvent> pending = events.lockPendingSkippingLocked(kind.name(), refId);
        pending.forEach(event -> event.reschedule(newDueAt));
        return !pending.isEmpty();
    }

    /**
     * Removes the pending events of this kind and subject.
     *
     * @return false if there was nothing to remove (see {@link #reschedule})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancel(GameEventKind kind, long refId) {
        List<GameEvent> pending = events.lockPendingSkippingLocked(kind.name(), refId);
        events.deleteAll(pending);
        return !pending.isEmpty();
    }

    /** Whether an event of this kind is waiting (for recurring events without a subject). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasPending(GameEventKind kind) {
        return events.existsPending(kind.name());
    }
}
