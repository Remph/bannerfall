package de.bannerfall.paladin;

import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.recruit.DismissOrder;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitOrderRepository;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The paladin moved from the Barracks into the Banner Hall in 1.4 (02-game-rules §5.5). Once, at
 * the first start of 1.4: every paladin whose home village has no Banner Hall leaves, wherever he
 * is (at home, in a support, on the way, in a recruit or a dismiss order), and the home village
 * gets his cost back in full, above the warehouse capacity if need be. A movement that held only
 * paladins is called off. Paladins in villages with a hall stay, also those still in a Barracks
 * recruit order.
 */
@Service
public class PaladinHallMove {

    /** The job's row in {@code one_time_job}. */
    static final String JOB = "paladin-banner-hall";

    private static final Logger log = LoggerFactory.getLogger(PaladinHallMove.class);

    private final JdbcClient jdbc;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitStackRepository stacks;
    private final MovementRepository movements;
    private final RecruitOrderRepository recruitOrders;
    private final DismissOrderRepository dismissOrders;
    private final EventScheduler scheduler;
    private final GameData gameData;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaladinHallMove(
            JdbcClient jdbc,
            VillageLocker locker,
            VillageStateService stateService,
            UnitStackRepository stacks,
            MovementRepository movements,
            RecruitOrderRepository recruitOrders,
            DismissOrderRepository dismissOrders,
            EventScheduler scheduler,
            GameData gameData,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.locker = locker;
        this.stateService = stateService;
        this.stacks = stacks;
        this.movements = movements;
        this.recruitOrders = recruitOrders;
        this.dismissOrders = dismissOrders;
        this.scheduler = scheduler;
        this.gameData = gameData;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Runs the move unless it ran before. One transaction: the job's row and the changes commit
     * together.
     *
     * @return the paladins removed, or -1 if the move had run already
     */
    @Transactional
    public int run() {
        Instant now = clock.instant();
        int claimed =
                jdbc.sql(
                                """
                                insert into one_time_job (name, done_at) values (:name, :now)
                                on conflict (name) do nothing
                                """)
                        .param("name", JOB)
                        .param("now", Timestamp.from(now))
                        .update();
        if (claimed == 0) {
            return -1;
        }
        List<Long> homes =
                jdbc.sql(
                                """
                                select v.id from village v where v.b_banner_hall = 0 and (
                                  exists (select 1 from unit_stack s
                                          where s.home_village_id = v.id and s.u_paladin > 0)
                                  or exists (select 1 from movement m
                                             where m.home_village_id = v.id and m.u_paladin > 0)
                                  or exists (select 1 from recruit_order o
                                             where o.village_id = v.id and o.unit = 'PALADIN')
                                  or exists (select 1 from dismiss_order o
                                             where o.village_id = v.id and o.unit = 'PALADIN'))
                                order by v.id
                                """)
                        .query(Long.class)
                        .list();
        if (homes.isEmpty()) {
            return 0;
        }
        // The villages where their supports stand and their movements come from or go to are
        // locked too, in one ascending pass: a battle there changes the same stacks and movements.
        Set<Long> ids = new HashSet<>(homes);
        ids.addAll(
                jdbc.sql(
                                """
                                select location_village_id from unit_stack
                                where home_village_id in (:homes) and u_paladin > 0
                                union select source_village_id from movement
                                where home_village_id in (:homes) and u_paladin > 0
                                union select target_village_id from movement
                                where home_village_id in (:homes) and u_paladin > 0
                                """)
                        .param("homes", homes)
                        .query(Long.class)
                        .list());
        List<Village> locked = locker.lock(ids.toArray(Long[]::new));
        int removed = 0;
        for (Village village : locked) {
            if (homes.contains(village.getId())) {
                removed += removeFrom(village, now);
            }
        }
        return removed;
    }

    /** The paladins of one home village (locked) leave; returns how many. */
    private int removeFrom(Village village, Instant now) {
        long villageId = village.getId();
        List<RecruitOrder> open = stateService.catchUp(village, now);
        ResourceAmounts unitCost = gameData.unit(UnitType.PALADIN).cost();
        ResourceAmounts refund = ResourceAmounts.ZERO;
        int count = 0;

        for (UnitStack stack : stacks.findByHomeVillageId(villageId)) {
            int paladins = stack.getUnits().get(UnitType.PALADIN);
            if (paladins == 0) {
                continue;
            }
            stack.getUnits().set(UnitType.PALADIN, 0);
            count += paladins;
            refund = refund.plus(unitCost.times(paladins));
            if (!stack.isHome()) {
                notifyOwnerOf(stack.getLocationVillage());
                if (stack.getUnits().isEmpty()) {
                    stacks.delete(stack);
                }
            }
        }
        for (Movement movement : movements.findByHomeVillageId(villageId)) {
            int paladins = movement.removeAll(UnitType.PALADIN);
            if (paladins == 0) {
                continue;
            }
            count += paladins;
            refund = refund.plus(unitCost.times(paladins));
            notifyOwnerOf(movement.getTargetVillage());
            if (movement.getUnits().isEmpty()) {
                // false: the worker holds the event and waits for our lock; it then finds it gone
                scheduler.cancel(GameEventKind.MOVEMENT_ARRIVE, movement.getId());
                movements.delete(movement);
            }
        }
        for (BuildingType building : BuildingType.values()) {
            List<RecruitOrder> queue =
                    open.stream().filter(o -> o.getBuilding() == building).toList();
            Instant previousEnd = null;
            boolean shift = false;
            for (RecruitOrder order : queue) {
                if (order.getUnit() == UnitType.PALADIN) {
                    count += order.remaining();
                    refund = refund.plus(order.refund());
                    recruitOrders.delete(order);
                    shift = true;
                    continue;
                }
                if (shift) {
                    // the same rule as a cancel: never later, never before now
                    order.moveTo(BuildRules.startAfterCancel(order.getStartAt(), previousEnd, now));
                }
                previousEnd = order.getEndAt();
            }
        }
        List<DismissOrder> dismissals =
                dismissOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId);
        for (BuildingType building : BuildingType.values()) {
            List<DismissOrder> queue =
                    dismissals.stream().filter(o -> o.getBuilding() == building).toList();
            Instant previousEnd = null;
            boolean shift = false;
            for (DismissOrder order : queue) {
                if (order.getUnit() == UnitType.PALADIN) {
                    count += order.remaining();
                    refund = refund.plus(unitCost.times(order.remaining()));
                    dismissOrders.delete(order);
                    shift = true;
                    continue;
                }
                if (shift) {
                    order.moveTo(BuildRules.startAfterCancel(order.getStartAt(), previousEnd, now));
                }
                previousEnd = order.getEndAt();
            }
        }

        village.addResources(refund);
        notifyOwnerOf(village);
        Player owner = village.getPlayer();
        log.info(
                "Paladin moved into the Banner Hall: {} paladin(s) of village {} ({}) removed, {}"
                        + " refunded",
                count,
                villageId,
                owner == null ? "no owner" : owner.getName(),
                refund);
        return count;
    }

    private void notifyOwnerOf(Village village) {
        Player owner = village.getPlayer();
        if (owner != null) {
            events.publishEvent(
                    new PlayerNotification(
                            owner.getId(), new GameNotification.VillageUpdated(village.getId())));
        }
    }
}
