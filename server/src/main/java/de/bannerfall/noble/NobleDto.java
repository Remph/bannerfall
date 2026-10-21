package de.bannerfall.noble;

import de.bannerfall.gamedata.ResourceAmounts;

/**
 * What the player's next noble costs and why (02-game-rules §5.7, {@code GET /api/noble}).
 *
 * @param villages villages the player owns now
 * @param nobles nobles he owns: at home, supporting, on the way, in training, waiting to be
 *     dismissed
 * @param freeRanks villages plus nobles that cost nothing extra (4)
 * @param rank {@code max(0, villages + nobles − freeRanks)}
 * @param step the world's step: each rank makes the price this share of the base price dearer; 0 =
 *     the flat price
 * @param maxFactor the price never goes above this many times the base price
 * @param factor the next noble's price as a multiple of the base price
 * @param baseCost the price of a noble at rank 0 (the game data)
 * @param cost the next noble's price
 * @param costAfter the price of the noble after it (one rank more once the free ranks are used)
 */
public record NobleDto(
        int villages,
        int nobles,
        int freeRanks,
        int rank,
        double step,
        double maxFactor,
        double factor,
        ResourceAmounts baseCost,
        ResourceAmounts cost,
        ResourceAmounts costAfter) {}
