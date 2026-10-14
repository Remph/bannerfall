package de.bannerfall.guild;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/guilds/mine/description}; the length rule is {@link GuildText}'s. */
public record DescriptionRequest(@NotNull @Size(max = 5000) String description) {}
