package de.bannerfall.victory;

/**
 * Who can win a world (02-game-rules §22): a guild with all its members' villages, or a player on
 * his own (every player while guilds are off, and a player in no guild). NPC lords are players.
 */
public enum SideKind {
    GUILD,
    PLAYER
}
