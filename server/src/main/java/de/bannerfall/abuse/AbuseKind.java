package de.bannerfall.abuse;

/**
 * What the suspicion log records (M14e, 01-architecture §4.12): requests the web client never sends
 * in normal play. What a normal player runs into through timing (an order that just finished, the
 * cancel window just over, a stale screen) is never one of these.
 */
public enum AbuseKind {
    /** An id that exists but belongs to someone else; the answer stays 404. */
    FOREIGN_ID,
    /** A 400 for a field or parameter the web app already limits (count, name length ...). */
    INVALID_INPUT,
    /** A body the server can't read: broken JSON, an unknown enum value, a wrong type. */
    MALFORMED_BODY,
    /** The write rate limit refused requests (at most one event per minute and account). */
    RATE_LIMITED,
    /** A write without a valid CSRF token. */
    CSRF_FAILED,
    /** A STOMP frame to a destination the client may not use (dropped by the guard). */
    STOMP_REFUSED
}
