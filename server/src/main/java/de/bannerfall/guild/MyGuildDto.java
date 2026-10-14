package de.bannerfall.guild;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The player's guild state ({@code GET /api/guilds/mine}) and the answer of every request that
 * changes it.
 *
 * @param guild null if the player is in no guild
 * @param role the player's role in {@code guild}; null without a guild
 * @param invitations the invitations the player has received (always empty inside a guild: they are
 *     gone the moment he joins one)
 */
public record MyGuildDto(
        @Schema(nullable = true) GuildDto guild,
        @Schema(nullable = true) GuildRole role,
        List<GuildInvitationDto> invitations) {}
