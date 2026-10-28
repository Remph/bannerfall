package de.bannerfall.rules;

/**
 * What the guild banner of a village owner's guild does for his villages right now (02-game-rules
 * §16.4): fractions, 0 = not switched on (the level is too low, the world has guild banners off, or
 * the owner is in no guild). Pure value, the rules take its numbers.
 *
 * @param production level 1: production of wood, clay and iron +
 * @param support level 2: travel time of supports sent to a village of a guild mate −
 * @param defence level 3: defence of the village +
 */
public record GuildEffect(double production, double support, double defence) {

    /** No effect. */
    public static final GuildEffect NONE = new GuildEffect(0, 0, 0);
}
