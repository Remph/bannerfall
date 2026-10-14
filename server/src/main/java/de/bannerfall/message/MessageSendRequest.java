package de.bannerfall.message;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/messages/with/{playerId}}; the text is checked by {@code ChatText}. */
record MessageSendRequest(@NotNull String text) {}
