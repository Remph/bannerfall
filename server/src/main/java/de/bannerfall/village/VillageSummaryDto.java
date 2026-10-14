package de.bannerfall.village;

/**
 * One of the player's own villages in {@code /api/me}.
 *
 * @param incomingAttacks attacks on the way to the village (the web's red counter in the top bar,
 *     M9b). Only for the owner: a list of someone else's villages (player profiles, M14a) needs its
 *     own DTO without it.
 */
public record VillageSummaryDto(
        long id, String name, int x, int y, int points, int incomingAttacks) {}
