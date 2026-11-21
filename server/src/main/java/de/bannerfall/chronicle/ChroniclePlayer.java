package de.bannerfall.chronicle;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A player as the chronicle remembers him: his name and guild at that moment, copied so a rename or
 * a deleted player still reads (2.1 I1).
 *
 * @param guildId his guild then; null without one
 * @param guildTag its tag; null without one
 */
public record ChroniclePlayer(
        long id,
        String name,
        boolean npc,
        @Schema(nullable = true) Long guildId,
        @Schema(nullable = true) String guildTag) {}
