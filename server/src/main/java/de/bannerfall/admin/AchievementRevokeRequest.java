package de.bannerfall.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/players/{id}/achievements/{key}/revoke (M16): the optional reason goes into the
 * audit log.
 */
public record AchievementRevokeRequest(
        @Schema(nullable = true)
                @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters") String reason) {}
