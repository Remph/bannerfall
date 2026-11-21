package de.bannerfall.solo;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerConnections;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.victory.FrozenTime;
import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldService.ClockState;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A solo world sleeps while nobody plays (2.1, 12-solo-and-allies §2, §3; 02-game-rules §28): two
 * minutes after the last game connection of a human closed it stands still like an ended world
 * ({@link WorldService#gameTime}, the event worker's cut-off), and the first game request or
 * connection of a human wakes it: {@link FrozenTime} moves every game clock on by the time asleep,
 * so nothing happened while he was away and everything keeps the time it had left.
 *
 * <p>The host's speed control: pause (asleep until he presses play, whoever comes), play, and fast
 * forward ×2 or ×4, which every {@value #STEP_SECONDS} seconds moves every game clock another 10 or
 * 30 seconds closer. Each move tells the humans {@code WORLD_TIME_MOVED}, so their clients load
 * everything again.
 *
 * <p>Time the server stood still (stopped, or the PC suspended) is no game time either (2.1 feature
 * review, finding 01): while an open solo world is awake, every tick writes a heartbeat ({@code
 * world_settings.heartbeat_at}). When the next tick, request or event round finds the heartbeat
 * more than {@link #GAP} old, the world falls asleep at the heartbeat, so the wake moves every game
 * clock on by the time the server stood still. A clean shutdown falls asleep at once.
 *
 * <p>Every change holds the settings row's lock ({@link WorldService#lockedClockState}), so falling
 * asleep, the wake, pause, play and a fast step never overlap.
 */
@Service
public class WorldSleep {

    private static final Logger log = LoggerFactory.getLogger(WorldSleep.class);

    /** How long the game must be closed before the world sleeps (12 §3). */
    public static final Duration IDLE = Duration.ofMinutes(2);

    /** The fast-forward step: the tick's interval in seconds. */
    public static final int STEP_SECONDS = 10;

    /**
     * A heartbeat older than this means the server stood still (a stop or a suspend): three ticks
     * missed.
     */
    public static final Duration GAP = Duration.ofSeconds(30);

    /** {@link #lastBeat} before the database was asked: check it at the first chance. */
    private static final Instant UNKNOWN = Instant.EPOCH;

    private final WorldService worldService;
    private final FrozenTime frozenTime;
    private final PlayerConnections connections;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    /** The last game request or open connection of a human; the start counts as one. */
    private volatile Instant lastActivity;

    /**
     * The heartbeat this server last wrote (null = none, {@link #UNKNOWN} = not read yet): only a
     * cheap gate in front of the stored one, which decides.
     */
    private volatile Instant lastBeat = UNKNOWN;

    WorldSleep(
            WorldService worldService,
            FrozenTime frozenTime,
            PlayerConnections connections,
            Clock clock,
            ApplicationEventPublisher events,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager) {
        this.worldService = worldService;
        this.frozenTime = frozenTime;
        this.connections = connections;
        this.clock = clock;
        this.events = events;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.lastActivity = clock.instant();
    }

    /**
     * Every {@value #STEP_SECONDS} seconds (with the event worker's scheduler): falls asleep after
     * {@link #IDLE} without a human, makes a fast-forward step, and wakes a world that is no solo
     * world any more.
     */
    @Scheduled(fixedDelay = STEP_SECONDS * 1000L, initialDelay = STEP_SECONDS * 1000L)
    public void tick() {
        try {
            step();
        } catch (RuntimeException e) {
            log.error("World sleep tick failed", e);
        }
    }

    /** One tick at the clock's time. Tests call it directly. */
    public void step() {
        Instant now = clock.instant();
        if (afterGap(now)) {
            return;
        }
        if (connections.anyOpen()) {
            lastActivity = now;
        }
        if (!worldService.settings().soloMode()) {
            if (worldService.asleepSince() != null) {
                // the host switched solo off: it runs again, a pause included
                wake(now, true);
            }
            clearHeartbeat();
            return;
        }
        if (worldService.phase() != WorldPhase.OPEN || worldService.asleepSince() != null) {
            clearHeartbeat();
            return;
        }
        if (Duration.between(lastActivity, now).compareTo(IDLE) >= 0) {
            fallAsleep(now);
            return;
        }
        if (worldService.clockSpeed() > 1) {
            fastStep(now);
        }
        beat(now);
    }

    /**
     * Before the event worker's round (which would otherwise run the events of a stop or suspend at
     * once): falls asleep at the heartbeat when the server stood still since.
     */
    public void beforeEvents() {
        try {
            afterGap(clock.instant());
        } catch (RuntimeException e) {
            log.error("World sleep check before the events failed", e);
        }
    }

    /** A clean shutdown: an open solo world that is awake falls asleep now. */
    @PreDestroy
    void shutdown() {
        try {
            Instant now = clock.instant();
            transaction.executeWithoutResult(
                    status -> {
                        ClockState state = worldService.lockedClockState();
                        if (state.soloMode()
                                && state.phase() == WorldPhase.OPEN
                                && state.asleepSince() == null) {
                            worldService.setAsleep(now, false);
                            writeHeartbeat(null);
                            log.info("The server stops: the world sleeps");
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Could not put the world to sleep at shutdown: {}", e.toString());
        }
    }

    /**
     * A game request or connection of a human (the filter): wakes the world unless the host paused
     * it. Returns once the wake is committed, so the request sees the moved clocks.
     */
    public void activity() {
        Instant now = clock.instant();
        // a request right after a suspend: the time stood still first, then he wakes it
        afterGap(now);
        lastActivity = now;
        if (worldService.asleepSince() != null && !worldService.paused()) {
            wake(now, false);
        }
    }

    /** The host's pause (12 §3): asleep now, and it stays asleep until he presses play. */
    public void pause() {
        Instant now = clock.instant();
        transaction.executeWithoutResult(
                status -> {
                    ClockState state = requireSoloAndOpen();
                    if (state.paused()) {
                        return;
                    }
                    worldService.setAsleep(
                            state.asleepSince() == null ? now : state.asleepSince(), true);
                    writeHeartbeat(null);
                    log.info("The host paused the world");
                    notifyHumans();
                });
    }

    /** The host's play: a paused world wakes, the time paused is moved on. */
    public void play() {
        transaction.executeWithoutResult(status -> requireSoloAndOpen());
        Instant now = clock.instant();
        lastActivity = now;
        wake(now, true);
    }

    /** The host's fast forward: 1 (normal), 2 or 4. */
    public void setSpeed(int speed) {
        if (speed != 1 && speed != 2 && speed != 4) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "The speed is 1, 2 or 4, not " + speed);
        }
        transaction.executeWithoutResult(
                status -> {
                    requireSoloAndOpen();
                    worldService.setClockSpeed(speed);
                    log.info("The host set the world's clock to x{}", speed);
                });
    }

    /** The state the host's speed control shows. */
    public WorldClockDto state() {
        return new WorldClockDto(
                worldService.settings().soloMode(),
                worldService.asleepSince(),
                worldService.paused(),
                worldService.clockSpeed());
    }

    private ClockState requireSoloAndOpen() {
        ClockState state = worldService.lockedClockState();
        if (!state.soloMode()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.WORLD_NOT_SOLO,
                    "Only a solo world can be paused or sped up");
        }
        if (state.phase() == WorldPhase.ENDED) {
            throw WorldService.ended();
        }
        if (state.phase() == WorldPhase.PREGAME) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.WORLD_NOT_OPEN, "The world has not opened yet");
        }
        return state;
    }

    private void fallAsleep(Instant now) {
        transaction.executeWithoutResult(
                status -> {
                    ClockState state = worldService.lockedClockState();
                    if (state.asleepSince() != null
                            || Duration.between(lastActivity, now).compareTo(IDLE) < 0) {
                        return;
                    }
                    worldService.setAsleep(now, false);
                    writeHeartbeat(null);
                    log.info("Nobody plays: the world sleeps");
                });
    }

    /**
     * The server stood still (stopped or suspended) since its last heartbeat: the world falls
     * asleep at the heartbeat, so that time is no game time. Returns whether it fell asleep. Only
     * asks the database when the heartbeat this server knows is older than {@link #GAP}.
     */
    private boolean afterGap(Instant now) {
        Instant known = lastBeat;
        if (known == null || Duration.between(known, now).compareTo(GAP) <= 0) {
            return false;
        }
        // The gate (lastBeat) changes only once the answer is committed and the settings are
        // reloaded: set earlier, another thread (the event round, a request) would skip the check
        // and work from the old settings while the world falls asleep at the old heartbeat.
        Gap gap =
                transaction.execute(
                        status -> {
                            ClockState state = worldService.lockedClockState();
                            Instant beat = storedHeartbeat();
                            if (!state.soloMode()
                                    || state.phase() != WorldPhase.OPEN
                                    || state.asleepSince() != null
                                    || beat == null) {
                                if (beat != null) {
                                    clearStoredHeartbeat();
                                }
                                return new Gap(false, state.asleepSince() != null, null);
                            }
                            if (Duration.between(beat, now).compareTo(GAP) <= 0) {
                                return new Gap(false, false, beat);
                            }
                            worldService.setAsleep(beat, false);
                            clearStoredHeartbeat();
                            log.info(
                                    "The server stood still for {} s: the world sleeps since then",
                                    Duration.between(beat, now).toSeconds());
                            return new Gap(true, true, null);
                        });
        if (gap.asleep() && worldService.asleepSince() == null) {
            // another thread put it to sleep a moment ago and may not have reloaded yet: the event
            // round after this check must not work from the old settings
            worldService.reload();
        }
        lastBeat = gap.beat();
        return gap.slept();
    }

    /**
     * What {@link #afterGap} found: whether it put the world to sleep, whether the world sleeps,
     * and the stored heartbeat if it is fresh.
     */
    private record Gap(boolean slept, boolean asleep, Instant beat) {}

    /** The tick saw the world awake: the heartbeat. */
    private void beat(Instant now) {
        jdbc.sql("update world_settings set heartbeat_at = ? where asleep_since is null")
                .param(Timestamp.from(now))
                .update();
        lastBeat = now;
    }

    private void clearHeartbeat() {
        if (lastBeat != null) {
            jdbc.sql("update world_settings set heartbeat_at = null where heartbeat_at is not null")
                    .update();
            lastBeat = null;
        }
    }

    /** Clears the stored heartbeat only: {@link #afterGap} sets the gate after its commit. */
    private void clearStoredHeartbeat() {
        jdbc.sql("update world_settings set heartbeat_at = null").update();
    }

    private void writeHeartbeat(Instant at) {
        if (at == null) {
            clearStoredHeartbeat();
        } else {
            jdbc.sql("update world_settings set heartbeat_at = ?")
                    .param(Timestamp.from(at))
                    .update();
        }
        lastBeat = at;
    }

    private Instant storedHeartbeat() {
        Timestamp beat =
                jdbc.sql("select heartbeat_at from world_settings")
                        .query(Timestamp.class)
                        .list()
                        .getFirst();
        return beat == null ? null : beat.toInstant();
    }

    /**
     * Wakes the world: every game clock moves on by the time asleep. A paused world only wakes when
     * {@code evenPaused} (play, or solo switched off).
     */
    private void wake(Instant now, boolean evenPaused) {
        Boolean woke =
                transaction.execute(
                        status -> {
                            ClockState state = worldService.lockedClockState();
                            if (state.asleepSince() == null || (state.paused() && !evenPaused)) {
                                return false;
                            }
                            Duration asleep = Duration.between(state.asleepSince(), now);
                            if (asleep.isPositive()) {
                                frozenTime.shift(asleep, now);
                            }
                            worldService.setAsleep(null, false);
                            // a fresh heartbeat: the time before the sleep is no gap
                            writeHeartbeat(now);
                            log.info("The world wakes after {} s", asleep.toSeconds());
                            notifyHumans();
                            return true;
                        });
        if (!Boolean.TRUE.equals(woke)) {
            // Another request woke it a moment ago and may not have reloaded the settings yet:
            // this request must not work out the time from the old ones.
            worldService.reload();
        }
    }

    /** Every game clock comes {@code speed - 1} steps closer. */
    private void fastStep(Instant now) {
        transaction.executeWithoutResult(
                status -> {
                    ClockState state = worldService.lockedClockState();
                    if (state.asleepSince() != null
                            || state.phase() != WorldPhase.OPEN
                            || !state.soloMode()
                            || state.speed() <= 1) {
                        return;
                    }
                    frozenTime.shift(
                            Duration.ofSeconds(-(long) (state.speed() - 1) * STEP_SECONDS), now);
                    notifyHumans();
                });
    }

    /** {@code WORLD_TIME_MOVED} to every human, after the commit. */
    private void notifyHumans() {
        var moved = new GameNotification.WorldTimeMoved();
        for (long playerId :
                jdbc.sql("select id from player where npc = false order by id")
                        .query(Long.class)
                        .list()) {
            events.publishEvent(new PlayerNotification(playerId, moved));
        }
    }
}
