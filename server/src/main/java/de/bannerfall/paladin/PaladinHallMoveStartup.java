package de.bannerfall.paladin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs {@link PaladinHallMove} at the start (it does something only the first time). Off in the
 * integration tests with the world initializer ({@code
 * bannerfall.world.initializer-enabled=false}): they share one database and call the move
 * themselves.
 */
@Component
class PaladinHallMoveStartup {

    private static final Logger log = LoggerFactory.getLogger(PaladinHallMoveStartup.class);

    private final PaladinHallMove move;
    private final boolean enabled;

    PaladinHallMoveStartup(
            PaladinHallMove move,
            @Value("${bannerfall.world.initializer-enabled:true}") boolean enabled) {
        this.move = move;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!enabled) {
            return;
        }
        try {
            int removed = move.run();
            if (removed >= 0) {
                log.info("Paladin moved into the Banner Hall: {} paladin(s) removed", removed);
            }
        } catch (RuntimeException e) {
            // Never stop the server for this: nothing was claimed, the next start tries again.
            log.error("Moving the paladins into the Banner Hall failed", e);
        }
    }
}
