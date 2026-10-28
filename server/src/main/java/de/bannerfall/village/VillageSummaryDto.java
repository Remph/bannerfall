package de.bannerfall.village;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the player's own villages in {@code /api/me}.
 *
 * @param incomingAttacks attacks on the way to the village (the web's red counter in the top bar,
 *     M9b). Only for the owner: a list of someone else's villages (player profiles, M14a) needs its
 *     own DTO without it.
 * @param tag the player's own tag on the village (1.7, 02-game-rules §26), null if he has none
 */
public record VillageSummaryDto(
        long id,
        String name,
        int x,
        int y,
        int points,
        int incomingAttacks,
        @Schema(nullable = true) String tag) {

    /** Without a tag; {@link VillageService#summariesOf} adds the tags. */
    public VillageSummaryDto(long id, String name, int x, int y, int points, int incomingAttacks) {
        this(id, name, x, y, points, incomingAttacks, null);
    }

    VillageSummaryDto withTag(String tag) {
        return new VillageSummaryDto(id, name, x, y, points, incomingAttacks, tag);
    }
}
