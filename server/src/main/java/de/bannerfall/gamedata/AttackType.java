package de.bannerfall.gamedata;

/** Decides which defence value of the defender is used against a unit (02-game-rules §8). */
public enum AttackType {
    INFANTRY,
    CAVALRY,
    /** 1.6: the archers (02-game-rules §5.6), met by the defenders' defence against archers. */
    ARCHER
}
