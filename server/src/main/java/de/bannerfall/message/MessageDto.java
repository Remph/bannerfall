package de.bannerfall.message;

import java.time.Instant;

/**
 * One private message in a conversation (04-api <i>Private messages</i>).
 *
 * @param mine true if the caller wrote it
 */
public record MessageDto(long id, boolean mine, String text, Instant createdAt) {}
