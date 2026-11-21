package de.bannerfall.npc;

import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * {@code NPC_ORDER_MARCH}, {@code refId} = the lord's player id (2.1 S3, 12-solo-and-allies §8):
 * the lord's planned "land together" march leaves. "Now" is the clock like the lord's round (09
 * §3): the command leaves when the worker gets to it, a second or two after its due time at most.
 */
@Component
class NpcOrderMarchHandler implements GameEventHandler {

    private final NpcOrders orders;
    private final Clock clock;

    NpcOrderMarchHandler(NpcOrders orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.NPC_ORDER_MARCH;
    }

    @Override
    public void handle(GameEvent event) {
        orders.march(event.getRefId(), clock.instant());
    }
}
