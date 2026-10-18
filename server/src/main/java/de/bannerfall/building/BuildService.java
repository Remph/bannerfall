package de.bannerfall.building;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.BuildingEffects;
import de.bannerfall.rules.Population;
import de.bannerfall.rules.Requirement;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The build queue of a village (02-game-rules §4.3): queueing an upgrade, cancelling or moving one,
 * and the building overview. Finished orders are applied by {@link BuildCompleteHandler}.
 *
 * <p>Every method locks the village first ({@code VillageLocker}), like every request that changes
 * a village. The order's BUILD_COMPLETE event is scheduled in the same transaction.
 */
@Service
public class BuildService {

    private final BuildOrderRepository orders;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final VillageBanners villageBanners;
    private final EventScheduler scheduler;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;

    BuildService(
            BuildOrderRepository orders,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            VillageBanners villageBanners,
            EventScheduler scheduler,
            GameData gameData,
            WorldService worldService,
            Clock clock) {
        this.orders = orders;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.villageBanners = villageBanners;
        this.scheduler = scheduler;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Queues an upgrade by one level. Pays the cost now, schedules the completion at the end of the
     * order. The checks run in this order, the first one that fails is the answer. Behind {@code
     * buildQueueSize} the paid slots up to {@code buildQueueMax} cost a surcharge by position
     * (02-game-rules §4.3, M13c); it is stored with the cost, so a cancel refunds it too.
     *
     * @throws GameRuleException {@code QUEUE_FULL} (at {@code buildQueueMax}), {@code
     *     MAX_LEVEL_REACHED} (queued levels count), {@code REQUIREMENTS_NOT_MET} (queued levels
     *     count), {@code BANNERS_DISABLED} (a new banner hall in a world without banners), {@code
     *     NOT_ENOUGH_RESOURCES}, {@code NOT_ENOUGH_POPULATION}
     */
    @Transactional
    public BuildOrderDto enqueue(long villageId, long playerId, BuildingType type) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<RecruitOrder> recruiting = stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        List<BuildOrder> queue = orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);

