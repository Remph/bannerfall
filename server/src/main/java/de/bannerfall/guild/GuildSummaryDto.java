package de.bannerfall.guild;

/** One line of the guild list (04-api {@code GET /api/guilds}); points = sum over the members. */
public record GuildSummaryDto(long id, String name, String tag, long memberCount, long points) {}
