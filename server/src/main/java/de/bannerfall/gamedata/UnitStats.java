package de.bannerfall.gamedata;

/**
 * The numbers of a unit that a level changes (02-game-rules §5.4): attack and the three defence
 * values (against archers since 1.6). Everything else about a unit (speed, carry, cost, population,
 * recruit time) is the same on every level.
 */
public record UnitStats(int attack, int defenseInfantry, int defenseCavalry, int defenseArcher) {

    /** The defence against an attacker of this class (02-game-rules §8). */
    public int defenseAgainst(AttackType type) {
        return switch (type) {
            case INFANTRY -> defenseInfantry;
            case CAVALRY -> defenseCavalry;
            case ARCHER -> defenseArcher;
        };
    }
}
