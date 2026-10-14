package de.bannerfall.guild;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /api/guilds}. The exact rules are {@link GuildText}'s; the sizes only cap input. */
public record CreateGuildRequest(
        @NotNull @Size(max = 100) String name, @NotNull @Size(max = 20) String tag) {}
