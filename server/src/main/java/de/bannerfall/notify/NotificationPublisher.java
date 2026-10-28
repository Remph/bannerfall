package de.bannerfall.notify;

import de.bannerfall.chat.BroadcastDto;
import de.bannerfall.chat.ChatHiddenDto;
import de.bannerfall.chat.ChatMessageDto;
import de.bannerfall.chat.ChatSeenUpdate;
import de.bannerfall.chat.GuildChatDelivery;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Everything the server pushes over STOMP leaves through here (01-architecture §4.8), and only
 * after the transaction that caused it has committed: a rolled-back request or event handler sends
 * nothing ({@code @TransactionalEventListener}, default phase AFTER_COMMIT). An event published
 * outside a transaction is dropped, so publish only from transactional code.
 *
 * <p>Nobody connected: the simple broker drops the message, the client loads the current state when
 * it comes back. A failing send is logged and never reaches the caller, whose transaction is done
 * already.
 */
@Component
class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    /** The STOMP user is the player id ({@code WebSocketConfig}). */
    static final String EVENTS = "/queue/events";

    static final String CHAT = "/topic/chat";

    /** The host's popup messages (1.7). */
    static final String BROADCAST = "/topic/broadcast";

    /** Per member, so a player who left or was kicked hears nothing more (M15b). */
    static final String GUILD_CHAT = "/queue/guild-chat";

    /** The player's read marker moved on another device (1.7). */
    static final String CHAT_SEEN = "/queue/chat-seen";

    private final SimpMessageSendingOperations messaging;

    private final SimpUserRegistry users;

    NotificationPublisher(SimpMessageSendingOperations messaging, SimpUserRegistry users) {
        this.messaging = messaging;
        this.users = users;
    }

    @TransactionalEventListener
    void send(PlayerNotification event) {
        try {
            sendToPlayer(event.playerId(), EVENTS, event.notification());
        } catch (RuntimeException e) {
            log.error("Could not send {} to player {}", event.notification(), event.playerId(), e);
        }
    }

    @TransactionalEventListener
    void send(ChatMessageDto message) {
        try {
            messaging.convertAndSend(CHAT, message);
        } catch (RuntimeException e) {
            log.error("Could not send chat message {}", message.id(), e);
        }
    }

    @TransactionalEventListener
    void send(BroadcastDto broadcast) {
        try {
            messaging.convertAndSend(BROADCAST, broadcast);
        } catch (RuntimeException e) {
            log.error("Could not send broadcast {}", broadcast.id(), e);
        }
    }

    @TransactionalEventListener
    void send(ChatHiddenDto hidden) {
        try {
            messaging.convertAndSend(CHAT, hidden);
        } catch (RuntimeException e) {
            log.error("Could not send the hiding of chat message {}", hidden.hiddenId(), e);
        }
    }

    @TransactionalEventListener
    void send(GuildChatDelivery delivery) {
        for (long playerId : delivery.playerIds()) {
            try {
                sendToPlayer(playerId, GUILD_CHAT, delivery.payload());
            } catch (RuntimeException e) {
                log.error("Could not send a guild chat message to player {}", playerId, e);
            }
        }
    }

    @TransactionalEventListener
    void send(ChatSeenUpdate update) {
        try {
            sendToPlayer(update.playerId(), CHAT_SEEN, update.seen());
        } catch (RuntimeException e) {
            log.error("Could not send the chat marker to player {}", update.playerId(), e);
        }
    }

    /**
     * Sends to every connection of the player, one message per connection. A single send to a
     * player with several connections (two tabs, or a phone whose old connection hasn't timed out
     * yet) reaches only the first one: with the receive order preserved ({@code WebSocketConfig}),
     * Spring's user destination handler hands the same message on to each connection, the first
     * hand-off makes it read-only and every further one fails with "Expected mutable
     * SimpMessageHeaderAccessor". A message with a session id goes to that connection alone.
     */
    private void sendToPlayer(long playerId, String destination, Object payload) {
        String user = Long.toString(playerId);
        SimpUser connected = users.getUser(user);
        Set<SimpSession> sessions = connected == null ? Set.of() : connected.getSessions();
        if (sessions.size() <= 1) {
            messaging.convertAndSendToUser(user, destination, payload);
            return;
        }
        for (SimpSession session : sessions) {
            SimpMessageHeaderAccessor headers =
                    SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(session.getId());
            headers.setLeaveMutable(true);
            messaging.convertAndSendToUser(user, destination, payload, headers.getMessageHeaders());
        }
    }
}
