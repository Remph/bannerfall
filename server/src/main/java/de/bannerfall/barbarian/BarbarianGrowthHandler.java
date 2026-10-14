package de.bannerfall.barbarian;

import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.rules.Barbarians;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The recurring growth round (02-game-rules §11): grows every barbarian village at the event's due
 * time and schedules the next round {@code 24 h / speed} after it. The next round is counted from
 * the due time, not the clock, so a server that was down catches up one round after the other. It
 * also keeps running while growth is switched off (then it only reschedules), so switching it on
 * again needs no restart.
 */
@Component
class BarbarianGrowthHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BarbarianGrowthHandler.class);

    private final BarbarianService barbarians;
    private final EventScheduler scheduler;
    private final WorldService worldService;

    BarbarianGrowthHandler(
            BarbarianService barbarians, EventScheduler scheduler, WorldService worldService) {
        this.barbarians = barbarians;
        this.scheduler = scheduler;
        this.worldService = worldService;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.BARBARIAN_GROWTH;
    }

    @Override
    public void handle(GameEvent event) {
        Instant at = event.getDueAt();
        int grown = barbarians.grow(at);
        Instant next = at.plus(Barbarians.growthInterval(worldService.settings()));
        scheduler.schedule(GameEventKind.BARBARIAN_GROWTH, null, next);
        log.info("Barbarian growth: {} villages grew, next round at {}", grown, next);
    }
}
