package de.bannerfall.gamedata;

/**
 * The nine banners of 02-game-rules §21.2, in the order of the table there (UI lists, DB column
 * {@code banner.type}). Never reorder; new types go to the end.
 */
public enum BannerType {
    HARVEST,
    BUILDERS,
    DRILL,
    WAR,
    BULWARK,
    HASTE,
    TREASURY,
    PLUNDER,
    OATH
}
