package de.bannerfall.recruit;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.building.BuildService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.paladin.PaladinService;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Population;
import de.bannerfall.rules.RecruitRules;
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
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recruiting units (02-game-rules §5.2): queueing and cancelling orders, and the overview of the
 * recruiting buildings. Each building has its own queue. There are no events: the village catch-up
 * ({@link VillageStateService#catchUp}) moves finished units into the home stack.
 *
 * <p>Every method locks the village first ({@code VillageLocker}) and catches it up to now, like
 * every request that reads or changes a village.
 */
@Service
public class RecruitService {

    private final RecruitOrderRepository orders;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final BuildService buildService;
    private final DismissService dismissService;
    private final PaladinService paladins;
    private final VillageBanners villageBanners;
    private final UnitService unitService;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;

    RecruitService(
            RecruitOrderRepository orders,
            VillageLocker locker,
            VillageStateService stateService,
            BuildService buildService,
            DismissService dismissService,
            PaladinService paladins,
            VillageBanners villageBanners,
            UnitService unitService,
            GameData gameData,
            WorldService worldService,
            Clock clock) {
        this.orders = orders;
        this.locker = locker;
        this.stateService = stateService;
        this.buildService = buildService;
        this.dismissService = dismissService;
        this.paladins = paladins;
        this.villageBanners = villageBanners;
        this.unitService = unitService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Queues {@code count} units in the queue of their building. Pays cost and population now; the
     * time per unit uses the building's level of right now. The checks run in this order, the first
     * one that fails is the answer.
     *
     * @throws GameRuleException {@code QUEUE_FULL} (that building's queue), {@code
     *     REQUIREMENTS_NOT_MET} (current levels, queued ones don't count), {@code PALADIN_DISABLED}
     *     / {@code PALADIN_SLOTS_FULL} (paladin), {@code BANNERS_DISABLED} (a banner thief in a
     *     world without banners), {@code NOT_ENOUGH_RESOURCES}, {@code NOT_ENOUGH_POPULATION}
     */
    @Transactional
    public RecruitOrderDto enqueue(long villageId, long playerId, UnitType type, int count) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<RecruitOrder> villageOrders = new ArrayList<>(stateService.catchUp(village, now));
        return RecruitOrderDto.of(
                enqueueLocked(village, villageOrders, worldService.settings(), type, count, now));
    }

    /**
     * Queues several orders at once ("recruit all", 02-game-rules §5.2): all or none. The orders
     * are checked and queued one after another in the given order with exactly the checks of {@link
     * #enqueue}, each against what the ones before it have left (resources, population, free places
     * in the queue of its building). The first one that fails is the answer, and the transaction
     * rolls the others back; its {@code unit} names the order that failed.
     *
     * @param requests at least one; every unit at most once (the controller checks both)
     * @throws GameRuleException like {@link #enqueue}, with the property {@code unit}
     */
    @Transactional
    public List<RecruitOrderDto> enqueueAll(
            long villageId, long playerId, List<RecruitOrderRequest> requests) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<RecruitOrder> villageOrders = new ArrayList<>(stateService.catchUp(village, now));
        WorldSettings world = worldService.settings();
        List<RecruitOrderDto> result = new ArrayList<>();
        for (RecruitOrderRequest request : requests) {
            try {
                RecruitOrder order =
                        enqueueLocked(
                                village,
                                villageOrders,
                                world,
                                request.unit(),
                                request.count(),
                                now);
                result.add(RecruitOrderDto.of(order));
            } catch (GameRuleException e) {
                throw e.withProperty("unit", request.unit().name());
            }
        }
        return result;
    }

    /**
     * The checks and the queueing of one order, on a locked and caught-up village. {@code
     * villageOrders} are its open orders; the new one is added at the end, so a next order in the
     * same request sees it (queue place, population, start time).
     */
    private RecruitOrder enqueueLocked(
            Village village,
            List<RecruitOrder> villageOrders,
            WorldSettings world,
            UnitType type,
            int count,
            Instant now) {
        UnitDef def = gameData.unit(type);
        BuildingType building = def.building();
        List<RecruitOrder> queue =
                villageOrders.stream().filter(o -> o.getBuilding() == building).toList();

        if (queue.size() >= world.recruitQueueSize()) {
            throw new GameRuleException(
                    ErrorCode.QUEUE_FULL, "The recruit queue of " + building + " is full");
        }
        // Units need the buildings now, so queued levels don't count (unlike for buildings). The
        // recruiting building itself is always among the requirements (checked in GameData).
        Map<BuildingType, Integer> levels = village.getBuildings().toMap();
        if (!BuildRules.missingRequirements(def.requires(), levels).isEmpty()) {
            throw new GameRuleException(
                    ErrorCode.REQUIREMENTS_NOT_MET, "Requirements for " + type + " are not met");
        }
        if (type == UnitType.PALADIN) {
            checkPaladinSlots(village, count, world);
        }
        if (type == UnitType.BANNER_THIEF && !world.bannersEnabled()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "Banners are switched off in this world");
        }
        ResourceAmounts cost = RecruitRules.cost(def, count);
        if (!cost.fitsInto(village.floorResources())) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_RESOURCES, "Not enough resources for " + type);
        }
        if (RecruitRules.pop(def, count) > freePopulation(village, villageOrders)) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_POPULATION, "Not enough free population for " + type);
        }

        // The Drill banner of right now (02-game-rules §21.2) shortens the order; a later change
        // of the banner leaves it alone, like a later change of the building's level.
        int secondsPerUnit =
                Math.toIntExact(
                        BannerRules.seconds(
                                RecruitRules.secondsPerUnit(def, levels.get(building), world),
                                villageBanners.effectOf(village).drill()));
        Instant lastEnd = queue.isEmpty() ? null : queue.getLast().getEndAt();
        // Same rule as the build queue: right after the last order, but not before now.
        Instant startAt = BuildRules.startAfter(lastEnd, now);

        village.spend(cost);
        RecruitOrder order =
                orders.save(
                        RecruitOrder.create(
                                village.getId(),
                                building,
                                type,
                                count,
                                secondsPerUnit,
                                startAt,
                                def.cost(),
                                def.pop(),
                                now));
        villageOrders.add(order);
        return order;
    }

    /**
     * A paladin needs a free slot (02-game-rules §5.5). Taken under the player lock, after the
     * village lock, so two villages of one player can't fill the last slot together.
     */
    private void checkPaladinSlots(Village village, int count, WorldSettings world) {
        if (world.paladinMaxSlots() < 1) {
            throw new GameRuleException(
                    ErrorCode.PALADIN_DISABLED, "There are no paladins in this world");
        }
        long playerId = village.getPlayer().getId();
        paladins.lockSlots(playerId);
        if (count > paladins.free(playerId)) {
            throw new GameRuleException(
                    ErrorCode.PALADIN_SLOTS_FULL, "Not enough free paladin slots");
        }
    }

    /**
     * Cancels an order (02-game-rules §5.2). The units already done stay (the catch-up at the start
     * has moved them home); the cost of the units still in training is refunded, but received like
     * incoming resources: up to the warehouse capacity, the rest is lost (as for build orders, so
     * the queue can't be used as a store above the capacity). Their population is free again as
     * soon as the order is gone (it is counted from the orders). The later orders of that building
     * move up and keep their duration.
     *
     * <p>An order whose last unit is done no longer exists after the catch-up, so it can't be
     * cancelled. The orders behind the cancelled one haven't started (the one in front of them
     * isn't done), so moving them is always possible.
     *
     * @throws NotFoundException {@code RECRUIT_ORDER_NOT_FOUND} if the village has no such order
     *     (never had, or all its units are done, or it was cancelled)
     */
    @Transactional
    public void cancel(long villageId, long orderId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        // Looked up among this village's open orders: that is the ownership check (an order of
        // another village is "not found"), and it also skips an order the catch-up has just
        // deleted.
        List<RecruitOrder> villageOrders = stateService.catchUp(village, now);
        RecruitOrder order =
                villageOrders.stream()
                        .filter(o -> o.getId() == orderId)
                        .findFirst()
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.RECRUIT_ORDER_NOT_FOUND));
        List<RecruitOrder> queue =
                villageOrders.stream().filter(o -> o.getBuilding() == order.getBuilding()).toList();
        int index = queue.indexOf(order);

        Instant previousEnd = index == 0 ? null : queue.get(index - 1).getEndAt();
        for (RecruitOrder later : queue.subList(index + 1, queue.size())) {
            // Same rule as the build queue: never later, never before now.
            later.moveTo(BuildRules.startAfterCancel(later.getStartAt(), previousEnd, now));
            previousEnd = later.getEndAt();
        }

        village.receiveResources(order.refund(), villageBanners.warehouseCapacity(village));
        orders.delete(order);
    }

    /**
     * The recruiting buildings in building order (the Banner Hall only while banners are on), each
     * with its units and its queue (04-api). Locks the village and catches it up, so finished units
     * are at home and the queues are current.
     */
    @Transactional
    public List<RecruitBuildingDto> recruitment(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        List<RecruitOrder> villageOrders = stateService.catchUp(village, clock.instant());
        WorldSettings world = worldService.settings();
        Map<BuildingType, Integer> levels = village.getBuildings().toMap();
        ResourceAmounts stock = village.floorResources();
        int freePop = freePopulation(village, villageOrders);
        UnitCounts home = unitService.homeUnits(villageId);
        List<DismissOrder> dismissals = dismissService.openOrders(villageId);
        int freePaladinSlots = paladins.free(village.getPlayer().getId());
        BannerEffect banner = villageBanners.effectOf(village);

        List<RecruitBuildingDto> result = new ArrayList<>();
        for (BuildingType building : GameData.RECRUITING_BUILDINGS) {
            // a world without banners has no thieves to train (02-game-rules §21.9): no Banner
            // Hall in the list, like the hall itself is hidden then
            if (building == BuildingType.BANNER_HALL && !world.bannersEnabled()) {
                continue;
            }
            int level = levels.get(building);
            List<RecruitBuildingDto.RecruitUnitDto> units = new ArrayList<>();
            for (UnitType type : UnitType.values()) {
                UnitDef def = gameData.unit(type);
                if (def.building() != building) {
                    continue;
                }
                // a world without paladins (02-game-rules §5.5) does not list him
                if (type == UnitType.PALADIN && world.paladinMaxSlots() < 1) {
                    continue;
                }
                List<Requirement> missing = BuildRules.missingRequirements(def.requires(), levels);
                units.add(
                        new RecruitBuildingDto.RecruitUnitDto(
                                type,
                                def.cost(),
                                def.pop(),
                                BannerRules.seconds(
                                        RecruitRules.secondsPerUnit(def, Math.max(1, level), world),
                                        banner.drill()),
                                missing.isEmpty(),
                                missing,
                                type == UnitType.PALADIN
                                        ? Math.min(
                                                freePaladinSlots,
                                                RecruitRules.maxAffordable(def, stock, freePop))
                                        : RecruitRules.maxAffordable(def, stock, freePop),
                                home.get(type),
                                RecruitRules.dismissSecondsPerUnit(def, level, world)));
            }
            List<RecruitOrderDto> queue =
                    villageOrders.stream()
                            .filter(o -> o.getBuilding() == building)
                            .map(RecruitOrderDto::of)
                            .toList();
            List<DismissOrderDto> dismissQueue =
                    dismissals.stream()
                            .filter(o -> o.getBuilding() == building)
                            .map(DismissOrderDto::of)
                            .toList();
            result.add(new RecruitBuildingDto(building, level, units, queue, dismissQueue));
        }
        return result;
    }

    /**
     * Free population (02-game-rules §5.3); the village is locked and caught up, {@code
     * villageOrders} are the open orders the catch-up returned.
     */
    private int freePopulation(Village village, List<RecruitOrder> villageOrders) {
        return Population.free(
                village.getBuildings().get(BuildingType.FARM),
                buildService.populationUsed(village, villageOrders));
    }
}
