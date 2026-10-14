package de.bannerfall.gamedata;

/**
 * The three numbers of a unit that a level changes (02-game-rules §5.4): attack and the two defence
 * values. Everything else about a unit (speed, carry, cost, population, recruit time) is the same
 * on every level.
 */
public record UnitStats(int attack, int defenseInfantry, int defenseCavalry) {}
