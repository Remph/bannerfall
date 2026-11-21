package de.bannerfall.message;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One line of the inbox: the other player and the newest message between the two.
 *
 * @param guildTag the other player's guild tag (M15a), null if he is in none
 * @param lastMine true if the caller wrote the newest message
 * @param unread the other player's messages the caller hasn't read
 * @param npc the other player is an NPC lord (2.1 S2b): it writes letters and reads nothing
 * @param lastLetter the newest message is a lord's letter: what it says ({@code lastText} is empty)
 */
public record ConversationDto(
        long playerId,
        String playerName,
        @Schema(nullable = true) String guildTag,
        String lastText,
        Instant lastAt,
        boolean lastMine,
        int unread,
        boolean npc,
        @Schema(nullable = true) LordLetter lastLetter) {}
