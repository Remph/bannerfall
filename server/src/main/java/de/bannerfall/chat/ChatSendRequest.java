package de.bannerfall.chat;

/**
 * The body of a STOMP SEND to {@code /app/chat.send}. Checked by {@link ChatService#send}: STOMP
 * messages don't go through bean validation.
 */
record ChatSendRequest(String text) {}
