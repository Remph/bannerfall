package de.bannerfall.chat;

/**
 * Sent on {@code /topic/chat} when the admin hides a message (M14a): open chats remove it. Told
 * apart from a {@link ChatMessageDto} by its only field.
 */
public record ChatHiddenDto(long hiddenId) {}
