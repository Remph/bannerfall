package de.bannerfall.victory;

import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The recurring victory check (02-game-rules §22): checks at the event's due time and schedules the
 * next check {@link VictoryService#CHECK_INTERVAL} after it, so a server that was down catches up
 * check by check. A world that has ended schedules none (the freeze stops it anyway).
 */
@Component
class VictoryCheckHandler implements GameEventHandler {

    private final VictoryService victory;
    private final EventScheduler scheduler;

    VictoryCheckHandler(VictoryService victory, EventScheduler scheduler) {
        this.victory = victory;
        this.scheduler = scheduler;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.VICTORY_CHECK;
    }

    @Override
    public void handle(GameEvent event) {
        Instant at = event.getDueAt();
        victory.check(at);
        scheduler.schedule(
                GameEventKind.VICTORY_CHECK, null, at.plus(VictoryService.CHECK_INTERVAL));
    }
}
