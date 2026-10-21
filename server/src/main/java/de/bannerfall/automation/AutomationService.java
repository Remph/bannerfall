package de.bannerfall.automation;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.building.AutomatedOrderCancelled;
import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildService;
import de.bannerfall.building.DemolitionQueued;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.npc.NpcPlanning;
import de.bannerfall.paladin.PaladinService;
import de.bannerfall.player.Player;
import de.bannerfall.recruit.RecruitBuildingDto;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitOrderRequest;
import de.bannerfall.recruit.RecruitService;
import de.bannerfall.recruit.RecruitTemplate;
import de.bannerfall.recruit.RecruitTemplateLetter;
import de.bannerfall.recruit.RecruitTemplateRepository;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Requirement;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The village automation (02-game-rules §25, 1.6): a village works through its build plan by itself
 * and, from the recruit line on, keeps a recruit template filled. Everything goes through {@link
 * BuildService} and {@link RecruitService} like a tap, so the automation pays the normal prices and
 * passes the normal checks; it only uses the normal places of the build queue, keeps at most {@link
 * AutomationPlanning#OWN_BUILD_ORDERS} orders of its own there and never recruits nobles or banner
 * thieves.
 *
 * <p>A village looks again through its {@code AUTOMATION_CHECK} event (one pending at a time): when
 * a queue place frees up, when the stock pays the next level, when a recruit queue gets room, and
 * every {@link #FALLBACK} anyway (losses, trades, a new village that unlocks it). Like the NPC
 * lords' rounds, "now" is the clock: a late look is a player who looks late.
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    /** The longest a switched-on village waits before it looks again. */
    static final Duration FALLBACK = Duration.ofMinutes(30);

    /** Looks after a due time come this much later, so the event that frees the place is done. */
    private static final Duration AFTER = Duration.ofSeconds(1);

    private final VillageAutomationRepository automations;
    private final BuildPlanRepository plans;
    private final RecruitTemplateRepository recruitTemplates;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final BuildService buildService;
    private final RecruitService recruitService;
    private final UnitService unitService;
    private final PaladinService paladins;
    private final VillageBanners villageBanners;
    private final EventScheduler scheduler;
    private final WorldService worldService;
    private final GameData gameData;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    AutomationService(
            VillageAutomationRepository automations,
            BuildPlanRepository plans,
            RecruitTemplateRepository recruitTemplates,
            VillageLocker locker,
            VillageStateService stateService,
            BuildService buildService,
            RecruitService recruitService,
            UnitService unitService,
            PaladinService paladins,
            VillageBanners villageBanners,
            EventScheduler scheduler,
            WorldService worldService,
            GameData gameData,
            ApplicationEventPublisher events,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.automations = automations;
        this.plans = plans;
        this.recruitTemplates = recruitTemplates;
        this.locker = locker;
        this.stateService = stateService;
        this.buildService = buildService;
        this.recruitService = recruitService;
        this.unitService = unitService;
        this.paladins = paladins;
        this.villageBanners = villageBanners;
        this.scheduler = scheduler;
        this.worldService = worldService;
        this.gameData = gameData;
        this.events = events;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The status of the village's automation; locks the village and catches it up. A village that
     * could queue now looks right away: resources from outside (loot, a transport, a trade) arrive
     * before the look it planned when its own production would have paid.
     */
    @Transactional
    public VillageAutomationDto status(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        Look look = look(village, now);
        List<RecruitBuildingDto> buildings = recruitment(look, villageId, playerId);
        // units lost in a battle are refilled now, not at the look up to 30 minutes away
        if (look.state == AutomationState.READY || look.refills(buildings)) {
            scheduler.scheduleNoLaterThan(
                    GameEventKind.AUTOMATION_CHECK, villageId, now.plus(AFTER), null);
        }
        return look.dto(buildings);
    }

    /** The village's recruiting buildings while there is a recruit template to look at. */
    private List<RecruitBuildingDto> recruitment(Look look, long villageId, long playerId) {
        return look.goal == null ? List.of() : recruitService.recruitment(villageId, playerId);
    }

    /**
     * The owner picks a plan for the village or switches it on or off; a pause after a cancel ends.
     * A switched-on village looks at once.
     *
     * @param plan null drops the automation (then {@code enabled} must be false)
     * @throws GameRuleException {@code AUTOMATION_LOCKED} when switching on with too few villages
     * @throws NotFoundException {@code BUILD_PLAN_NOT_FOUND} for a plan the player doesn't have
     */
    @Transactional
    public VillageAutomationDto set(
            long villageId, long playerId, BuildPlanLetter plan, boolean enabled) {
        Village village = locker.lockOwned(villageId, playerId);
        if (plan == null) {
            automations.deleteForVillage(villageId);
            automations.flush();
            return look(village, clock.instant()).dto(List.of());
        }
        if (plans.findByPlayerIdAndLetter(playerId, plan).isEmpty()) {
            throw new NotFoundException(ErrorCode.BUILD_PLAN_NOT_FOUND);
        }
        if (enabled && !unlocked(village.getPlayer(), worldService.settings())) {
            throw new GameRuleException(
                    ErrorCode.AUTOMATION_LOCKED, "Too few villages for the automation");
        }
        VillageAutomation automation = automations.findById(villageId).orElse(null);
        if (automation == null) {
            automations.save(new VillageAutomation(villageId, plan, enabled));
        } else {
            automation.set(plan, enabled);
        }
        if (enabled) {
            scheduler.scheduleNoLaterThan(
                    GameEventKind.AUTOMATION_CHECK, villageId, clock.instant(), null);
        }
        Look look = look(village, clock.instant());
        return look.dto(recruitment(look, villageId, playerId));
    }

    /** A changed plan: the villages working through it look at once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void planChanged(long playerId, BuildPlanLetter letter) {
        Instant now = clock.instant();
        for (VillageAutomation automation :
                automations.findEnabledByPlayerAndPlan(playerId, letter)) {
            scheduler.scheduleNoLaterThan(
                    GameEventKind.AUTOMATION_CHECK, automation.getVillageId(), now, null);
        }
    }

    /** The owner cancelled an order of the automation: it pauses in that village (§25). */
    @EventListener
    void onCancel(AutomatedOrderCancelled event) {
        automations.findById(event.villageId()).ifPresent(VillageAutomation::pauseByCancel);
    }

    /**
     * The owner queued a demolition of a level the village's plan builds: the automation would
     * build it again, so it pauses like after a cancel (§25). A demolition the plan doesn't undo
     * changes nothing.
     */
    @EventListener
    void onDemolition(DemolitionQueued event) {
        VillageAutomation automation = automations.findById(event.villageId()).orElse(null);
        if (automation == null || !automation.isEnabled()) {
            return;
        }
        boolean wanted =
                plans
                        .findByPlayerIdAndLetter(event.playerId(), automation.getPlanLetter())
                        .map(BuildPlan::getLines)
                        .orElse(List.of())
                        .stream()
                        .anyMatch(
                                line ->
                                        line.building() == event.building()
                                                && line.level() > event.level());
        if (wanted) {
            automation.pauseByCancel();
        }
    }

    /**
     * One look of a village (the {@code AUTOMATION_CHECK} handler): queue what the plan allows,
     * recruit what the template lacks, each in a transaction of its own (a failure in one doesn't
     * undo the other and is only logged), then the time of the next look.
     *
     * @return when to look again; null when the village has no switched-on automation
     */
    public Instant run(long villageId) {
        Instant now = clock.instant();
        step(villageId, "build", () -> tx.executeWithoutResult(s -> build(villageId, now)));
        step(villageId, "recruit", () -> tx.executeWithoutResult(s -> recruit(villageId, now)));
        return tx.execute(s -> nextLook(villageId, now));
    }

    /** Schedules the next look (in the transaction of the handler of event {@code handling}). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleLook(long villageId, Instant at, long handling) {
        scheduler.scheduleNoLaterThan(GameEventKind.AUTOMATION_CHECK, villageId, at, handling);
    }

    private static void step(long villageId, String what, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            log.warn("Village automation of {}: the {} step failed", villageId, what, e);
        }
    }

    /** Queues the plan's next levels while it says READY (at most two own orders). */
    private void build(long villageId, Instant now) {
        Village village = lockedOwned(villageId);
        if (village == null) {
            return;
        }
        boolean queued = false;
        for (int i = 0; i < AutomationPlanning.OWN_BUILD_ORDERS; i++) {
            Look look = look(village, now);
            if (look.state != AutomationState.READY) {
                break;
            }
            buildService.enqueue(villageId, village.getPlayer().getId(), look.building, true);
            queued = true;
        }
        if (queued) {
            updated(village);
        }
    }

    /** The owner's open pages show the new orders (VILLAGE_UPDATED). */
    private void updated(Village village) {
        PlayerNotification.toOwnerOf(
                village, new GameNotification.VillageUpdated(village.getId()), events);
    }

    /**
     * From the recruit line on: what the recruit template lacks, out of what is left after the next
     * build line's cost and population, queued with "recruit all".
     */
    private void recruit(long villageId, Instant now) {
        Village village = lockedOwned(villageId);
        if (village == null) {
            return;
        }
        Look look = look(village, now);
        if (look.goal == null || !look.active()) {
            return;
        }
        long playerId = village.getPlayer().getId();
        List<RecruitBuildingDto> buildings = recruitService.recruitment(villageId, playerId);
        // a stuck build line (a resource that isn't produced, a farm at its top) waits for the
        // player: keeping its cost back would stop recruiting as well
        boolean stuck = look.state == AutomationState.BLOCKED;
        ResourceAmounts reserve = stuck ? ResourceAmounts.ZERO : look.reserve;
        int popReserve = stuck ? 0 : look.popReserve;
        List<RecruitOrderRequest> orders =
                AutomationPlanning.recruitOrders(
                        look.goal,
                        buildings,
                        look.units,
                        nonNegative(look.stock.minus(reserve)),
                        look.freePop - popReserve,
                        look.world.recruitQueueSize(),
                        paladins.free(playerId));
        if (!orders.isEmpty()) {
            recruitService.enqueueAll(villageId, playerId, orders);
            updated(village);
        }
    }

    /** When the village looks again: the earliest reason, at the latest after {@link #FALLBACK}. */
    private Instant nextLook(long villageId, Instant now) {
        Village village = lockedOwned(villageId);
        if (village == null) {
            return null;
        }
        Look look = look(village, now);
        Instant next = now.plus(FALLBACK);
        if (!look.active()) {
            // a switched-on village below the unlock looks now and then for a new village; one
            // without an automation (dropped, or the village changed owner) stops looking
            boolean resting =
                    look.state == AutomationState.LOCKED
                            && look.automation != null
                            && look.automation.isEnabled();
            return resting ? next : null;
        }
        if (look.state == AutomationState.WAITING_RESOURCES) {
            next = earlier(next, look.readyAt.plus(AFTER));
        } else if ((look.state == AutomationState.WAITING_QUEUE
                        || look.state == AutomationState.WAITING_UPGRADE)
                && !look.queue.isEmpty()) {
            next = earlier(next, look.queue.getFirst().getEndAt().plus(AFTER));
        }
        if (look.goal != null) {
            Map<?, List<RecruitOrder>> byBuilding = byBuilding(look.recruitOrders);
            for (List<RecruitOrder> queue : byBuilding.values()) {
                if (queue.size() >= AutomationPlanning.RECRUIT_QUEUE_BUSY) {
                    next = earlier(next, queue.getFirst().getEndAt().plus(AFTER));
                }
            }
        }
        return next.isAfter(now) ? next : now.plus(AFTER);
    }

    /** The village locked, if it still has an owner; null for a barbarian village. */
    private Village lockedOwned(long villageId) {
        List<Village> locked = locker.lock(villageId);
        if (locked.isEmpty() || locked.getFirst().getPlayer() == null) {
            return null;
        }
        return locked.getFirst();
    }

    /** Everything the automation looks at, of a locked village caught up to {@code now}. */
    private Look look(Village village, Instant now) {
        WorldSettings world = worldService.settings();
        Player owner = village.getPlayer();
        VillageAutomation automation = automations.findById(village.getId()).orElse(null);
        BuildPlan plan =
                automation == null || owner == null
                        ? null
                        : plans.findByPlayerIdAndLetter(owner.getId(), automation.getPlanLetter())
                                .orElse(null);
        List<RecruitOrder> recruitOrders = stateService.catchUp(village, now);
        List<BuildOrder> queue = buildService.queueOf(village.getId());
        UnitCounts units = unitService.unitsOf(village.getId(), recruitOrders).all();
        Map<BuildingType, Integer> current = village.getBuildings().toMap();
        Map<BuildingType, Integer> levels = BuildService.levelsWithQueued(current, queue);
        // a queued farm demolition counts the smaller farm, like the build and recruit checks
        int freePop =
                BuildService.farmCapacity(village, queue)
                        - buildService.populationUsed(village, queue, units);

        Look look = new Look(world, owner, automation, plan, queue, recruitOrders, units);
        look.villages = owner == null ? 0 : owner.getVillageCount();
        look.freePop = freePop;
        look.stock = village.floorResources();
        List<BuildPlanLine> lines = plan == null ? List.of() : plan.getLines();
        boolean hall = world.bannerHallAvailable();
        look.line = AutomationPlanning.currentLine(lines, levels, hall);
        if (plan != null && AutomationPlanning.recruiting(lines, look.line)) {
            look.recruitLetter = lines.get(AutomationPlanning.recruitLine(lines)).recruit();
            look.goal =
                    recruitTemplates
                            .findByPlayerIdAndLetter(owner.getId(), look.recruitLetter)
                            .map(RecruitTemplate::getUnits)
                            .orElse(null);
        }

        if (!unlocked(owner, world)) {
            look.state = AutomationState.LOCKED;
        } else if (automation == null) {
            look.state = AutomationState.OFF;
        } else if (plan == null) {
            look.state = AutomationState.NO_PLAN;
        } else if (!automation.isEnabled()) {
            look.state =
                    automation.isPausedByCancel() ? AutomationState.PAUSED : AutomationState.OFF;
        }
        if (plan == null) {
            return look;
        }
        NpcPlanning.Next next =
                NpcPlanning.nextBuild(
                        AutomationPlanning.steps(lines, hall), levels, current, gameData, freePop);
        if (next.kind() == NpcPlanning.Kind.DONE) {
            look.state = look.state == null ? AutomationState.DONE : look.state;
            return look;
        }
        if (next.kind() == NpcPlanning.Kind.WAIT) {
            look.state = look.state == null ? AutomationState.WAITING_UPGRADE : look.state;
            return look;
        }
        BuildingType type = next.type();
        BuildingDef def = gameData.building(type);
        int level = levels.get(type) + 1;
        ResourceAmounts cost = BuildRules.cost(def, level);
        int pop = BuildRules.popDelta(def, level);
        look.building = type;
        look.level = level;
        look.reserve = cost;
        look.popReserve = pop;
        if (look.state != null) {
            return look;
        }
        long own = queue.stream().filter(BuildOrder::isAutomated).count();
        OptionalLong wait =
                AutomationPlanning.secondsUntil(cost, look.stock, productionPerHour(village));
        if (!BuildRules.missingRequirements(def.requires(), levels).isEmpty()
                || pop > freePop
                || wait.isEmpty()) {
            look.state = AutomationState.BLOCKED;
        } else if (queue.size() >= world.buildQueueSize()
                || own >= AutomationPlanning.OWN_BUILD_ORDERS
                || demolishing(queue, type)) {
            look.state = AutomationState.WAITING_QUEUE;
        } else if (wait.getAsLong() > 0) {
            look.state = AutomationState.WAITING_RESOURCES;
            look.readyAt = now.plusSeconds(wait.getAsLong());
        } else {
            look.state = AutomationState.READY;
        }
        return look;
    }

    /**
     * Whether the queue holds a demolition of {@code type} (1.6, 02-game-rules §4.5): an upgrade
     * can't be queued next to it, so the automation waits until it is done.
     */
    private static boolean demolishing(List<BuildOrder> queue, BuildingType type) {
        return queue.stream().anyMatch(order -> order.isDemolish() && order.getBuilding() == type);
    }

    private Map<ResourceType, Double> productionPerHour(Village village) {
        WorldSettings world = worldService.settings();
        BannerEffect banner = villageBanners.effectOf(village);
        Map<ResourceType, Double> rates = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            rates.put(type, VillageStateService.productionPerHour(village, type, world, banner));
        }
        return rates;
    }

    private static boolean unlocked(Player owner, WorldSettings world) {
        return owner != null
                && world.automationVillages() > 0
                && owner.getVillageCount() >= world.automationVillages();
    }

    private static Map<BuildingType, List<RecruitOrder>> byBuilding(List<RecruitOrder> orders) {
        Map<BuildingType, List<RecruitOrder>> result = new EnumMap<>(BuildingType.class);
        for (RecruitOrder order : orders) {
            result.computeIfAbsent(order.getBuilding(), b -> new ArrayList<>()).add(order);
        }
        return result;
    }

    private static ResourceAmounts nonNegative(ResourceAmounts amounts) {
        return new ResourceAmounts(
                Math.max(0, amounts.wood()),
                Math.max(0, amounts.clay()),
                Math.max(0, amounts.iron()));
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** What one look found; {@link #dto} is the status. */
    private static final class Look {
        final WorldSettings world;
        final Player owner;
        final VillageAutomation automation;
        final BuildPlan plan;
        final List<BuildOrder> queue;
        final List<RecruitOrder> recruitOrders;
        final UnitCounts units;
        AutomationState state;
        int villages;
        int freePop;
        ResourceAmounts stock = ResourceAmounts.ZERO;
        int line;
        BuildingType building;
        int level;
        ResourceAmounts reserve = ResourceAmounts.ZERO;
        int popReserve;
        Instant readyAt;
        RecruitTemplateLetter recruitLetter;
        UnitCounts goal;

        Look(
                WorldSettings world,
                Player owner,
                VillageAutomation automation,
                BuildPlan plan,
                List<BuildOrder> queue,
                List<RecruitOrder> recruitOrders,
                UnitCounts units) {
            this.world = world;
            this.owner = owner;
            this.automation = automation;
            this.plan = plan;
            this.queue = queue;
            this.recruitOrders = recruitOrders;
            this.units = units;
        }

        /** Switched on, unlocked and with its plan: it queues and recruits. */
        boolean active() {
            return state != AutomationState.LOCKED
                    && state != AutomationState.OFF
                    && state != AutomationState.PAUSED
                    && state != AutomationState.NO_PLAN;
        }

        /**
         * Whether a look could recruit something: the template lacks units the village can recruit
         * (the blocked ones wait for a building).
         */
        boolean refills(List<RecruitBuildingDto> buildings) {
            if (goal == null || !active()) {
                return false;
            }
            Map<UnitType, Integer> missing = new EnumMap<>(UnitType.class);
            Map<UnitType, List<Requirement>> blocked = new EnumMap<>(UnitType.class);
            collect(buildings, missing, blocked, new ArrayList<>());
            return missing.size() > blocked.size();
        }

        /**
         * Splits the goal: what the village lacks goes to {@code missing}, and the units of those
         * it can't recruit yet also to {@code blocked} with their unmet requirements; the units the
         * automation never recruits go to {@code byHand}. Units the world doesn't list (switched
         * off) are left out.
         */
        private void collect(
                List<RecruitBuildingDto> buildings,
                Map<UnitType, Integer> missing,
                Map<UnitType, List<Requirement>> blocked,
                List<UnitType> byHand) {
            if (goal == null) {
                return;
            }
            Map<UnitType, RecruitBuildingDto.RecruitUnitDto> listed = new EnumMap<>(UnitType.class);
            for (RecruitBuildingDto building : buildings) {
                building.units().forEach(unit -> listed.put(unit.unit(), unit));
            }
            goal.forEach(
                    (type, count) -> {
                        if (count <= 0) {
                            return;
                        }
                        if (AutomationPlanning.BY_HAND.contains(type)) {
                            byHand.add(type);
                        } else if (count > units.get(type) && listed.containsKey(type)) {
                            RecruitBuildingDto.RecruitUnitDto unit = listed.get(type);
                            missing.put(type, count - units.get(type));
                            if (!unit.available()) {
                                blocked.put(type, unit.missing());
                            }
                        }
                    });
        }

        VillageAutomationDto dto(List<RecruitBuildingDto> buildings) {
            Map<UnitType, Integer> missing = new EnumMap<>(UnitType.class);
            Map<UnitType, List<Requirement>> blocked = new EnumMap<>(UnitType.class);
            List<UnitType> byHand = new ArrayList<>();
            collect(buildings, missing, blocked, byHand);
            return new VillageAutomationDto(
                    state,
                    world.automationVillages(),
                    villages,
                    automation == null ? null : automation.getPlanLetter(),
                    automation != null && automation.isEnabled(),
                    automation != null && automation.isPausedByCancel(),
                    line,
                    plan == null ? 0 : plan.getLines().size(),
                    building,
                    building == null ? 0 : level,
                    readyAt,
                    goal == null ? null : recruitLetter,
                    missing,
                    byHand,
                    blocked);
        }
    }
}
