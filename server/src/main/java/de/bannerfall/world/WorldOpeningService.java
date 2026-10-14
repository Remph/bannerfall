package de.bannerfall.world;

import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's control over the end of the pre-game (M14g): set, move or clear the opening time. The
 * time is kept in the settings row ({@code opens_at}) and as a pending {@code WORLD_OPEN} event
 * that {@link WorldOpenHandler} answers; both change in one transaction.
 */
@Service
public class WorldOpeningService {

    /** The {@code refId} of the WORLD_OPEN event: the one world row. */
    static final long WORLD_REF = 1;

    private final WorldService worldService;
    private final EventScheduler scheduler;
    private final Clock clock;

    WorldOpeningService(WorldService worldService, EventScheduler scheduler, Clock clock) {
        this.worldService = worldService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Sets the time the world opens, or takes it back ({@code null}: the world stays in the
     * pre-game until the host sets a time). A time in the past opens the world as soon as the event
     * worker runs.
     *
     * @throws de.bannerfall.common.ApiException {@code 409 WORLD_ALREADY_OPEN} once it is open
     */
    @Transactional
    public void schedule(Instant opensAt) {
        worldService.setOpensAt(opensAt);
        // cancel() skips an event the worker holds right now; that one opens the world, and the
        // handler ignores a second event for a world that is open.
        scheduler.cancel(GameEventKind.WORLD_OPEN, WORLD_REF);
        if (opensAt != null) {
            scheduler.schedule(GameEventKind.WORLD_OPEN, WORLD_REF, opensAt);
        }
    }

    /** Opens the world now (the event worker does it within a moment). */
    @Transactional
    public void openNow() {
        schedule(clock.instant());
    }

    /**
     * After a start: a pre-game world with a time needs its event (a lost or FAILED one comes back,
     * like the NPC rounds).
     */
    @Transactional
    public void ensureEvent() {
        Instant opensAt = worldService.opensAt();
        if (worldService.phase() == WorldPhase.PREGAME
                && opensAt != null
                && !scheduler.hasPending(GameEventKind.WORLD_OPEN)) {
            scheduler.schedule(GameEventKind.WORLD_OPEN, WORLD_REF, opensAt);
        }
    }
}
