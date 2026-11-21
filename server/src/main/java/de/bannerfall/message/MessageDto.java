package de.bannerfall.message;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One private message in a conversation (04-api <i>Private messages</i>).
 *
 * @param mine true if the caller wrote it
 * @param text empty for a letter of an NPC lord
 * @param letter what a lord's letter says (2.1 S2b); null for a message a player wrote
 */
public record MessageDto(
        long id,
        boolean mine,
        String text,
        Instant createdAt,
        @Schema(nullable = true) LordLetter letter) {

    /** A message a player wrote. */
    public MessageDto(long id, boolean mine, String text, Instant createdAt) {
        this(id, mine, text, createdAt, null);
    }
}
