package de.bannerfall.recruit;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dismissing units (02-game-rules §5.2a): units of the home stack leave it at once and are
 * dismissed one by one at five times the recruit speed, in a queue per recruiting building next to
 * the recruit queue. Nothing is refunded; every dismissed unit frees its population. No events: the
 * village catch-up ({@link VillageStateService#catchUp}) finishes the orders.
 *
 * <p>Every method locks the village first and catches it up to now, like {@link RecruitService}.
 */
@Service
public class DismissService {

    private final DismissOrderRepository orders;
    private final MovementRepository movements;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;

    DismissService(
            DismissOrderRepository orders,
            MovementRepository movements,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            GameData gameData,
            WorldService worldService,
            Clock clock) {
        this.orders = orders;
        this.movements = movements;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Takes {@code count} units of one kind out of the home stack and queues them for dismissal in
     * the queue of their building. The time per unit is a fifth of the recruit time at the
     * building's current level (level 1 if it is razed). The checks run in this order.
     *
     * @throws GameRuleException {@code QUEUE_FULL} (that building's dismiss queue has {@code
     *     recruitQueueSize} orders), {@code NOT_ENOUGH_UNITS} (fewer than {@code count} of them at
     *     home: units away or in training don't count)
     */
    @Transactional
    public DismissOrderDto enqueue(long villageId, long playerId, UnitType type, int count) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        UnitDef def = gameData.unit(type);
        BuildingType building = def.building();
        List<DismissOrder> queue = queueOf(villageId, building);

        if (queue.size() >= world.recruitQueueSize()) {
            throw new GameRuleException(
                    ErrorCode.QUEUE_FULL, "The dismiss queue of " + building + " is full");
        }
        UnitCounts home = unitService.homeUnits(villageId);
        if (home.get(type) < count) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_UNITS, "Not enough " + type + " at home");
        }

        int level = village.getBuildings().get(building);
        int secondsPerUnit = Math.toIntExact(RecruitRules.dismissSecondsPerUnit(def, level, world));
        // Same rule as the other queues: right after the last order, but not before now.
        Instant startAt =
                BuildRules.startAfter(queue.isEmpty() ? null : queue.getLast().getEndAt(), now);

        // Out of the home stack now: they no longer defend and can't be sent. They stay counted
        // for the farm until they are dismissed (UnitService.unitsOf).
        home.add(type, -count);
        DismissOrder order =
                orders.save(
                        DismissOrder.create(
                                villageId, building, type, count, secondsPerUnit, startAt, now));
        return DismissOrderDto.of(order);
    }

    /**
     * Cancels an order: the units not yet dismissed go back into the home stack (the ones already
     * dismissed are gone for good), the later orders of that building move up and keep their
     * duration.
     *
     * @throws NotFoundException {@code DISMISS_ORDER_NOT_FOUND} if the village has no such order
     *     (never had, or all its units are gone, or it was cancelled)
     * @throws GameRuleException {@code DISMISS_LOCKED} while an attack is on its way to the village
     */
    @Transactional
    public void cancel(long villageId, long orderId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        // Looked up among this village's open orders: that is the ownership check, and it skips
        // an order the catch-up has just deleted.
        List<DismissOrder> villageOrders = orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);
        DismissOrder order =
                villageOrders.stream()
                        .filter(o -> o.getId() == orderId)
                        .findFirst()
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.DISMISS_ORDER_NOT_FOUND));
        // The units of an order can't be hit by an attack; taking them back while one is coming
        // would make the queue a hiding place. They come back once the attack has landed.
        if (movements.countIncomingAttacks(villageId) > 0) {
            throw new GameRuleException(
                    ErrorCode.DISMISS_LOCKED,
                    "Dismiss orders can't be cancelled while an attack is on its way");
        }
        List<DismissOrder> queue =
                villageOrders.stream().filter(o -> o.getBuilding() == order.getBuilding()).toList();
        int index = queue.indexOf(order);

        Instant previousEnd = index == 0 ? null : queue.get(index - 1).getEndAt();
        for (DismissOrder later : queue.subList(index + 1, queue.size())) {
            // The orders behind haven't started (the one in front of them isn't done).
            later.moveTo(BuildRules.startAfterCancel(later.getStartAt(), previousEnd, now));
            previousEnd = later.getEndAt();
        }
        unitService.homeUnits(villageId).add(order.getUnit(), order.remaining());
        orders.delete(order);
    }

    /** The open dismiss orders of the village's queues, running order; for the overview. */
    List<DismissOrder> openOrders(long villageId) {
        return orders.findByVillageIdOrderByEndAtAscIdAsc(villageId);
    }

    private List<DismissOrder> queueOf(long villageId, BuildingType building) {
        return orders.findByVillageIdOrderByEndAtAscIdAsc(villageId).stream()
                .filter(o -> o.getBuilding() == building)
                .toList();
    }
}
