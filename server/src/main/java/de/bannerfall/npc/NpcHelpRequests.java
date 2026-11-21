package de.bannerfall.npc;

import de.bannerfall.gamedata.UnitType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The human's side of help requests (2.1 S4, 12-solo-and-allies §9) for the API: his open requests
 * and "Not this time". Answering them is an ordinary support or shipment, seen in the reports.
 */
@Component
public class NpcHelpRequests {

    private final NpcHelpStore store;
    private final NpcHelp help;
    private final NpcHelpSettings settings;

    NpcHelpRequests(NpcHelpStore store, NpcHelp help, NpcHelpSettings settings) {
        this.store = store;
        this.help = help;
        this.settings = settings;
    }

    /** The human's open requests, nearest deadline first (inside the caller's transaction). */
    public List<NpcHelpStore.Request> open(long playerId) {
        return store.openFor(playerId);
    }

    /** "Not this time" (−5 trust); false when the request isn't his open one any more. */
    @Transactional
    public boolean decline(long playerId, long requestId, Instant now) {
        return help.decline(playerId, requestId, now);
    }

    /**
     * The defenders the sheet fills in from a village with {@code home} units at home for a request
     * of {@code wanted} (spearmen, swordsmen, archers, heavy cavalry): the fastest first, and only
     * the ones that land within {@code secondsLeft} while any does.
     *
     * @param seconds the travel time of one unit of a type to the lord's village
     */
    public Map<UnitType, Integer> fill(
            Map<UnitType, Integer> home,
            int wanted,
            java.util.function.ToLongFunction<UnitType> seconds,
            long secondsLeft) {
        return NpcHelpRules.fill(home, NpcHelp.DEFENDERS, wanted, seconds, secondsLeft);
    }

    /** Whether nothing the player sends could land in time: no trust lost by a no (review). */
    public boolean outOfReach(NpcHelpStore.Request request) {
        return help.outOfReach(request);
    }

    /** What an answer is worth, for the sheet. */
    public NpcHelpSettings settings() {
        return settings;
    }
}
