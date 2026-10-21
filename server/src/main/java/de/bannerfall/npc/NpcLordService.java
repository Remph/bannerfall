package de.bannerfall.npc;

import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.RandomSource;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.village.SpawnService;
import de.bannerfall.village.Village;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates NPC lords (09-npc-lords §2): a player row without account, a starting village with
 * barbarian villages around it like a human gets, the brain and the first decision round. Spawned
 * at world start by {@code WorldInitializer}; the admin spawns more ({@code NpcAdminService}).
 */
@Service
public class NpcLordService {

    private static final Logger log = LoggerFactory.getLogger(NpcLordService.class);

    /** EN/DE-neutral own names; every one has a space, so it can't collide with a username. */
    static final List<String> NAMES =
            List.of(
                    "Lord Aldric",
                    "Gräfin Hedda",
                    "Baron Wulfram",
                    "Lady Rowena",
                    "Graf Ottmar",
                    "Dame Isolde",
                    "Ritter Kuno",
                    "Lady Mathilde",
                    "Sir Edmund",
                    "Baronin Adela",
                    "Graf Hagen",
                    "Lady Gisela",
                    "Sir Roderick",
                    "Gräfin Irmgard",
                    "Baron Leofric",
                    "Dame Elsbeth",
                    "Ritter Volker",
                    "Lord Cedric",
                    "Lady Brunhild",
                    "Graf Eckhart",
                    "Gräfin Walburga",
                    "Baron Osric",
                    "Lady Ermengard",
                    "Sir Godwin",
                    "Dame Hildegard",
                    "Ritter Gunther",
                    "Lord Wilfried",
                    "Lady Adelheid",
                    "Graf Dietmar",
                    "Baronin Liutgard");

    private final PlayerRepository players;
    private final NpcBrainRepository brains;
    private final SpawnService spawnService;
    private final BarbarianService barbarians;
    private final EventScheduler scheduler;
    private final WorldService worldService;
    private final NpcBalance balance;
    private final RandomSource random;
    private final Clock clock;

    NpcLordService(
            PlayerRepository players,
            NpcBrainRepository brains,
            SpawnService spawnService,
            BarbarianService barbarians,
            EventScheduler scheduler,
            WorldService worldService,
            NpcBalance balance,
            RandomSource random,
            Clock clock) {
        this.players = players;
        this.brains = brains;
        this.spawnService = spawnService;
        this.barbarians = barbarians;
        this.scheduler = scheduler;
        this.worldService = worldService;
        this.balance = balance;
        this.random = random;
        this.clock = clock;
    }

    /**
     * Spawns {@code count} lords, personalities in the order of 09 §2. A lord that finds no room is
     * skipped with a WARN (the world starts anyway).
     *
     * @return the lords that were created
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Player> spawnInitial(int count) {
        List<Player> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<Player> lord = spawn(NpcPersonality.ofNth(brains.count()));
            if (lord.isEmpty()) {
                log.warn(
                        "No room for NPC lord {} of {}, the others are spawned anyway",
                        i + 1,
                        count);
                continue;
            }
            created.add(lord.get());
        }
        return created;
    }

    /**
     * Spawns one lord of this personality: a random unused name, the player row with the
     * difficulty's production factor and no beginner protection, a starting village, {@code
     * barbariansPerNewPlayer} barbarian villages around it, the brain, and the first decision round
     * at a random moment within one interval (so lords don't act in lockstep).
     *
     * @return empty if the map has no room for another player village
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Player> spawn(NpcPersonality personality) {
        WorldSettings world = worldService.settings();
        Instant now = clock.instant();
        double factor = NpcBalance.productionFactor(balance.difficulty(), NpcMemory.Stance.NONE);
        Player lord = players.save(Player.npcLord(pickName(), factor, now));
        Optional<Village> village = spawnService.tryCreateStartingVillage(lord, false);
        if (village.isEmpty()) {
            players.delete(lord);
            return Optional.empty();
        }
        barbarians.spawnAround(village.get().coordinates(), world.barbariansPerNewPlayer());
        brains.save(new NpcBrain(lord.getId(), personality));
        scheduleFirstRound(lord.getId(), now);
        log.info(
                "NPC lord {} spawned ({}) at {}",
                lord.getName(),
                personality,
                village.get().coordinates());
        return Optional.of(lord);
    }

    /**
     * Makes sure every lord with villages has a pending round (start-up safety net, 09 §3): a lost
     * or FAILED one comes back at the next start.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureRounds() {
        Instant now = clock.instant();
        for (long lordId : players.findNpcLordIdsWithoutPendingRound()) {
            scheduleFirstRound(lordId, now);
            log.info("NPC lord {} had no pending round, one is scheduled", lordId);
        }
    }

    private void scheduleFirstRound(long lordId, Instant now) {
        WorldSettings world = worldService.settings();
        long interval = NpcPlanning.thinkIntervalSeconds(world.npcThinkMinutes(), world.speed());
        long delay = Math.round(random.nextDouble(0, interval));
        scheduler.schedule(GameEventKind.NPC_THINK, lordId, now.plusSeconds(delay));
    }

    /**
     * A random unused name; when every name is taken the list is used again with " II", " III", …
     * appended (09 §2).
     */
    private String pickName() {
        Set<String> taken = new HashSet<>(players.findNpcNames());
        for (int round = 1; ; round++) {
            String suffix = round == 1 ? "" : " " + roman(round);
            List<String> free =
                    NAMES.stream().map(n -> n + suffix).filter(n -> !taken.contains(n)).toList();
            if (!free.isEmpty()) {
                return free.get(random.nextInt(0, free.size() - 1));
            }
        }
    }

    static String roman(int n) {
        String[] symbols = {"X", "IX", "V", "IV", "I"};
        int[] values = {10, 9, 5, 4, 1};
        StringBuilder text = new StringBuilder();
        int rest = n;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                text.append(symbols[i]);
                rest -= values[i];
            }
        }
        return text.toString();
    }
}
