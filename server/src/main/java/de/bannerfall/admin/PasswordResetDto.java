package de.bannerfall.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer of a password reset.
 *
 * @param generatedPassword the password the server made up, shown this once; null when the host
 *     gave one
 */
public record PasswordResetDto(
        long playerId, String name, @Schema(nullable = true) String generatedPassword) {}
