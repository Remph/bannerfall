package de.bannerfall.chat;

import java.time.Instant;

/**
 * A message of the host that pops up on every screen (1.7): sent on {@code /topic/broadcast} and
 * listed by {@code GET /api/broadcasts} for players who connect shortly after.
 *
 * @param id when it was sent, in milliseconds since 1970 and always above the id before: the web
 *     remembers the newest one the player dismissed, so ids must keep growing across a restart
 * @param text 1–500 characters, trimmed
 */
public record BroadcastDto(long id, String text, Instant sentAt) {}
