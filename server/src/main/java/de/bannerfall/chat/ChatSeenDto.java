package de.bannerfall.chat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The newest chat message a player has seen in one channel ({@code GET/PUT /api/chat/seen}, and
 * pushed on {@code /user/queue/chat-seen} to his other devices when it moves forward).
 *
 * @param guildId null for the world chat, otherwise the guild whose chat it is
 * @param id the message id; messages up to it count as read
 */
public record ChatSeenDto(@Schema(nullable = true) Long guildId, long id) {}
