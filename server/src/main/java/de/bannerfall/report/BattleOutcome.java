package de.bannerfall.report;

/** How a battle ended (02-game-rules §7–§8). */
public enum BattleOutcome {
    /** A &gt; D: the defenders are dead, rams, catapults and loot did their work. */
    ATTACKER_WON,
    /** A ≤ D: the attackers are dead (a tie goes to the defender). */
    DEFENDER_WON,
    /** Only scouts attacked: there was no main battle, nobody won. */
    SCOUTS_ONLY
}
