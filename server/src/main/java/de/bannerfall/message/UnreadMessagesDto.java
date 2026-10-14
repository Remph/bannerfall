package de.bannerfall.message;

/**
 * {@code GET /api/messages/unread}: the number for the counter in the navigation.
 *
 * @param unread the player's unread messages, not counting those of blocked players
 */
public record UnreadMessagesDto(long unread) {}
