package de.bannerfall.chat;

import java.util.List;

/**
 * Something for the open guild chats (M15b): a {@link ChatMessageDto} or a {@link ChatHiddenDto},
 * for these players. The members are looked up in the transaction that causes the event, so a
 * member who leaves a moment later still gets what was said while he was in.
 */
public record GuildChatDelivery(List<Long> playerIds, Object payload) {}
