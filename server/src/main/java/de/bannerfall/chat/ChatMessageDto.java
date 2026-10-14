package de.bannerfall.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A chat message as the history ({@code GET /api/chat/messages}) and {@code /topic/chat} send it.
 *
 * @param playerId null for a broadcast by the host (M14d); the web shows it as a server message
 * @param playerName null for a broadcast
 */
public record ChatMessageDto(
        long id,
        @Schema(nullable = true) Long playerId,
        @Schema(nullable = true) String playerName,
        String text,
        Instant createdAt) {}