        if (queue.size() >= world.buildQueueMax()) {
            throw new GameRuleException(ErrorCode.QUEUE_FULL, "The build queue is full");
        }
        BuildingDef def = gameData.building(type);
        Map<BuildingType, Integer> levels = levelsWithQueued(village, queue);
        int targetLevel = levels.get(type) + 1;
        if (targetLevel > def.maxLevel()) {
            throw new GameRuleException(
                    ErrorCode.MAX_LEVEL_REACHED, type + " has reached its maximum level");
        }
        if (!BuildRules.missingRequirements(def.requires(), levels).isEmpty()) {
            throw new GameRuleException(
                    ErrorCode.REQUIREMENTS_NOT_MET, "Requirements for " + type + " are not met");
        }
        // The banner hall serves the banners and trains the paladin (02-game-rules §21.3): no new
        // one in a world that has neither.
        if (type == BuildingType.BANNER_HALL && !world.bannerHallAvailable()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "Banners are switched off in this world");
        }
        ResourceAmounts cost =
                BuildRules.cost(def, targetLevel, queue.size() + 1, world.buildQueueSize());
        if (!cost.fitsInto(village.floorResources())) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_RESOURCES, "Not enough resources for " + type);
        }
        int extraPop = BuildRules.popDelta(def, targetLevel);
        int used = populationUsed(village, queue, unitService.unitsOf(villageId, recruiting).all());
        if (extraPop > Population.free(village.getBuildings().get(BuildingType.FARM), used)) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_POPULATION, "Not enough free population for " + type);
        }

        // The Town Hall level of right now sets the time, queued Town Hall upgrades don't (§4.2);
        // so does the Builders banner of right now (§21.2), later banner changes leave the order
        // alone.
        int hqLevel = village.getBuildings().get(BuildingType.HEADQUARTERS);
        BannerEffect banner = villageBanners.effectOf(village);
        int duration =
                Math.toIntExact(
                        BannerRules.seconds(
                                BuildRules.buildSeconds(def, targetLevel, hqLevel, world),
                                banner.builders()));
        Instant lastEnd = queue.isEmpty() ? now : queue.get(queue.size() - 1).getEndAt();
        Instant startAt = lastEnd.isAfter(now) ? lastEnd : now;

        village.spend(cost);
        BuildOrder order =
                orders.save(
                        BuildOrder.create(
                                villageId,
                                type,
                                targetLevel,
                                duration,
                                startAt,
                                cost,
                                extraPop,
                                now));
        scheduler.schedule(GameEventKind.BUILD_COMPLETE, order.getId(), order.getEndAt());
        return BuildOrderDto.of(order);
    }

    /**
     * Cancels an order of the queue (02-game-rules §4.3). Orders behind it that no longer make
     * sense are cancelled with it: later upgrades of the same building (their target level is gone)
     * and orders whose requirements were only met through a cancelled order. Every cancelled order
     * is refunded, but only up to the warehouse capacity, the rest is lost. The orders that stay
     * move up and their completion events move with them. The refunded population needs no booking:
     * it is counted from the queued orders, so it is free again as soon as the orders are gone.
     *
     * <p>An order whose end has passed is done, even if the event worker hasn't applied it yet, so
     * it can't be cancelled any more. Since end times grow along the queue, no order that is due is
     * ever cancelled or moved here, and a {@link EventScheduler#reschedule} that answers {@code
     * false} (the worker holds the event) can't happen for an order that really moves. A cancel
     * that loses the race against the worker is harmless: the handler finds the order gone.
     *
     * @throws NotFoundException {@code BUILD_ORDER_NOT_FOUND} if the village has no such order
     *     (never had, or it is done or was cancelled meanwhile)
     */
    @Transactional
    public void cancel(long villageId, long orderId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        List<BuildOrder> queue = orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);

        int index = indexOf(queue, orderId);
        if (index < 0 || !queue.get(index).getEndAt().isAfter(now)) {
            throw new NotFoundException(ErrorCode.BUILD_ORDER_NOT_FOUND);
        }

        // The orders in front of the cancelled one stay as they are.
        Map<BuildingType, Integer> levels = levelsWithQueued(village, queue.subList(0, index));
        List<BuildOrder> cancelled = new ArrayList<>(List.of(queue.get(index)));
        Instant previousEnd = index == 0 ? null : queue.get(index - 1).getEndAt();
        for (BuildOrder later : queue.subList(index + 1, queue.size())) {
            if (!stillFits(later, levels)) {
                cancelled.add(later);
                continue;
            }
            levels.merge(later.getBuilding(), 1, Integer::sum);
            later.moveTo(BuildRules.startAfterCancel(later.getStartAt(), previousEnd, now));
            scheduler.reschedule(GameEventKind.BUILD_COMPLETE, later.getId(), later.getEndAt());
            previousEnd = later.getEndAt();
        }

        ResourceAmounts refund = ResourceAmounts.ZERO;
        for (BuildOrder order : cancelled) {
            refund = refund.plus(order.cost());
            orders.delete(order);
            scheduler.cancel(GameEventKind.BUILD_COMPLETE, order.getId());
        }
        village.receiveResources(refund, villageBanners.warehouseCapacity(village));
    }

    /**
     * Moves an order that hasn't started to another place in the queue (02-game-rules §4.3,
     * <i>Move</i>). Every order must still fit afterwards: it is the next level of its building and
     * its requirements are met by the orders in front of it, so upgrades of one building keep their
     * order. The waiting orders are then re-timed back to back behind the started ones (an order
     * may get later here, unlike after a cancel) and their events move with them. Costs and
     * durations stay as they were paid. The paid slots behind {@code buildQueueSize} are fixed
     * (M13c): an order there can't be moved and nothing can be moved there; once it has moved up
     * into the normal slots it is movable like any other, its surcharge stays as paid.
     *
     * <p>Started orders are a prefix of the queue (start times grow along it). An order that has
     * started is never moved, and no event the worker may hold right now belongs to a waiting order
     * (its end is in the future), so {@link EventScheduler#reschedule} can't answer {@code false}
     * here.
     *
     * @param position the new 0-based index in the queue
     * @return the queue in its new order
     * @throws NotFoundException {@code BUILD_ORDER_NOT_FOUND} like {@link #cancel}
     * @throws GameRuleException {@code ORDER_NOT_MOVABLE} if the order has started or is in a paid
     *     slot, or the position is in front of a started order, in a paid slot or behind the end of
     *     the queue; {@code REQUIREMENTS_NOT_MET} if an order would no longer fit
     */
    @Transactional
    public List<BuildOrderDto> move(long villageId, long orderId, int position, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<BuildOrder> queue =
                new ArrayList<>(orders.findByVillageIdOrderByEndAtAscIdAsc(villageId));

        int index = indexOf(queue, orderId);
        if (index < 0 || !queue.get(index).getEndAt().isAfter(now)) {
            throw new NotFoundException(ErrorCode.BUILD_ORDER_NOT_FOUND);
        }
        int started = startedCount(queue, now);
        int fixedFrom = Math.min(queue.size(), worldService.settings().buildQueueSize());
        if (index < started || index >= fixedFrom || position < started || position >= fixedFrom) {
            throw new GameRuleException(
                    ErrorCode.ORDER_NOT_MOVABLE, "Build order " + orderId + " can't go there");
        }
        if (position != index) {
            queue.add(position, queue.remove(index));
            List<BuildOrder> waiting = queue.subList(started, queue.size());

            Map<BuildingType, Integer> levels =
                    levelsWithQueued(village, queue.subList(0, started));
            for (BuildOrder order : waiting) {
                if (!stillFits(order, levels)) {
                    throw new GameRuleException(
                            ErrorCode.REQUIREMENTS_NOT_MET,
                            "Build order " + order.getId() + " would no longer fit");
                }
                levels.merge(order.getBuilding(), 1, Integer::sum);
            }

            Instant previousEnd = started == 0 ? null : queue.get(started - 1).getEndAt();
            for (BuildOrder order : waiting) {
                order.moveTo(BuildRules.startAfter(previousEnd, now));
                scheduler.reschedule(GameEventKind.BUILD_COMPLETE, order.getId(), order.getEndAt());
                previousEnd = order.getEndAt();
            }
        }
        return queue.stream().map(BuildOrderDto::of).toList();
    }

    /**
     * Whether a queued order still makes sense behind the orders that stay in front of it: it is
     * still the next level of its building, and its requirements are still met (queued levels in
     * front of it count, like when it was queued).
     */
    private boolean stillFits(BuildOrder order, Map<BuildingType, Integer> levelsBefore) {
        BuildingType type = order.getBuilding();
        return order.getTargetLevel() == levelsBefore.get(type) + 1
                && BuildRules.missingRequirements(gameData.building(type).requires(), levelsBefore)
                        .isEmpty();
    }

    /**
     * Every building with its level, the queued level and what the next upgrade would cost
     * (04-api): the price at the next free position of the queue, a paid slot's surcharge included
     * (with a full queue: the last slot's price). Locks the village so the levels and the queue
     * belong together.
     */
    @Transactional
    public List<BuildingInfoDto> buildings(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        List<BuildOrder> queue = orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);
        WorldSettings world = worldService.settings();
        Map<BuildingType, Integer> current = village.getBuildings().toMap();
        Map<BuildingType, Integer> queued = levelsWithQueued(village, queue);
        int hqLevel = current.get(BuildingType.HEADQUARTERS);
        BannerEffect banner = villageBanners.effectOf(village);
        int position = Math.min(queue.size() + 1, world.buildQueueMax());
        int surcharge = BuildRules.slotSurchargePercent(position, world.buildQueueSize());

        // A world without banners and paladins (02-game-rules §21.3) has no use for the banner
        // hall: it is left out of the list (and of what other upgrades unlock) unless one stands
        // there already.
        boolean banners = world.bannersEnabled();
        boolean hall = world.bannerHallAvailable();
        List<BuildingInfoDto> result = new ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.BANNER_HALL && !hall && current.get(type) == 0) {
                continue;
            }
            BuildingDef def = gameData.building(type);
            int queuedLevel = queued.get(type);
            List<Requirement> missing = BuildRules.missingRequirements(def.requires(), queued);
            BuildingInfoDto.NextLevelDto next = null;
            if (queuedLevel < def.maxLevel()) {
                int nextLevel = queuedLevel + 1;
                next =
                        new BuildingInfoDto.NextLevelDto(
                                nextLevel,
                                BuildRules.cost(def, nextLevel, position, world.buildQueueSize()),
                                surcharge,
                                BuildRules.popDelta(def, nextLevel),
                                BannerRules.seconds(
                                        BuildRules.buildSeconds(def, nextLevel, hqLevel, world),
                                        banner.builders()),
                                BuildingEffects.upgrade(type, queuedLevel, world, banner),
                                unlockedBuildings(type, queued, hall),
                                unlockedUnits(type, queued, world.paladinMaxSlots() > 0, banners));
            }
            result.add(
                    new BuildingInfoDto(
                            type,
                            current.get(type),
                            queuedLevel,
                            def.maxLevel(),
                            next,
                            missing.isEmpty(),
                            missing));
        }
        return result;
    }

    /**
     * The buildings that this upgrade of {@code type} lets the player queue: their requirements are
     * not met with {@code queued} but are with one more level of {@code type} (M24).
     */
    private List<BuildingType> unlockedBuildings(
            BuildingType type, Map<BuildingType, Integer> queued, boolean hall) {
        Map<BuildingType, Integer> after = withOneMore(queued, type);
        List<BuildingType> unlocked = new ArrayList<>();
        for (BuildingType other : BuildingType.values()) {
            if (other == BuildingType.BANNER_HALL && !hall) {
                continue;
            }
            Map<BuildingType, Integer> requires = gameData.building(other).requires();
            if (!BuildRules.missingRequirements(requires, queued).isEmpty()
                    && BuildRules.missingRequirements(requires, after).isEmpty()) {
                unlocked.add(other);
            }
        }
        return unlocked;
    }

    /** The units that this upgrade of {@code type} lets the player recruit (M24). */
    private List<UnitType> unlockedUnits(
            BuildingType type,
            Map<BuildingType, Integer> queued,
            boolean paladins,
            boolean banners) {
        Map<BuildingType, Integer> after = withOneMore(queued, type);
        List<UnitType> unlocked = new ArrayList<>();
        for (UnitType unit : UnitType.values()) {
            // a world without paladins (02-game-rules §5.5) has no paladin to unlock
            if (unit == UnitType.PALADIN && !paladins) {
                continue;
            }
            // nor a world without banners a banner thief (02-game-rules §21.5)
            if (unit == UnitType.BANNER_THIEF && !banners) {
                continue;
            }
            Map<BuildingType, Integer> requires = gameData.unit(unit).requires();
            if (!BuildRules.missingRequirements(requires, queued).isEmpty()
                    && BuildRules.missingRequirements(requires, after).isEmpty()) {
                unlocked.add(unit);
            }
        }
        return unlocked;
    }

    private static Map<BuildingType, Integer> withOneMore(
            Map<BuildingType, Integer> levels, BuildingType type) {
        var after = new EnumMap<BuildingType, Integer>(BuildingType.class);
        after.putAll(levels);
        after.merge(type, 1, Integer::sum);
        return after;
    }

    /** The queue of a village in running order. The caller has locked the village. */
    public List<BuildOrder> queueOf(long villageId) {
        return orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);
    }

    /**
     * A building of the village lost {@code levels} levels in a battle (02-game-rules §9.2). Its
     * queued orders stay valid and each still adds one level when it completes, so their target
     * levels move down by as much: the queue shows the right levels, and a later cancel or move
     * (which checks that every order is the next level of its building) still works. Costs and
     * times stay as they were paid. The caller has locked the village.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void levelsLost(long villageId, BuildingType building, int levels) {
        for (BuildOrder order : queueOf(villageId)) {
            if (order.getBuilding() == building) {
                order.lowerTargetLevel(levels);
            }
        }
    }

    /**
     * Population in use (02-game-rules §5.3) with the queue and the units loaded here. The caller
     * has locked the village and caught it up. Used by the recruit checks.
     *
     * @param recruitOrders the open recruit orders the catch-up returned
     */
    public int populationUsed(Village village, List<RecruitOrder> recruitOrders) {
        long villageId = village.getId();
        return populationUsed(
                village, queueOf(villageId), unitService.unitsOf(villageId, recruitOrders).all());
    }

    /**
     * Population in use (02-game-rules §5.3): the buildings with their <b>queued</b> levels plus
     * all units of the village ({@link UnitService.VillageUnits#all()}: home, supporting elsewhere,
     * on the way, in recruit orders). Capacity is the farm's current level, see {@link
     * Population#free}.
     */
    public int populationUsed(Village village, List<BuildOrder> queue, UnitCounts allUnits) {
        return Population.usedByBuildings(gameData, levelsWithQueued(village, queue))
                + Population.usedByUnits(gameData, allUnits.toMap());
    }

    private static Map<BuildingType, Integer> levelsWithQueued(
            Village village, List<BuildOrder> queue) {
        return BuildRules.levelsWithQueued(
                village.getBuildings().toMap(),
                queue.stream().map(BuildOrder::getBuilding).toList());
    }

    /** How many orders at the head of the queue have started (start at or before now). */
    private static int startedCount(List<BuildOrder> queue, Instant now) {
        int count = 0;
        while (count < queue.size() && !queue.get(count).getStartAt().isAfter(now)) {
            count++;
        }
        return count;
    }

    private static int indexOf(List<BuildOrder> queue, long orderId) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == orderId) {
                return i;
            }
        }
        return -1;
    }
}
