package de.bannerfall.world;

/**
 * Where the world is in its life (02-game-rules §1b). {@link #PREGAME}: the map has not been
 * created, players can register and chat but have no village. {@link #OPEN}: the game runs.
 */
public enum WorldPhase {
    PREGAME,
    OPEN
}
