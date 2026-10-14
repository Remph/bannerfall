package de.bannerfall.admin;

import de.bannerfall.account.Role;
import de.bannerfall.village.ProfileVillageDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A human player in {@code GET /api/admin/players} (08-host-console §2), with his villages (the
 * host cheats take village ids).
 *
 * @param lastLoginAt null if he never logged in since M14d
 * @param deleted the admin deleted the player (M14a): no login, no actions
 * @param bannedUntil the end of a ban that lasts now (M14e); null when not banned
 * @param banReason null when not banned
 */
public record AdminPlayerDto(
        long playerId,
        String name,
        Role role,
        int points,
        int villageCount,
        @Schema(nullable = true) Instant lastLoginAt,
        Instant createdAt,
        boolean deleted,
        @Schema(nullable = true) Instant bannedUntil,
        @Schema(nullable = true) String banReason,
        List<ProfileVillageDto> villages) {}
