package de.bannerfall.event;

public enum GameEventStatus {
    /** Waits for its {@code dueAt}, or for another attempt after a failure. */
    PENDING,
    /** The handler ran and its changes are committed. */
    DONE,
    /** Given up after too many failed attempts; kept for the admin to see, never retried. */
    FAILED
}
