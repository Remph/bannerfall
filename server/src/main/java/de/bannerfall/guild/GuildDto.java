package de.bannerfall.guild;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A guild with its members (04-api {@code GET /api/guilds/{id}}); everybody may read any guild.
 *
 * @param points the sum of the members' points
 * @param invitations the open invitations, only for the leader of this very guild; null for
 *     everybody else
 */
public record GuildDto(
        long id,
        String name,
        String tag,
        String description,
        Instant createdAt,
        long points,
        List<GuildMemberDto> members,
        @Schema(nullable = true) List<GuildInvitationDto> invitations) {}
