package de.bannerfall.event;

import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The event worker (01-architecture §4.6): runs the due events strictly in {@code (dueAt, id)}
 * order, one transaction per event.
 *
 * <p>Every event runs in its own transaction that starts with the event's row lock and a status
 * check, so an event is never applied twice. If the handler throws, that transaction is rolled back
 * completely and the failure is written in a second one: the attempt is counted, and after {@value
 * #MAX_ATTEMPTS} attempts the event is FAILED. A failing event never blocks the others.
 *
 * <p>A world that has ended is frozen (v1.3, 02-game-rules §22): only events due at the end or
 * before still run, everything later stays pending (and runs if the host takes the end back).
 */
@Component
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    /** How many events are fetched at once. */
    static final int BATCH_SIZE = 50;

    /** After this many failed attempts an event is given up (FAILED). */
    static final int MAX_ATTEMPTS = 3;

    /** The MDC key for the event being handled; logback-spring.xml prints it. */
    static final String MDC_KEY = "event";

    private enum Outcome {
        DONE,
        /** Not pending or not due any more when we got to it: cancelled, handled or moved. */
        SKIPPED,
        FAILED
    }

    private final GameEventRepository events;
    private final EventHandlerRegistry handlers;
    private final Clock clock;
    private final StartupGate startupGate;
    private final WorldService worldService;

    /** REQUIRES_NEW: every event gets its own transaction, even if the caller has one open. */
    private final TransactionTemplate transaction;

    EventProcessor(
            GameEventRepository events,
            EventHandlerRegistry handlers,
            Clock clock,
            PlatformTransactionManager transactionManager,
            StartupGate startupGate,
            WorldService worldService) {
        this.events = events;
        this.worldService = worldService;
        this.handlers = handlers;
        this.clock = clock;
        this.startupGate = startupGate;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs every 200 ms; switched off by {@code bannerfall.events.scheduler-enabled=false}. The
     * first tick that gets through after a start has handled everything due at that moment: then
     * game requests are let in ({@link StartupGate}).
     */
    @Scheduled(fixedDelay = 200)
    public void tick() {
        try {
            int processed = processDue(clock.instant());
            if (!startupGate.isOpen()) {
                startupGate.open();
                log.info(
                        "Caught up after the start ({} events): game requests are open", processed);
            }
        } catch (RuntimeException e) {
            // For example the database is down. The next tick tries again.
            log.error("Event tick failed", e);
        }
    }

    /**
     * Handles all PENDING events due at {@code until} or earlier, oldest first, including events
     * that handlers schedule meanwhile and that are due as well. Tests call this directly.
     *
     * <p>An event that fails is tried at most once per call; its next attempt is the next call's.
     *
     * @return how many events were handled successfully
     */
    public int processDue(Instant until) {
        Instant endedAt = worldService.endedAt();
        if (endedAt != null && endedAt.isBefore(until)) {
            until = endedAt;
        }
        int processed = 0;
        // Failed in this call but still PENDING (attempts left): they would be fetched again and
        // again, so they are skipped until the next call.
        Set<Long> failed = new HashSet<>();
        while (true) {
            List<Long> ids =
                    events.findDueIds(until, BATCH_SIZE + failed.size()).stream()
                            .filter(id -> !failed.contains(id))
                            .toList();
            if (ids.isEmpty()) {
                return processed;
            }
            for (long id : ids) {
                switch (processOne(id, until)) {
                    case DONE -> processed++;
                    case FAILED -> failed.add(id);
                    case SKIPPED -> {}
                }
            }
        }
    }

    private Outcome processOne(long id, Instant until) {
        // Every log line written while this event runs (handler, failure) shows "event=<id>".
        try (MDC.MDCCloseable _ = MDC.putCloseable(MDC_KEY, Long.toString(id))) {
            try {
                boolean handled =
                        Boolean.TRUE.equals(transaction.execute(status -> handleLocked(id, until)));
                return handled ? Outcome.DONE : Outcome.SKIPPED;
            } catch (RuntimeException e) {
                recordFailure(id, e);
                return Outcome.FAILED;
            }
        }
    }

    /**
     * The body of the event's transaction. Locks the row first and only then looks at the status:
     * two workers (or a worker and a test) racing for the same event serialize here, and the second
     * one finds it DONE. The due time is checked again too: the id list is fetched before, and the
     * event may have been moved later meanwhile (by an earlier handler of this call or by a request
     * that committed while we waited for the lock). It must not run before its time.
     *
     * @return false if there was nothing to do: the event is gone, no longer pending or not due
     */
    private boolean handleLocked(long id, Instant until) {
        GameEvent event = events.findByIdForUpdate(id).orElse(null);
        if (event == null
                || event.getStatus() != GameEventStatus.PENDING
                || event.getDueAt().isAfter(until)) {
            return false;
        }
        // An earlier event of this call may have ended the world (§22): later ones wait.
        Instant endedAt = worldService.endedAt();
        if (endedAt != null && event.getDueAt().isAfter(endedAt)) {
            return false;
        }
        handlers.get(event.getKind()).handle(event);
        event.markDone(clock.instant());
        return true;
    }

    /** In a transaction of its own: the one of the failed attempt is already rolled back. */
    private void recordFailure(long id, RuntimeException cause) {
        try {
            transaction.executeWithoutResult(status -> countFailedAttempt(id, cause));
        } catch (RuntimeException e) {
            // A failure that can't be written must not stop the other events. This event stays
            // PENDING, unchanged, and is tried again by the next call.
            log.error("Event {} failed", id, cause);
            log.error("Could not record the failure of event {}", id, e);
        }
    }

    private void countFailedAttempt(long id, RuntimeException cause) {
        GameEvent event = events.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getStatus() != GameEventStatus.PENDING) {
            return;
        }
        event.recordFailure(describe(cause), MAX_ATTEMPTS, clock.instant());
        if (event.getStatus() == GameEventStatus.FAILED) {
            // The stack trace once, with the final verdict.
            log.error(
                    "Event {} ({}, ref {}) given up after {} attempts",
                    id,
                    event.getKind(),
                    event.getRefId(),
                    event.getAttempts(),
                    cause);
        } else {
            log.warn(
                    "Event {} ({}, ref {}) failed, attempt {} of {}: {}",
                    id,
                    event.getKind(),
                    event.getRefId(),
                    event.getAttempts(),
                    MAX_ATTEMPTS,
                    event.getLastError());
        }
    }

    private static String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
