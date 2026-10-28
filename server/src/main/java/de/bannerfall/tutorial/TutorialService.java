package de.bannerfall.tutorial;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.tutorial.TutorialStep.Detection;
import de.bannerfall.tutorial.TutorialStep.Kind;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tutorial (1.7, 02-game-rules §27): a player's list of steps and lessons, which of them are
 * done or skipped, and the refunds. Plain SQL ({@code tutorial_state}, {@code tutorial_progress}).
 *
 * <p>Every call ends with the same view. {@link #sync} is the heart: it records the steps the game
 * state shows as done and pays their refunds, once, because a step has one row per player.
 */
@Service
public class TutorialService {

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final GameData gameData;
    private final TutorialFactsReader factsReader;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final Clock clock;

    TutorialService(
            JdbcClient jdbc,
            WorldService worldService,
            GameData gameData,
            TutorialFactsReader factsReader,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            Clock clock) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.gameData = gameData;
        this.factsReader = factsReader;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.clock = clock;
    }

    /** A row of {@code tutorial_progress}. */
    private record Progress(TutorialStepStatus status) {}

    /** A new account has the tutorial on from the start, if the world has it. */
    @Transactional
    public void startForNewPlayer(long playerId) {
        if (worldService.settings().tutorialEnabled()) {
            setActive(playerId, true);
        }
    }

    /**
     * The tutorial as it stands now. Records the steps the game state shows as done, and pays what
     * they refund.
     */
    @Transactional
    public TutorialDto sync(long playerId) {
        WorldSettings world = worldService.settings();
        if (!world.tutorialEnabled() || !isActive(playerId)) {
            return view(playerId, world, List.of());
        }
        return view(playerId, world, record(playerId, world, false));
    }

    /**
     * Switches the tutorial on (the menu row). What the game state already shows as done is ticked
     * silently, without a refund; so is everything done while it was off.
     */
    @Transactional
    public TutorialDto start(long playerId) {
        WorldSettings world = worldService.settings();
        if (!world.tutorialEnabled()) {
            return view(playerId, world, List.of());
        }
        setActive(playerId, true);
        record(playerId, world, true);
        return view(playerId, world, List.of());
    }

    /** Switches the tutorial off: no card until it is started again. Progress stays. */
    @Transactional
    public TutorialDto stop(long playerId) {
        WorldSettings world = worldService.settings();
        if (world.tutorialEnabled() && isActive(playerId)) {
            setActive(playerId, false);
        }
        return view(playerId, world, List.of());
    }

    /**
     * Skips a step or lesson for good: it earns no refund, even if the player does it later.
     * Skipping what is over already changes nothing.
     */
    @Transactional
    public TutorialDto skip(long playerId, TutorialStep step) {
        WorldSettings world = worldService.settings();
        if (!world.tutorialEnabled() || !isActive(playerId)) {
            return view(playerId, world, List.of());
        }
        List<TutorialRefundDto> paid = record(playerId, world, false);
        if (TutorialRules.inWorld(step, world) && !progress(playerId).containsKey(step)) {
            insert(playerId, step, TutorialStepStatus.SKIPPED);
        }
        return view(playerId, world, paid);
    }

    /**
     * The client says the player did what the server cannot see (opened a barbarian village's
     * panel, the simulator) or read a lesson.
     *
     * @throws ApiException 400 {@code VALIDATION} for a step the server sees by itself
     * @throws GameRuleException {@code TUTORIAL_STEP_NOT_OPEN} if its turn has not come
     */
    @Transactional
    public TutorialDto done(long playerId, TutorialStep step) {
        if (step.detection() != Detection.ACK) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "The server sees this step by itself");
        }
        WorldSettings world = worldService.settings();
        if (!world.tutorialEnabled() || !isActive(playerId)) {
            return view(playerId, world, List.of());
        }
        List<TutorialRefundDto> paid = record(playerId, world, false);
        Map<TutorialStep, Progress> progress = progress(playerId);
        if (!progress.containsKey(step)) {
            TutorialFacts facts = factsReader.of(playerId);
            if (!isOpen(step, progress, facts, world)) {
                throw new GameRuleException(
                        ErrorCode.TUTORIAL_STEP_NOT_OPEN, "This step is not open yet");
            }
            insert(playerId, step, TutorialStepStatus.DONE);
        }
        return view(playerId, world, paid);
    }

    // ---- recording -------------------------------------------------------------------------

    /**
     * Records every step of the player's game state that is done and whose turn has come, until
     * nothing more changes (one step can open the next, which may be done already).
     *
     * @param silent no refunds: the tutorial was just switched on
     */
    private List<TutorialRefundDto> record(long playerId, WorldSettings world, boolean silent) {
        TutorialFacts facts = factsReader.of(playerId);
        Map<TutorialStep, Progress> progress = progress(playerId);
        List<TutorialRefundDto> paid = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TutorialStep step : TutorialStep.values()) {
                if (step.detection() != Detection.STATE
                        || progress.containsKey(step)
                        || !TutorialRules.inWorld(step, world)
                        || !prerequisiteOver(step, progress, world)
                        || !TutorialRules.done(step, facts)) {
                    continue;
                }
                if (insert(playerId, step, TutorialStepStatus.DONE)) {
                    if (!silent) {
                        pay(playerId, step).ifPresent(paid::add);
                    }
                }
                progress.put(step, new Progress(TutorialStepStatus.DONE));
                changed = true;
            }
        }
        return paid;
    }

    /** Pays the step's refund into the village that did the work; empty without a refund. */
    private Optional<TutorialRefundDto> pay(long playerId, TutorialStep step) {
        ResourceAmounts refund = TutorialRules.refund(step, gameData);
        if (refund.total() == 0) {
            return Optional.empty();
        }
        Optional<Long> villageId = refundVillage(playerId, step);
        if (villageId.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Village village = locker.lockOwned(villageId.get(), playerId);
        stateService.catchUp(village, now);
        ResourceAmounts lost =
                village.receiveResources(refund, villageBanners.warehouseCapacity(village));
        ResourceAmounts received = refund.minus(lost);
        jdbc.sql(
                        """
                        update tutorial_progress set wood = :wood, clay = :clay, iron = :iron
                        where player_id = :p and step_key = :key
                        """)
                .param("wood", received.wood())
                .param("clay", received.clay())
                .param("iron", received.iron())
                .param("p", playerId)
                .param("key", step.name())
                .update();
        return Optional.of(new TutorialRefundDto(step, village.getId(), received));
    }

    /** The player's village with the highest level of the building the step asked for. */
    private Optional<Long> refundVillage(long playerId, TutorialStep step) {
        String order =
                switch (step) {
                    case WOODCUTTER -> "b_woodcutter desc, id";
                    case BARRACKS, SPEARMEN -> "b_barracks desc, id";
                    case MARKET -> "b_market desc, id";
                    default -> "id";
                };
        return jdbc.sql(
                        "select id from village where player_id = :p order by "
                                + order
                                + " limit 1")
                .param("p", playerId)
                .query(Long.class)
                .optional();
    }

    // ---- reading ---------------------------------------------------------------------------

    private TutorialDto view(long playerId, WorldSettings world, List<TutorialRefundDto> paid) {
        if (!world.tutorialEnabled()) {
            return new TutorialDto(false, false, null, List.of(), List.of());
        }
        if (!isActive(playerId)) {
            return new TutorialDto(true, false, null, List.of(), List.of());
        }
        TutorialFacts facts = factsReader.of(playerId);
        Map<TutorialStep, Progress> progress = progress(playerId);
        List<TutorialStepDto> steps = new ArrayList<>();
        for (TutorialStep step : TutorialStep.values()) {
            if (TutorialRules.inWorld(step, world)) {
                steps.add(
                        new TutorialStepDto(
                                step, step.kind(), status(step, progress, facts, world)));
            }
        }
        return new TutorialDto(true, true, current(steps, world), steps, paid);
    }

    private TutorialStepStatus status(
            TutorialStep step,
            Map<TutorialStep, Progress> progress,
            TutorialFacts facts,
            WorldSettings world) {
        Progress recorded = progress.get(step);
        if (recorded != null) {
            return recorded.status();
        }
        return isOpen(step, progress, facts, world)
                ? TutorialStepStatus.OPEN
                : TutorialStepStatus.UPCOMING;
    }

    /**
     * A lesson first (it is short and comes up just when its feature opens), else the next step.
     */
    private TutorialCurrentDto current(List<TutorialStepDto> steps, WorldSettings world) {
        for (TutorialStepDto line : steps) {
            if (line.kind() == Kind.LESSON && line.status() == TutorialStepStatus.OPEN) {
                return new TutorialCurrentDto(line.key(), Kind.LESSON, 0, 0, null);
            }
        }
        List<TutorialStepDto> only = steps.stream().filter(s -> s.kind() == Kind.STEP).toList();
        for (int i = 0; i < only.size(); i++) {
            TutorialStepDto line = only.get(i);
            if (line.status() == TutorialStepStatus.OPEN) {
                ResourceAmounts refund = TutorialRules.refund(line.key(), gameData);
                return new TutorialCurrentDto(
                        line.key(),
                        Kind.STEP,
                        i + 1,
                        only.size(),
                        refund.total() == 0 ? null : refund);
            }
        }
        return null;
    }

    private boolean isOpen(
            TutorialStep step,
            Map<TutorialStep, Progress> progress,
            TutorialFacts facts,
            WorldSettings world) {
        return TutorialRules.inWorld(step, world)
                && prerequisiteOver(step, progress, world)
                && TutorialRules.available(step, facts, world);
    }

    /**
     * Whether the step before it is done or skipped; a step the world does not have is not waited
     * for, the one before that counts instead.
     */
    private static boolean prerequisiteOver(
            TutorialStep step, Map<TutorialStep, Progress> progress, WorldSettings world) {
        TutorialStep before = step.prerequisite();
        while (before != null && !TutorialRules.inWorld(before, world)) {
            before = before.prerequisite();
        }
        return before == null || progress.containsKey(before);
    }

    // ---- rows ------------------------------------------------------------------------------

    private boolean isActive(long playerId) {
        return jdbc.sql("select active from tutorial_state where player_id = :p")
                .param("p", playerId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    private void setActive(long playerId, boolean active) {
        jdbc.sql(
                        """
                        insert into tutorial_state (player_id, active) values (:p, :active)
                        on conflict (player_id) do update set active = excluded.active
                        """)
                .param("p", playerId)
                .param("active", active)
                .update();
    }

    private Map<TutorialStep, Progress> progress(long playerId) {
        Map<TutorialStep, Progress> rows = new EnumMap<>(TutorialStep.class);
        jdbc.sql("select step_key, status from tutorial_progress where player_id = :p")
                .param("p", playerId)
                .query(
                        rs -> {
                            try {
                                rows.put(
                                        TutorialStep.valueOf(rs.getString("step_key")),
                                        new Progress(
                                                TutorialStepStatus.valueOf(
                                                        rs.getString("status"))));
                            } catch (IllegalArgumentException e) {
                                // a step a later version dropped: nothing to show
                            }
                        });
        return rows;
    }

    /** False when the step already had a row (another request was first). */
    private boolean insert(long playerId, TutorialStep step, TutorialStepStatus status) {
        return jdbc.sql(
                                """
                                insert into tutorial_progress (player_id, step_key, status, done_at)
                                values (:p, :key, :status, :now)
                                on conflict (player_id, step_key) do nothing
                                """)
                        .param("p", playerId)
                        .param("key", step.name())
                        .param("status", status.name())
                        .param("now", java.sql.Timestamp.from(clock.instant()))
                        .update()
                == 1;
    }
}
