package de.bannerfall.npc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/admin/npc-lords/{playerId}/guild} (09-npc-lords §13.7): the admin places a lord.
 * JOIN moves it into the lord guild {@code guildId} (out of its own first), LEAVE takes it out,
 * FOUND starts a new lord guild with it alone; all three mark it as placed, so the forming leaves
 * it alone. AUTO clears the mark: the lord chooses again.
 */
public record NpcGuildRequest(@NotNull Action action, @Schema(nullable = true) Long guildId) {

    public enum Action {
        JOIN,
        LEAVE,
        FOUND,
        AUTO
    }
}
