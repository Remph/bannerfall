package de.bannerfall;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * Records every message the server hands to the STOMP broker (the {@code brokerChannel}), so tests
 * can check what was pushed without a WebSocket client. A message to a player's queue is recorded
 * under {@code /user/{playerId}/queue/events}, whether or not the player is connected. Sending is
 * synchronous, so a message is recorded before the call that caused it returns.
 */
public class SentMessages implements ChannelInterceptor {

    /** One message: destination and payload (JSON text). */
    public record Sent(String destination, String json) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    public SentMessages(@Qualifier("brokerChannel") AbstractSubscribableChannel brokerChannel) {
        brokerChannel.addInterceptor(this);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination != null && message.getPayload() instanceof byte[] bytes) {
            sent.add(new Sent(destination, new String(bytes, StandardCharsets.UTF_8)));
        }
        return message;
    }

    /** The notifications sent to the player so far, oldest first, as JSON text. */
    public List<String> toPlayer(long playerId) {
        String destination = "/user/" + playerId + "/queue/events";
        return sent.stream()
                .filter(message -> message.destination().equals(destination))
                .map(Sent::json)
                .toList();
    }

    /** Everything sent to {@code /topic/chat} so far, oldest first. */
    public List<String> chat() {
        return sent.stream()
                .filter(message -> message.destination().equals("/topic/chat"))
                .map(Sent::json)
                .toList();
    }

    /** Everything sent to the player's guild chat queue so far, oldest first (M15b). */
    public List<String> guildChat(long playerId) {
        String destination = "/user/" + playerId + "/queue/guild-chat";
        return sent.stream()
                .filter(message -> message.destination().equals(destination))
                .map(Sent::json)
                .toList();
    }
}
