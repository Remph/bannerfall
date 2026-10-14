package de.bannerfall.abuse;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One account in {@code GET /api/admin/suspicious} (M14e).
 *
 * @param counts events per kind (kinds without events are missing)
 * @param lastAt the newest event
 * @param bannedUntil null when not banned (also when a ban is over)
 * @param recent the newest events, newest first
 */
public record SuspiciousAccountDto(
        long playerId,
        String name,
        Map<AbuseKind, Long> counts,
        long total,
        Instant lastAt,
        @Schema(nullable = true) Instant bannedUntil,
        @Schema(nullable = true) String banReason,
        List<AbuseEventDto> recent) {}
