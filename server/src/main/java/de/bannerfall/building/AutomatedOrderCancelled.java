package de.bannerfall.building;

/**
 * The owner cancelled a build order the village automation had placed (02-game-rules §25),
 * published inside the cancel's transaction; the automation pauses in that village.
 */
public record AutomatedOrderCancelled(long villageId) {}
