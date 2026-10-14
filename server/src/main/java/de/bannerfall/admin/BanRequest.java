package de.bannerfall.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * POST /api/admin/players/{id}/ban (M14e).
 *
 * @param until the end of the ban, in the future; null = permanent
 * @param reason shown to the player when he tries to log in
 */
public record BanRequest(
        @Schema(nullable = true) Instant until,
        @NotBlank @Size(max = 200) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters") String reason) {}
