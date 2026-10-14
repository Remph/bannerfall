package de.bannerfall.guild;

import java.time.Instant;

/**
 * An invitation, seen by the invited player (in {@link MyGuildDto#invitations}) and by the leader
 * of the inviting guild ({@link GuildDto#invitations}).
 */
public record GuildInvitationDto(
        long id,
        long guildId,
        String guildName,
        String guildTag,
        long playerId,
        String playerName,
        String invitedByName,
        Instant createdAt) {}
