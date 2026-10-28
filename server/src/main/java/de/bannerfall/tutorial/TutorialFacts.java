package de.bannerfall.tutorial;

/**
 * What the tutorial needs to know about a player's game: the highest level of a building in any of
 * his villages, and a few counts.
 *
 * @param spearmen spearmen the player owns (an order still in the queue does not count)
 * @param attackSent an attack of his was sent (on the way or reported; a copy of another player's
 *     report does not count)
 * @param attackReportRead a report of one of his attacks has been opened
 * @param farmListEntries targets on the farm lists of his villages
 */
public record TutorialFacts(
        int woodcutter,
        int headquarters,
        int barracks,
        int market,
        int warehouse,
        int smithy,
        int academy,
        int bannerHall,
        int villages,
        int spearmen,
        boolean attackSent,
        boolean attackReportRead,
        int farmListEntries,
        boolean inGuild) {}
