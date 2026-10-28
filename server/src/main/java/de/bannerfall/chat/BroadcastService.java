package de.bannerfall.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's popup messages (1.7, 08-host-console §2), e.g. "update in 5 minutes". Not a chat
 * message: nothing goes to the database and the chat stays as it is. Pushed to everyone connected
 * on {@code /topic/broadcast} after commit, and kept in memory for {@link #KEEP}, so a player who
 * logs in (or reconnects) meanwhile still gets it once. A restart forgets them.
 */
@Service
public class BroadcastService {

    /** How long a broadcast is handed to players who connect after it was sent. */
    public static final Duration KEEP = Duration.ofMinutes(10);

    private static final int MAX_KEPT = 20;

    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final Deque<BroadcastDto> kept = new ArrayDeque<>();
    private long lastId;

    BroadcastService(Clock clock, ApplicationEventPublisher events) {
        this.clock = clock;
        this.events = events;
    }

    /**
     * Keeps the message and pushes it to everyone after commit.
     *
     * @throws de.bannerfall.common.ApiException 400 {@code VALIDATION} as for a chat message
     */
    @Transactional
    public synchronized BroadcastDto send(String text) {
        String trimmed = ChatText.checked(text);
        Instant now = clock.instant();
        lastId = Math.max(lastId + 1, now.toEpochMilli());
        BroadcastDto broadcast = new BroadcastDto(lastId, trimmed, now);
        kept.addLast(broadcast);
        while (kept.size() > MAX_KEPT) {
            kept.removeFirst();
        }
        events.publishEvent(broadcast);
        return broadcast;
    }

    /** The broadcasts of the last {@link #KEEP}, oldest first. */
    public synchronized List<BroadcastDto> recent() {
        Instant oldest = clock.instant().minus(KEEP);
        kept.removeIf(broadcast -> broadcast.sentAt().isBefore(oldest));
        return List.copyOf(kept);
    }
}
