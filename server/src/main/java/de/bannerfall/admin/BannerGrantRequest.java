package de.bannerfall.admin;

import de.bannerfall.gamedata.BannerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/players/{id}/banners (M32): gives a player a new banner in his stash. The
 * optional reason goes into the audit log and the banner log.
 */
public record BannerGrantRequest(
        @NotNull BannerType type,
        @Min(1) @Max(3) int tier,
        @Schema(nullable = true)
                @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters") String reason) {}
