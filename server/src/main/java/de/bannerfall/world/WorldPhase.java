package de.bannerfall.world;

/**
 * Where the world is in its life (02-game-rules §1b). {@link #PREGAME}: the map has not been
 * created, players can register and chat but have no village. {@link #OPEN}: the game runs. {@link
 * #ENDED} (v1.3, §22): a side won or the end date came; the world is frozen, everything stays
 * readable and the chat stays open.
 */
public enum WorldPhase {
    PREGAME,
    OPEN,
    ENDED
}
