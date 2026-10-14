package de.bannerfall.guild;

import java.time.Instant;

/** One member of a guild, leader first (04-api <i>Guilds</i>). */
public record GuildMemberDto(
        long playerId,
        String name,
        GuildRole role,
        int points,
        int villageCount,
        Instant joinedAt) {}
