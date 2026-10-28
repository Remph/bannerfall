package de.bannerfall.village;

import de.bannerfall.automation.VillageAutomationRepository;
import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildOrderRepository;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.farm.FarmTargetRepository;
import de.bannerfall.market.TradeOfferRepository;
import de.bannerfall.market.TradeTransport;
import de.bannerfall.market.TradeTransportRepository;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.movement.ReturnWatchRepository;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrderRepository;
import de.bannerfall.research.ResearchOrderRepository;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a village leaves behind when it changes owner: a conquest (02-game-rules §10) or a deleted
 * player's village turning barbarian (§11). Its orders and offers are deleted without refund and
 * its units everywhere are lost (§6). The caller holds the village's lock and the locks of the
 * villages its supports stand in ({@code UnitStackRepository.findSupportLocationIds}).
 */
@Component
public class OwnerChange {

    private final BuildOrderRepository buildOrders;
    private final RecruitOrderRepository recruitOrders;
    private final DismissOrderRepository dismissOrders;
    private final ResearchOrderRepository researchOrders;
    private final FarmTargetRepository farmTargets;
    private final VillageAutomationRepository automations;
    private final TradeOfferRepository tradeOffers;
    private final TradeTransportRepository transports;
    private final UnitStackRepository stacks;
    private final UnitService unitService;
    private final MovementRepository movements;
    private final EventScheduler scheduler;
    private final ApplicationEventPublisher events;
    private final ReturnWatchRepository returnWatches;
    private final VillageTagService villageTags;

    OwnerChange(
            BuildOrderRepository buildOrders,
            RecruitOrderRepository recruitOrders,
            DismissOrderRepository dismissOrders,
            ResearchOrderRepository researchOrders,
            FarmTargetRepository farmTargets,
            VillageAutomationRepository automations,
            TradeOfferRepository tradeOffers,
            TradeTransportRepository transports,
            UnitStackRepository stacks,
            UnitService unitService,
            MovementRepository movements,
            EventScheduler scheduler,
            ApplicationEventPublisher events,
            ReturnWatchRepository returnWatches,
            VillageTagService villageTags) {
        this.buildOrders = buildOrders;
        this.recruitOrders = recruitOrders;
        this.dismissOrders = dismissOrders;
        this.researchOrders = researchOrders;
        this.farmTargets = farmTargets;
        this.automations = automations;
        this.tradeOffers = tradeOffers;
        this.transports = transports;
        this.stacks = stacks;
        this.unitService = unitService;
        this.movements = movements;
        this.scheduler = scheduler;
        this.events = events;
        this.returnWatches = returnWatches;
        this.villageTags = villageTags;
    }

    /**
     * Build orders (with their events), recruit, dismiss and running research orders, market
     * offers, the farm list, the village automation and the owner's tag are deleted; nothing is
     * refunded (an offer's resources are lost, 02-game-rules §10). Transports on the way still
     * arrive, and what comes to the village goes to its new owner; the merchants the village sent
     * out were the old owner's and don't come back (code review 1.2, D8): they are no longer busy
     * here or listed.
     *
     * @param at when the village changes owner (a conquest: the battle's time, which the event
     *     worker may handle a little later)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dropOrders(long villageId, Instant at) {
        for (BuildOrder order : buildOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            // false: the worker holds the event and waits for our lock; it then finds the order
            // gone
            scheduler.cancel(GameEventKind.BUILD_COMPLETE, order.getId());
            buildOrders.delete(order);
        }
        recruitOrders.deleteAll(recruitOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId));
        dismissOrders.deleteAll(dismissOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId));
        // A research that still runs is lost with the village (M30); the finished ones are the
        // player's unit levels and stay with him.
        researchOrders.deleteAll(researchOrders.findByVillageIdAndEndAtAfter(villageId, at));
        tradeOffers.deleteAll(tradeOffers.findByVillageIdOrderByIdAsc(villageId));
        for (TradeTransport transport :
                transports.findBySourceVillageIdAndReturnAtAfter(villageId, at)) {
            transport.lostWithVillage(at);
        }
        // the farm list is the old owner's: the new one starts with an empty one
        farmTargets.deleteAll(farmTargets.findByVillageIdOrderByIdAsc(villageId));
        // so is the automation (1.6, 02-game-rules §25); its pending look finds nothing to do
        automations.deleteForVillage(villageId);
        // and the old owner's tag (1.7, §26)
        villageTags.deleteForVillage(villageId);
    }

    /**
     * The village's own units are gone: at home, supporting elsewhere and on the way (with their
     * events). The owners of the villages where they stood or were heading are told
     * (VILLAGE_UPDATED), except {@code quietPlayerId}.
     *
     * @param quietPlayerId a player who hears about it otherwise (e.g. the deleted player); null
     *     tells everyone
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dropUnits(Village village, Long quietPlayerId) {
        UnitCounts home = unitService.homeUnits(village.getId());
        home.toMap().forEach((type, count) -> home.add(type, -count));
        for (UnitStack stack : stacks.findByHomeVillageId(village.getId())) {
            if (!stack.isHome()) {
                notifyOwnerOf(stack.getLocationVillage(), quietPlayerId);
                stacks.delete(stack);
            }
        }
        for (Movement movement : movements.findByHomeVillageId(village.getId())) {
            // false: the worker holds the event and waits for our lock; it then finds it gone
            scheduler.cancel(GameEventKind.MOVEMENT_ARRIVE, movement.getId());
            // an attack on its way disappears from the target's incoming list
            notifyOwnerOf(movement.getTargetVillage(), quietPlayerId);
            movements.delete(movement);
        }
        // nobody waits for them any more (1.6)
        returnWatches.deleteByVillage(village.getId());
    }

    private void notifyOwnerOf(Village village, Long quietPlayerId) {
        Player owner = village.getPlayer();
        if (owner != null && !Objects.equals(owner.getId(), quietPlayerId)) {
            events.publishEvent(
                    new PlayerNotification(
                            owner.getId(), new GameNotification.VillageUpdated(village.getId())));
        }
    }
}
