package de.bannerfall.victory;

import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.rules.Victory;
import de.bannerfall.world.EndReason;
import de.bannerfall.world.VictoryMode;
import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldService.VictoryState;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a world is won and ends (v1.3, 02-game-rules §22): the victory check every few minutes, the
 * end date, the end itself (result, freeze, notification), the host's "end now" and taking an end
 * back, and what players see of it ({@link #victory()}).
 */
@Service
public class VictoryService {

    private static final Logger log = LoggerFactory.getLogger(VictoryService.class);

    /** The victory condition is checked this often (real time, not scaled by the speed). */
    public static final Duration CHECK_INTERVAL = Duration.ofMinutes(5);

    /** The {@code refId} of the WORLD_END event: the one world row. */
    static final long WORLD_REF = 1;

    /** How many sides {@link #victory()} lists while the world runs. */
    static final int SHOWN_SIDES = 10;

    private final WorldService worldService;
    private final StandingsQuery standingsQuery;
    private final WorldResultStore results;
    private final BarbarianService barbarians;
    private final FrozenTime frozenTime;
    private final EventScheduler scheduler;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    VictoryService(
            WorldService worldService,
            StandingsQuery standingsQuery,
            WorldResultStore results,
            BarbarianService barbarians,
            FrozenTime frozenTime,
            EventScheduler scheduler,
            JdbcClient jdbc,
            ApplicationEventPublisher events,
            Clock clock) {
        this.worldService = worldService;
        this.standingsQuery = standingsQuery;
        this.results = results;
        this.barbarians = barbarians;
        this.frozenTime = frozenTime;
        this.scheduler = scheduler;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    /**
     * After the world's content exists (every start, the opening): the banner keeps of a KEEPS
     * world, the recurring VICTORY_CHECK and the WORLD_END event of an end date, so a lost or
     * FAILED one comes back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensure(Instant now) {
        if (worldService.lockedVictoryState().phase() != WorldPhase.OPEN) {
            return;
        }
        WorldSettings world = worldService.settings();
        if (world.victoryMode() == VictoryMode.KEEPS) {
            int spawned = barbarians.ensureKeeps(world.keepCount()).size();
            if (spawned > 0) {
                log.info("{} banner keeps spawned", spawned);
            }
        }
        if (!scheduler.hasPending(GameEventKind.VICTORY_CHECK)) {
            scheduler.schedule(GameEventKind.VICTORY_CHECK, null, now.plus(CHECK_INTERVAL));
        }
        Instant endsAt = worldService.endsAt();
        if (endsAt != null && !scheduler.hasPending(GameEventKind.WORLD_END)) {
            scheduler.schedule(GameEventKind.WORLD_END, WORLD_REF, endsAt);
        }
    }

    /**
     * One victory check at {@code at} (the event's due time): spawns missing keeps (the host may
     * have switched to KEEPS or raised their number), finds the side that meets the condition,
     * moves the clock and ends the world when that side has held long enough.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(Instant at) {
        VictoryState state = worldService.lockedVictoryState();
        if (state.phase() != WorldPhase.OPEN) {
            return;
        }
        WorldSettings world = worldService.settings();
        if (world.victoryMode() == VictoryMode.KEEPS) {
            barbarians.ensureKeeps(world.keepCount());
        }
        Standings standings = standingsQuery.read(world.guildsEnabled());
        Optional<VictorySide> candidate = meetsCondition(world, standings);
        Victory.Clock next =
                Victory.next(
                        state.leader(),
                        state.leaderSince(),
                        candidate.map(VictorySide::key).orElse(null),
                        at);
        if (next.won(at, world.victoryHoldHours())) {
            EndReason reason =
                    world.victoryMode() == VictoryMode.KEEPS
                            ? EndReason.KEEPS
                            : EndReason.DOMINATION;
            end(reason, candidate.orElseThrow(), standings, at);
            return;
        }
        if (!Objects.equals(next.leader(), state.leader())
                || !Objects.equals(next.since(), state.leaderSince())) {
            worldService.setVictoryLeader(next.leader(), next.since());
        }
        if (!Objects.equals(next.leader(), state.leader())) {
            announceClock(
                    candidate.orElse(null),
                    next.leader() == null
                            ? null
                            : Victory.winsAt(next.since(), world.victoryHoldHours()),
                    side(standings, state.leader()));
        }
    }

    /**
     * Tells every player that the clock started, passed to {@code leader} or stopped (null), and
     * whether the sides in it are his own.
     */
    private void announceClock(VictorySide leader, Instant winsAt, VictorySide previous) {
        log.info(
                "Victory clock: {} (before: {})",
                leader == null ? "stopped" : leader.key() + " until " + winsAt,
                previous == null ? "nobody" : previous.key());
        for (Human human : humans()) {
            var notification =
                    new GameNotification.VictoryClock(
                            leader, winsAt, previous, human.owns(leader), human.owns(previous));
            events.publishEvent(new PlayerNotification(human.id(), notification));
        }
    }

    /** The side with that key in the standings; null for no key or a side without villages. */
    private static VictorySide side(Standings standings, String key) {
        return key == null
                ? null
                : standings.sides().stream()
                        .filter(side -> side.key().equals(key))
                        .findFirst()
                        .orElse(null);
    }

    /** The side that meets the world's victory condition now, if one does. */
    static Optional<VictorySide> meetsCondition(WorldSettings world, Standings standings) {
        return switch (world.victoryMode()) {
            case DOMINATION ->
                    standings
                            .aloneAhead(VictorySide::villages)
                            .filter(
                                    side ->
                                            Victory.dominates(
                                                    side.villages(),
                                                    standings.allVillages(),
                                                    world.dominationPercent()));
            case KEEPS ->
                    standings.allKeeps() == 0
                            ? Optional.empty()
                            : standings
                                    .aloneAhead(VictorySide::keeps)
                                    .filter(
                                            side ->
                                                    side.keeps()
                                                            >= Victory.keepsNeeded(
                                                                    standings.allKeeps(),
                                                                    world.keepPercent()));
            case NONE -> Optional.empty();
        };
    }

    /**
     * The end date has come (event WORLD_END at {@code at}): the side with the most points wins.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void endDateReached(Instant at) {
        VictoryState state = worldService.lockedVictoryState();
        if (state.phase() != WorldPhase.OPEN
                || state.endsAt() == null
                || at.isBefore(state.endsAt())) {
            log.info("WORLD_END ignored: the world does not run or the end date was moved");
            return;
        }
        Standings standings = standingsQuery.read(worldService.settings().guildsEnabled());
        end(EndReason.END_DATE, standings.best().orElse(null), standings, at);
    }

    private void end(EndReason reason, VictorySide winner, Standings standings, Instant at) {
        if (!worldService.markEnded(at)) {
            return;
        }
        List<VictorySide> sides =
                standings.sides().stream().limit(WorldResultStore.MAX_SIDES).toList();
        results.save(new WorldResultDto(reason, at, winner, sides));
        log.info(
                "The world has ended ({}): {} won",
                reason,
                winner == null ? "nobody" : winner.key() + " " + winner.name());
        var notification =
                new GameNotification.WorldEnded(reason, winner == null ? null : winner.name());
        for (long playerId : humanIds()) {
            events.publishEvent(new PlayerNotification(playerId, notification));
        }
    }

    /**
     * Sets, moves or clears (null) the end date (the host). A time in the past ends the world now,
     * as soon as the event worker runs: the end is never dated back, or the freeze would hold back
     * nothing of what has happened since.
     *
     * @throws ApiException {@code 409 WORLD_ENDED} once it has ended
     */
    @Transactional
    public void setEndsAt(Instant requested) {
        Instant now = clock.instant();
        Instant endsAt = requested != null && requested.isBefore(now) ? now : requested;
        worldService.setEndsAt(endsAt);
        // cancel() skips an event the worker holds right now; that one ends the world, and the
        // handler ignores a second one for a world that has ended.
        scheduler.cancel(GameEventKind.WORLD_END, WORLD_REF);
        if (endsAt != null) {
            scheduler.schedule(GameEventKind.WORLD_END, WORLD_REF, endsAt);
        }
    }

    /** Ends the world now (the host): the event worker does it within a moment. */
    @Transactional
    public void endNow() {
        setEndsAt(clock.instant());
    }

    /**
     * Takes the end back (the host): the world runs again without an end date, the victory clock
     * starts from zero, and everything picks up where it stopped ({@link FrozenTime}).
     *
     * @throws ApiException {@code 409 WORLD_NOT_ENDED} while it runs
     */
    @Transactional
    public void reopen() {
        Instant endedAt = worldService.lockedVictoryState().endedAt();
        if (!worldService.markReopened()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.WORLD_NOT_ENDED, "The world has not ended");
        }
        frozenTime.resume(endedAt, clock.instant());
        results.delete();
        scheduler.cancel(GameEventKind.WORLD_END, WORLD_REF);
        if (!scheduler.hasPending(GameEventKind.VICTORY_CHECK)) {
            scheduler.schedule(
                    GameEventKind.VICTORY_CHECK, null, clock.instant().plus(CHECK_INTERVAL));
        }
        log.info("The host took the end of the world back");
        var notification = new GameNotification.WorldOpened();
        for (long playerId : humanIds()) {
            events.publishEvent(new PlayerNotification(playerId, notification));
        }
    }

    /** What players see: the rules, the leading sides and the clock, or the result. */
    @Transactional(readOnly = true)
    public VictoryDto victory() {
        WorldSettings world = worldService.settings();
        WorldPhase phase = worldService.phase();
        Optional<WorldResultDto> result =
                phase == WorldPhase.ENDED ? results.load() : Optional.empty();
        Standings standings =
                phase == WorldPhase.OPEN
                        ? standingsQuery.read(world.guildsEnabled())
                        : new Standings(List.of(), 0, 0);
        VictoryState state = phase == WorldPhase.OPEN ? worldService.victoryState() : null;
        VictorySide leader =
                state == null || state.leader() == null
                        ? null
                        : standings.sides().stream()
                                .filter(side -> side.key().equals(state.leader()))
                                .findFirst()
                                .orElse(null);
        int keepCount =
                jdbc.sql("select count(*) from village where keep").query(Integer.class).single();
        return new VictoryDto(
                world.victoryMode(),
                world.dominationPercent(),
                world.victoryHoldHours(),
                keepCount,
                world.keepPercent(),
                Victory.keepsNeeded(
                        keepCount > 0 ? keepCount : world.keepCount(), world.keepPercent()),
                worldService.endsAt(),
                phase,
                standings.allVillages(),
                shownSides(world.victoryMode(), standings.sides()),
                leader,
                leader == null ? null : state.leaderSince(),
                leader == null
                        ? null
                        : Victory.winsAt(state.leaderSince(), world.victoryHoldHours()),
                result.orElse(null));
    }

    private static List<VictorySide> shownSides(VictoryMode mode, List<VictorySide> sides) {
        Comparator<VictorySide> order =
                switch (mode) {
                    case DOMINATION ->
                            Comparator.comparingInt(VictorySide::villages)
                                    .reversed()
                                    .thenComparing(Standings.BY_POINTS);
                    case KEEPS ->
                            Comparator.comparingInt(VictorySide::keeps)
                                    .reversed()
                                    .thenComparing(
                                            Comparator.comparingInt(VictorySide::villages)
                                                    .reversed())
                                    .thenComparing(Standings.BY_POINTS);
                    case NONE -> Standings.BY_POINTS;
                };
        return sides.stream().sorted(order).limit(SHOWN_SIDES).toList();
    }

    /** A human player and his guild (null without one), to tell whether a side is his own. */
    private record Human(long id, Long guildId) {

        boolean owns(VictorySide side) {
            if (side == null) {
                return false;
            }
            return switch (side.kind()) {
                case PLAYER -> side.id() == id;
                case GUILD -> guildId != null && side.id() == guildId;
            };
        }
    }

    private List<Human> humans() {
        return jdbc.sql(
                        """
                        select p.id, m.guild_id from player p
                        left join guild_member m on m.player_id = p.id
                        where p.npc = false order by p.id
                        """)
                .query(
                        (rs, row) ->
                                new Human(rs.getLong("id"), rs.getObject("guild_id", Long.class)))
                .list();
    }

    private List<Long> humanIds() {
        return jdbc.sql("select id from player where npc = false order by id")
                .query(Long.class)
                .list();
    }
}
