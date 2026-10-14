package de.bannerfall.world;

import de.bannerfall.account.Account;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.npc.NpcLordService;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.Barbarians;
import de.bannerfall.village.SpawnService;
import de.bannerfall.village.Village;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills a new world when the server starts (02-game-rules §2, §11): the first start ever spawns the
 * initial barbarian villages ({@code world_settings.initialized_at} makes sure that happens only
 * once), then the NPC lords ({@code npcLordCount}, 09-npc-lords §2). Every start makes sure the
 * recurring BARBARIAN_GROWTH event exists and that every NPC lord with villages has a pending
 * NPC_THINK round, so a lost or FAILED one comes back.
 *
 * <p>A fresh production server starts in the pre-game instead ({@code
 * bannerfall.world.start-in-pregame}, M14g): nothing is created until the world opens ({@link
 * #open}, event WORLD_OPEN); the players who registered meanwhile get their village then.
 *
 * <p>The integration tests switch it off ({@code bannerfall.world.initializer-enabled=false}): they
 * share one database and call {@link #initialize()} themselves.
 */
@Component
public class WorldInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorldInitializer.class);

    private final WorldService worldService;
    private final BarbarianService barbarians;
    private final NpcLordService npcLords;
    private final EventScheduler scheduler;
    private final WorldOpeningService opening;
    private final SpawnService spawns;
    private final PlayerRepository players;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean enabled;
    private final boolean startInPregame;

    WorldInitializer(
            WorldService worldService,
            BarbarianService barbarians,
            NpcLordService npcLords,
            EventScheduler scheduler,
            WorldOpeningService opening,
            SpawnService spawns,
            PlayerRepository players,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${bannerfall.world.initializer-enabled:true}") boolean enabled,
            @Value("${bannerfall.world.start-in-pregame:false}") boolean startInPregame) {
        this.worldService = worldService;
        this.barbarians = barbarians;
        this.npcLords = npcLords;
        this.scheduler = scheduler;
        this.opening = opening;
        this.spawns = spawns;
        this.players = players;
        this.events = events;
        this.clock = clock;
        this.enabled = enabled;
        this.startInPregame = startInPregame;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStart() {
        if (enabled) {
            if (startInPregame && worldService.enterPregame()) {
                log.info("The world is in the pre-game");
            }
            initialize();
        }
    }

    /**
     * One transaction: the initial villages and the "initialized" mark commit together. In the
     * pre-game it only makes sure the opening event exists.
     */
    @Transactional
    public void initialize() {
        // The stored phase, not the cached one: onStart() has just put the row into the pre-game
        // in this very transaction, and a request that came in while the server was starting may
        // have cached "open" from the row as it was.
        if (worldService.lockedPhase() == WorldPhase.PREGAME) {
            opening.ensureEvent();
            return;
        }
        create(clock.instant());
    }

    /**
     * The end of the pre-game (event WORLD_OPEN): the same creation as at the first start, then a
     * village for every player who registered meanwhile, oldest first, with beginner protection
     * counted from now. A player the map has no room for stays without a village and sees the
     * respawn screen, which tries again.
     */
    @Transactional
    public void open() {
        create(clock.instant());
    }

    private void create(Instant now) {
        WorldSettings world = worldService.settings();
        if (worldService.claimInitialization(now)) {
            int spawned = barbarians.spawnInitial(world.barbarianInitialCount()).size();
            log.info("New world: {} barbarian villages spawned", spawned);
            int lords = npcLords.spawnInitial(world.npcLordCount()).size();
            log.info("New world: {} NPC lords spawned", lords);
            placeWaitingPlayers(world, now);
        }
        npcLords.ensureRounds();
        if (!scheduler.hasPending(GameEventKind.BARBARIAN_GROWTH)) {
            Instant first = now.plus(Barbarians.growthInterval(world));
            scheduler.schedule(GameEventKind.BARBARIAN_GROWTH, null, first);
            log.info("Barbarian growth scheduled for {}", first);
        }
    }

    private void placeWaitingPlayers(WorldSettings world, Instant now) {
        Duration protection = Duration.ofHours(world.beginnerProtectionHours());
        int placed = 0;
        for (Player player : players.findWaitingForVillage(Account.PERMANENT_BAN)) {
            player.protectUntil(now.plus(protection));
            Optional<Village> village = spawns.tryCreateStartingVillage(player);
            if (village.isEmpty()) {
                log.warn(
                        "No room on the map for {}; they can try again on the respawn screen",
                        player.getName());
            } else {
                barbarians.spawnAround(village.get().coordinates(), world.barbariansPerNewPlayer());
                placed++;
            }
            // villageless too: the client reloads and sees the respawn screen
            events.publishEvent(
                    new PlayerNotification(player.getId(), new GameNotification.WorldOpened()));
        }
        if (placed > 0) {
            log.info("New world: {} villages for the players who registered before", placed);
        }
    }
}
