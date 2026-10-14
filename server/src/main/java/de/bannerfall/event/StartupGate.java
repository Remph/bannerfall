package de.bannerfall.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Closed after a start until the event worker has caught up with every event that came due while
 * the server was down (code review 1.2, D3): game requests wait meanwhile ({@code
 * StartupGateFilter}, 503 {@code SERVER_STARTING}). Without it, a request could catch a village up
 * before its overdue events ran, and processing late would no longer give the same result
 * (01-architecture §4.6): a defender could send his troops away after the attack's time, a finished
 * building would produce at the old level until the worker got there.
 *
 * <p>Open from the start when the worker doesn't run by itself (tests call {@link
 * EventProcessor#processDue} by hand).
 */
@Component
public class StartupGate {

    private volatile boolean open;

    StartupGate(@Value("${bannerfall.events.scheduler-enabled:true}") boolean schedulerEnabled) {
        this.open = !schedulerEnabled;
    }

    public boolean isOpen() {
        return open;
    }

    /** The worker has handled everything that was due when it started. */
    void open() {
        open = true;
    }

    /** As right after a start (tests). */
    public void close() {
        open = false;
    }
}
