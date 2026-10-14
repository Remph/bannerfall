package de.bannerfall.village;

import de.bannerfall.gamedata.BannerType;

/**
 * The banner flying in a village of the viewer's own (02-game-rules §21): its type and tier, and
 * the tier it works at ({@code min(tier, Banner Hall)}; 0 = dormant).
 */
public record VillageBannerDto(BannerType type, int tier, int effectiveTier) {}
