package de.bannerfall.message;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One line of the inbox: the other player and the newest message between the two.
 *
 * @param guildTag the other player's guild tag (M15a), null if he is in none
 * @param lastMine true if the caller wrote the newest message
 * @param unread the other player's messages the caller hasn't read
 */
public record ConversationDto(
        long playerId,
        String playerName,
        @Schema(nullable = true) String guildTag,
        String lastText,
        Instant lastAt,
        boolean lastMine,
        int unread) {}
