package de.bannerfall.cheat;

import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildOrderRepository;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.market.TradeTransport;
import de.bannerfall.market.TradeTransportRepository;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitOrderRepository;
import de.bannerfall.research.ResearchOrder;
import de.bannerfall.research.ResearchOrderRepository;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cheats: shortcuts for developing and for playtests (04-api, "Dev cheats"; 08-host-console §3).
 * One implementation, two doors: the dev cheats ({@code DevCheatController}, profile {@code dev})
 * work on the caller's own villages ({@link CheatTarget#own}), the host cheats ({@code
 * AdminCheatController}, only with {@code bannerfall.cheats.enabled}) on any village ({@link
 * CheatTarget#any}). The service itself is always there; only the controllers are switched.
 */
@Service
public class CheatService {

    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final BuildOrderRepository orders;
    private final RecruitOrderRepository recruitOrders;
    private final DismissOrderRepository dismissOrders;
    private final ResearchOrderRepository researchOrders;
    private final MovementRepository movements;
    private final TradeTransportRepository transports;
    private final UnitService unitService;
    private final EventScheduler scheduler;
    private final EventProcessor processor;
    private final Clock clock;
    private final TransactionTemplate transaction;

    CheatService(
            VillageLocker locker,
            VillageStateService stateService,
            BuildOrderRepository orders,
            RecruitOrderRepository recruitOrders,
            DismissOrderRepository dismissOrders,
            ResearchOrderRepository researchOrders,
            MovementRepository movements,
            TradeTransportRepository transports,
            UnitService unitService,
            EventScheduler scheduler,
            EventProcessor processor,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.locker = locker;
        this.stateService = stateService;
        this.orders = orders;
        this.recruitOrders = recruitOrders;
        this.dismissOrders = dismissOrders;
        this.researchOrders = researchOrders;
        this.movements = movements;
        this.transports = transports;
        this.unitService = unitService;
        this.scheduler = scheduler;
        this.processor = processor;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Adds the amounts; the warehouse capacity is ignored. */
    @Transactional
    public void addResources(CheatTarget target, ResourceAmounts amounts) {
        Village village = lock(target);
        stateService.catchUp(village, clock.instant());
        village.addResources(amounts);
    }

    /**
     * Adds units to the home stack. The farm is not looked at (the village may end up with more
     * population than it has room for).
     */
    @Transactional
    public void addUnits(CheatTarget target, Map<UnitType, Integer> units) {
        Village village = lock(target);
        stateService.catchUp(village, clock.instant());
        UnitCounts home = unitService.homeUnits(target.villageId());
        units.forEach(home::add);
    }

    /**
     * Completes all orders of the village now. Recruit orders: all their units go home right away
     * and the orders are deleted; dismiss orders are over, all their units gone. Build orders:
     * every order ends now and its event is moved to now, then the due events are processed.
     *
     * <p>Deliberately <b>not</b> one transaction: the handlers run in transactions of their own and
     * need the village lock, which this request would still hold.
     */
    public void finishOrders(CheatTarget target) {
        transaction.executeWithoutResult(status -> endOrdersNow(target));
        processor.processDue(clock.instant());
    }

    /**
     * Lets every movement of the village's units arrive now: its own commands, supports and returns
     * (not foreign movements coming here) and the merchants it sent. Then the due events are
     * processed. An attack fights on arrival; its survivors walk home as a new RETURN: call again
     * to bring them home. Not one transaction, for the same reason as {@link #finishOrders}.
     */
    public void finishMovements(CheatTarget target) {
        transaction.executeWithoutResult(status -> movementsArriveNow(target));
        processor.processDue(clock.instant());
    }

    private void movementsArriveNow(CheatTarget target) {
        long villageId = lock(target).getId();
        Instant now = clock.instant();
        for (Movement movement : movements.findByHomeVillageId(villageId)) {
            movement.arriveNow(now);
            // false: the worker holds the event right now and lets it arrive anyway.
            scheduler.reschedule(
                    GameEventKind.MOVEMENT_ARRIVE, movement.getId(), movement.getArriveAt());
        }
        for (TradeTransport transport :
                transports.findBySourceVillageIdAndArrivedFalse(villageId)) {
            transport.arriveNow(now);
            scheduler.reschedule(
                    GameEventKind.TRADE_ARRIVE, transport.getId(), transport.getArriveAt());
        }
    }

    private void endOrdersNow(CheatTarget target) {
        Village village = lock(target);
        long villageId = village.getId();
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        // Events at the same instant run in creation order, which is queue order unless an order
        // was moved. Either way the result is the same: each completion adds one level, all at now.
        for (BuildOrder order : orders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            order.finishNow(now);
            // false: the worker holds the event right now and completes it anyway.
            scheduler.reschedule(GameEventKind.BUILD_COMPLETE, order.getId(), now);
        }
        // The catch-up above has moved the units that were done already.
        UnitCounts home = unitService.homeUnits(villageId);
        for (RecruitOrder order : recruitOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            home.add(order.getUnit(), order.finishNow());
            recruitOrders.delete(order);
        }
        // Dismissed units left the home stack when the order was placed: all that's left is for
        // the order to be over, which frees their population.
        dismissOrders.deleteAll(dismissOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId));
        // A running research is done now (M30): its row stays and counts from this moment on.
        for (ResearchOrder order : researchOrders.findByVillageIdAndEndAtAfter(villageId, now)) {
            order.finishNow(now);
        }
    }

    /**
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} if the village doesn't exist or, for
     *     {@link CheatTarget#own}, belongs to someone else
     */
    private Village lock(CheatTarget target) {
        if (target.playerId() != null) {
            return locker.lockOwned(target.villageId(), target.playerId());
        }
        return locker.lock(target.villageId()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND));
    }
}
