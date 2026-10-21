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
    BANNER_THIEF,
    /**
     * 1.6 (02-game-rules §5.6), only in a world with archers; appended, so the stored order stays.
     * The web lists it after the axeman.
     */
    ARCHER,
    /** 1.6, like the archer; the web lists it after the light cavalry. */
    MOUNTED_ARCHER;

    /**
     * The two archers (1.6), recruited and researched only in a world with {@code archersEnabled}.
     */
    public boolean isArcher() {
        return this == ARCHER || this == MOUNTED_ARCHER;
    }
}
