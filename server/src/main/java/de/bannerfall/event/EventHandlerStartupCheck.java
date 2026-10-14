package de.bannerfall.event;

import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Stops the server at startup if events are waiting for which no handler exists (for example after
 * rolling back to an older version). Better than failing them one by one at runtime. Runs once all
 * beans exist, and before the scheduler starts its first tick.
 */
@Component
class EventHandlerStartupCheck implements SmartInitializingSingleton {

    private final GameEventRepository events;
    private final EventHandlerRegistry handlers;

    EventHandlerStartupCheck(GameEventRepository events, EventHandlerRegistry handlers) {
        this.events = events;
        this.handlers = handlers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<GameEventKind> waiting =
                events.findPendingKinds().stream().map(EventHandlerStartupCheck::toKind).toList();
        handlers.requireHandlersFor(waiting);
    }

    private static GameEventKind toKind(String name) {
        try {
            return GameEventKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Events of kind "
                            + name
                            + " are waiting in game_event, but this version doesn't know that"
                            + " kind. Deploy the version that added it or delete those events.",
                    e);
        }
    }
}
