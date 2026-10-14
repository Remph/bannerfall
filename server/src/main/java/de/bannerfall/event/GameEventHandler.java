package de.bannerfall.event;

/**
 * Does the work of one {@link GameEventKind}. One bean per kind; the {@link EventHandlerRegistry}
 * finds it by {@link #kind()}.
 *
 * <p>Rules for handlers (01-architecture §4.6):
 *
 * <ul>
 *   <li>{@link #handle} runs inside the processor's transaction, together with marking the event
 *       DONE: if it throws, all its changes are rolled back and the attempt is counted.
 *   <li>The current game time is {@code event.getDueAt()}, <b>not</b> the clock: processing late
 *       (server was down) must give the same result as processing on time.
 *   <li>If the referenced row is gone (cancelled in the meantime), log at WARN and return.
 *   <li>Recurring events schedule their next occurrence inside the handler.
 * </ul>
 */
public interface GameEventHandler {

    GameEventKind kind();

    void handle(GameEvent event);
}
