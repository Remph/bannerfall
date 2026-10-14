package de.bannerfall.account;

/**
 * The account's open sessions must end (M14e): banned, deleted, or its password was reset or
 * changed. Published inside the transaction of the change; listeners act after the commit (the
 * WebSocket connections of the player are closed, {@code PlayerConnections}). The HTTP sessions end
 * by themselves at their next request ({@code AccountGuardFilter}).
 */
public record SessionsEnded(long accountId) {}
