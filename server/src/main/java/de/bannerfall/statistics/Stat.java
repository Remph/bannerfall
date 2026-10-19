package de.bannerfall.statistics;

/**
 * The running totals of a player (02-game-rules §24). Stored by name in {@code player_stat}; the
 * order here is the order of the profile's statistics card. Kills and losses count population (02
 * §5.3), so a noble weighs more than a spearman.
 */
public enum Stat {
    /** Attacks with a main battle that he won. */
    ATTACKS_WON,
    /** Attacks with a main battle that he lost. */
    ATTACKS_LOST,
    /** Main battles in his village or where his support stood, won by the defenders. */
    DEFENCES_WON,
    /** Main battles in his village or where his support stood, won by the attacker. */
    DEFENCES_LOST,
    /** Population of the defenders his attacks killed (not his own support standing there). */
    KILLED_ATTACKING,
    /** His share of the population of the attackers killed where he defended. */
    KILLED_DEFENDING,
    /** Population of his own units that died, attacking or defending. */
    UNITS_LOST,
    /** Resources his attacks took. */
    LOOT_TAKEN,
    /** Resources attacks took from his villages. */
    LOOT_LOST,
    VILLAGES_CONQUERED,
    /** Villages conquered from him (not the ones the host turned barbarian). */
    VILLAGES_LOST,
    /** Units finished in his villages. */
    UNITS_RECRUITED,
    /** Building levels finished in his villages (not the levels catapults took). */
    BUILDING_LEVELS
}
