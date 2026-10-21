package de.bannerfall.building;

import de.bannerfall.gamedata.BuildingType;

/**
 * The owner queued a demolition (02-game-rules §4.5), published inside its transaction; the village
 * automation pauses if its plan builds that level again (§25).
 *
 * @param level the building's level once the demolition is done
 */
public record DemolitionQueued(long villageId, long playerId, BuildingType building, int level) {}
