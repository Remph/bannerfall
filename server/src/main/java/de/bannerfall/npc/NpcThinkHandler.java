package de.bannerfall.npc;

import de.bannerfall.common.RandomSource;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code NPC_THINK}, {@code refId} = the lord's player id (09-npc-lords §3). The event's own
 * transaction only checks that the lord exists and has villages and schedules the next round;
 * {@link NpcRound} does the work in transactions of its own.
 *
 * <p>An exception to 01-architecture §4.6: "now" is the clock, not the due time. A late round is a
 * player who comes back late, and the player services take the time from the clock anyway.
 */
@Component
class NpcThinkHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NpcThinkHandler.class);

    private final PlayerRepository players;
    private final NpcRound round;
    private final NpcBalance balance;
    private final EventScheduler scheduler;
    private final WorldService worldService;
    private final RandomSource random;
    private final Clock clock;

    NpcThinkHandler(
            PlayerRepository players,
            NpcRound round,
            NpcBalance balance,
            EventScheduler scheduler,
            WorldService worldService,
            RandomSource random,
            Clock clock) {
        this.players = players;
        this.round = round;
        this.balance = balance;
        this.scheduler = scheduler;
        this.worldService = worldService;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.NPC_THINK;
    }

    @Override
    public void handle(GameEvent event) {
        long playerId = event.getRefId();
        Optional<Integer> villageCount = players.findNpcVillageCount(playerId);
        if (villageCount.isEmpty()) {
            log.warn("NPC_THINK for {}: no such NPC lord", playerId);
            return;
        }
        if (villageCount.get() == 0) {
            log.info("NPC lord {} has no villages and is out of the game", playerId);
            return;
        }
        Instant now = clock.instant();
        scheduler.schedule(GameEventKind.NPC_THINK, playerId, now.plusSeconds(nextDelaySeconds()));
        round.run(playerId, now);
    }

    /** The next round: {@code npcThinkMinutes ÷ speed} with jitter (09 §3). */
    private long nextDelaySeconds() {
        WorldSettings world = worldService.settings();
        long interval = NpcPlanning.thinkIntervalSeconds(world.npcThinkMinutes(), world.speed());
        return NpcPlanning.jitteredSeconds(interval, balance.general().thinkJitter(), random);
    }
}
