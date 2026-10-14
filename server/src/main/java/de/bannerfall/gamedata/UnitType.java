package de.bannerfall.gamedata;

/** Canonical order of 02-game-rules §5.1 (UI columns, report tables, DB columns). Never reorder. */
public enum UnitType {
    SPEARMAN,
    SWORDSMAN,
    AXEMAN,
    SCOUT,
    LIGHT_CAVALRY,
    HEAVY_CAVALRY,
    RAM,
    CATAPULT,
    NOBLE,
    PALADIN,
    /** M32b (02-game-rules §21.5); appended, so after the paladin, who came first. */
    BANNER_THIEF
}
