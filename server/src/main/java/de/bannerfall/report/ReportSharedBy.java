package de.bannerfall.report;

/**
 * Where a copy of a report came from (M40, 02-game-rules §14): the player who sent it and the
 * player whose report it was first. "You" in the payload means the second one; for a report its
 * owner sent, both are the same player.
 */
public record ReportSharedBy(long playerId, String name, long viewOfPlayerId, String viewOfName) {}
