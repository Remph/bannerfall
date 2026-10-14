package de.bannerfall.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/banners/{id}/revoke (M32): the optional reason goes into the audit log and the
 * banner log.
 */
public record BannerRevokeRequest(
        @Schema(nullable = true)
                @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters") String reason) {}
