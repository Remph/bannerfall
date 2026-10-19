package de.bannerfall.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * GET /api/auth/external (1.5): what happened at the last login through the account server in this
 * browser.
 *
 * @param name the name of a player who logged in there but has no player on this world yet: the
 *     join page asks for the invite code. Null otherwise
 * @param problem why the last login or link didn't work, shown once; null if it did
 * @param bannedUntil with {@code BANNED}: until when ({@code 9999-12-31} for good)
 * @param banReason with {@code BANNED}: why, if the host said
 */
public record ExternalLoginDto(
        @Schema(nullable = true) String name,
        @Schema(nullable = true) ExternalLoginProblem problem,
        @Schema(nullable = true) Instant bannedUntil,
        @Schema(nullable = true) String banReason) {}
