package de.bannerfall;

import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The handler of the event kind {@code TEST}, for the event engine's tests: remembers what it
 * handled, can be told to run code first (it runs inside the processor's transaction, like a real
 * handler) and to fail for chosen subjects. One instance is shared by all integration tests: call
 * {@link #reset()} before using it.
 */
public class TestEventHandler implements GameEventHandler {

    /** What the handler saw: the event's id, subject and due time. */
    public record Handled(long eventId, Long refId, Instant dueAt) {}

    private final List<Handled> handled = new CopyOnWriteArrayList<>();
    private final Set<Long> failingRefs = ConcurrentHashMap.newKeySet();
    private volatile Consumer<GameEvent> beforeHandling = event -> {};

    @Override
    public GameEventKind kind() {
        return GameEventKind.TEST;
    }

    @Override
    public void handle(GameEvent event) {
        beforeHandling.accept(event);
        if (failingRefs.contains(event.getRefId())) {
            throw new IllegalStateException("test failure for ref " + event.getRefId());
        }
        handled.add(new Handled(event.getId(), event.getRefId(), event.getDueAt()));
    }

    /** Events handled successfully, in the order they were handled. */
    public List<Handled> handled() {
        return List.copyOf(handled);
    }

    /** The subjects ({@code refId}) of the handled events, in order. */
    public List<Long> handledRefs() {
        return handled.stream().map(Handled::refId).toList();
    }

    /** Makes the handler throw for events with this subject. */
    public void failFor(long refId) {
        failingRefs.add(refId);
    }

    /** Runs this first for every event, before the handler fails or succeeds. */
    public void beforeHandling(Consumer<GameEvent> action) {
        beforeHandling = action;
    }

    public void reset() {
        handled.clear();
        failingRefs.clear();
        beforeHandling = event -> {};
    }
}
