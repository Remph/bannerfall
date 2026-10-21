package de.bannerfall.farm;

/**
 * What copying a farm list to another village did (1.6.1, 04-api <i>Farm helper</i>): every entry
 * of the source list is in exactly one of the four counts.
 *
 * @param copied entries added to the other list (same target, same template or own units)
 * @param alreadyThere entries whose target the other list has already (left as they were)
 * @param ownVillage entries passed over because the target is a village of the player's now,
 *     including the village the entries are copied to
 * @param listFull entries that did not fit: a list holds {@link FarmService#MAX_TARGETS}
 */
public record FarmCopyDto(int copied, int alreadyThere, int ownVillage, int listFull) {}
