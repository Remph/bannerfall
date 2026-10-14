package de.bannerfall.world;

import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The end of the pre-game (M14g): creates the world's content and the players' villages ({@link
 * WorldInitializer#open}). An event that arrives for a world that is open already, or before the
 * (moved) opening time, does nothing.
 */
@Component
class WorldOpenHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WorldOpenHandler.class);

    private final WorldService worldService;
    private final WorldInitializer initializer;
    private final Clock clock;

    WorldOpenHandler(WorldService worldService, WorldInitializer initializer, Clock clock) {
        this.worldService = worldService;
        this.initializer = initializer;
        this.clock = clock;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.WORLD_OPEN;
    }

    @Override
    public void handle(GameEvent event) {
        Instant opensAt = worldService.opensAt();
        if (worldService.phase() != WorldPhase.PREGAME
                || opensAt == null
                || clock.instant().isBefore(opensAt)) {
            log.info("WORLD_OPEN ignored: the world is open already or the time was moved");
            return;
        }
        initializer.open();
    }
}
