package de.bannerfall.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * {@code PUT /api/chat/seen}: the player has seen the messages up to {@code id}.
 *
 * @param guildId null for the world chat, otherwise his current guild
 */
public record ChatSeenRequest(@Schema(nullable = true) Long guildId, @Min(1) long id) {}
