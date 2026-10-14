package de.bannerfall.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * A row of {@code GET /api/admin/audit}. {@code details} are whatever the action wrote (amounts,
 * the old and new role ...), keyed by name; never a password.
 */
public record AdminActionDto(
        long id,
        Instant at,
        String actor,
        String action,
        @Schema(nullable = true) String targetType,
        @Schema(nullable = true) Long targetId,
        Map<String, Object> details) {}
