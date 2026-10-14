package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;

/** "Barracks level 5": a building and the level it needs. */
public record Requirement(BuildingType building, int level) {}
