package de.bannerfall.village;

import de.bannerfall.player.Player;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A village inside other objects: movements, troops, later reports (04-api "Village refs").
 *
 * @param name null for barbarian villages (the client shows a localized name)
 * @param playerId null for barbarian villages
 * @param playerName null for barbarian villages
 */
public record VillageRefDto(
        long id,
        @Schema(nullable = true) String name,
        int x,
        int y,
        @Schema(nullable = true) Long playerId,
        @Schema(nullable = true) String playerName) {

    /** Reads the owner's name, so the player is loaded if it isn't yet (inside a transaction). */
    public static VillageRefDto of(Village village) {
        Player player = village.getPlayer();
        return new VillageRefDto(
                village.getId(),
                village.getName(),
                village.getX(),
                village.getY(),
                player == null ? null : player.getId(),
                player == null ? null : player.getName());
    }
}
