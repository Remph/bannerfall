package de.bannerfall.automation;

import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * {@code AUTOMATION_CHECK}, {@code refId} = the village (02-game-rules §25, 1.6). The look itself
 * runs in transactions of its own ({@link AutomationService#run}); the event's transaction only
 * schedules the next one.
 *
 * <p>An exception to 01-architecture §4.6, like {@code NPC_THINK}: "now" is the clock, not the due
 * time. A late look is a player who looks late, and the build and recruit services take the time
 * from the clock anyway.
 */
@Component
class AutomationCheckHandler implements GameEventHandler {

    private final AutomationService automation;

    AutomationCheckHandler(AutomationService automation) {
        this.automation = automation;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.AUTOMATION_CHECK;
    }

    @Override
    public void handle(GameEvent event) {
        long villageId = event.getRefId();
        Instant next = automation.run(villageId);
        if (next != null) {
            automation.scheduleLook(villageId, next, event.getId());
        }
    }
}
