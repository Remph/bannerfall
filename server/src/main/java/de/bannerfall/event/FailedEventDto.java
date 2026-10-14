package de.bannerfall.event;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A FAILED event for the admin list ({@code GET /api/admin/events/failed}). */
public record FailedEventDto(
        long id,
        GameEventKind kind,
        @Schema(nullable = true) Long refId,
        Instant dueAt,
        int attempts,
        @Schema(nullable = true) String lastError,
        @Schema(nullable = true) Instant processedAt) {

    static FailedEventDto of(GameEvent event) {
        return new FailedEventDto(
                event.getId(),
                event.getKind(),
                event.getRefId(),
                event.getDueAt(),
                event.getAttempts(),
                event.getLastError(),
                event.getProcessedAt());
    }
}
