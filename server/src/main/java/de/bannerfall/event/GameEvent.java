package de.bannerfall.event;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * Something that must happen at an exact moment: a building finishes, troops arrive ... Rows are
 * processed strictly in {@code (dueAt, id)} order by the {@link EventProcessor}.
 *
 * <p>No {@code @Version}: whoever changes a row holds its row lock (see {@link EventProcessor} and
 * {@link EventScheduler}).
 */
@Entity
public class GameEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private GameEventKind kind;

    /** The build order, movement ... this event is about; no foreign key, may be null. */
    private Long refId;

    /** The game time at which the event happens. Handlers use this as "now". */
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    private GameEventStatus status;

    /** How often the handler has failed so far. */
    private int attempts;

    private String lastError;

    private Instant createdAt;

    /** When the event became DONE or FAILED. */
    private Instant processedAt;

    protected GameEvent() {}

    private GameEvent(GameEventKind kind, Long refId, Instant dueAt, Instant now) {
        this.kind = kind;
        this.refId = refId;
        this.dueAt = dueAt;
        this.status = GameEventStatus.PENDING;
        this.createdAt = now;
    }

    public static GameEvent pending(GameEventKind kind, Long refId, Instant dueAt, Instant now) {
        return new GameEvent(kind, refId, dueAt, now);
    }

    /** Moves a pending event to another time. */
    public void reschedule(Instant newDueAt) {
        requirePending();
        dueAt = newDueAt;
    }

    public void markDone(Instant now) {
        requirePending();
        status = GameEventStatus.DONE;
        processedAt = now;
    }

    /**
     * Counts a failed attempt. The event stays PENDING (and is tried again) until it has failed
     * {@code maxAttempts} times; then it is FAILED for good.
     */
    public void recordFailure(String error, int maxAttempts, Instant now) {
        requirePending();
        attempts++;
        lastError = truncate(error);
        if (attempts >= maxAttempts) {
            status = GameEventStatus.FAILED;
            processedAt = now;
        }
    }

    /** Puts a FAILED event back to PENDING with a fresh set of attempts (admin retry, M14a). */
    public void retry() {
        if (status != GameEventStatus.FAILED) {
            throw new IllegalStateException("Event " + id + " is " + status + ", not FAILED");
        }
        status = GameEventStatus.PENDING;
        attempts = 0;
        processedAt = null;
    }

    private void requirePending() {
        if (status != GameEventStatus.PENDING) {
            throw new IllegalStateException("Event " + id + " is " + status + ", not PENDING");
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public GameEventKind getKind() {
        return kind;
    }

    public Long getRefId() {
        return refId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public GameEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
