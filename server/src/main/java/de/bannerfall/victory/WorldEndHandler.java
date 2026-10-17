package de.bannerfall.victory;

import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import org.springframework.stereotype.Component;

/**
 * The end date the host set has come (02-game-rules §22): the world ends, the side with the most
 * points wins. An event for a world that does not run, or before the (moved) end date, does
 * nothing.
 */
@Component
class WorldEndHandler implements GameEventHandler {

    private final VictoryService victory;

    WorldEndHandler(VictoryService victory) {
        this.victory = victory;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.WORLD_END;
    }

    @Override
    public void handle(GameEvent event) {
        victory.endDateReached(event.getDueAt());
    }
}
