package de.bannerfall.event;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Finds the {@link GameEventHandler} of an event kind. Spring passes in every handler bean; two
 * handlers for the same kind stop the server at startup. (That every kind with waiting events has a
 * handler is checked by {@link EventHandlerStartupCheck}.)
 */
@Component
public class EventHandlerRegistry {

    private final Map<GameEventKind, GameEventHandler> byKind = new EnumMap<>(GameEventKind.class);

    EventHandlerRegistry(List<GameEventHandler> handlers) {
        for (GameEventHandler handler : handlers) {
            GameEventHandler previous = byKind.put(handler.kind(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two handlers for event kind "
                                + handler.kind()
                                + ": "
                                + previous.getClass().getName()
                                + " and "
                                + handler.getClass().getName());
            }
        }
    }

    /**
     * @throws IllegalStateException if the kind has no handler
     */
    public GameEventHandler get(GameEventKind kind) {
        GameEventHandler handler = byKind.get(kind);
        if (handler == null) {
            throw new IllegalStateException("No handler for event kind " + kind);
        }
        return handler;
    }

    /**
     * @throws IllegalStateException naming the kinds that have no handler
     */
    void requireHandlersFor(Collection<GameEventKind> kinds) {
        List<GameEventKind> missing =
                kinds.stream().filter(k -> !byKind.containsKey(k)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Events of kind "
                            + missing
                            + " are waiting in game_event but nothing handles them. Add the"
                            + " handler or delete those events.");
        }
    }
}
