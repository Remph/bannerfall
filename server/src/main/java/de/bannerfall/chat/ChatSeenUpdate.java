package de.bannerfall.chat;

/**
 * A seen marker that moved forward: goes to all connections of the player after commit, so his
 * other devices clear their badge ({@code /user/queue/chat-seen}).
 */
public record ChatSeenUpdate(long playerId, ChatSeenDto seen) {}
