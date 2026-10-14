package de.bannerfall.movement;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.combat.BattleService;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.report.SupportReport;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageStateService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * A movement arrives (02-game-rules §6): both villages are locked and brought up to the arrival,
 * then
 *
 * <ul>
 *   <li>SUPPORT: the units join (or create) the stack of their home village in the target, both
 *       owners get a SUPPORT_ARRIVED report and notification;
 *   <li>RETURN: the units join the home stack, the loot is received like incoming resources; the
 *       owner is notified (VILLAGE_UPDATED);
 *   <li>ATTACK: the battle ({@link BattleService#resolve}). An attack on a village that belongs to
 *       its sender by now (he conquered it meanwhile, e.g. with an earlier noble) doesn't fight his
 *       own units: it arrives like a support.
 * </ul>
 *
 * The movement row is deleted afterwards. Population needs no booking: units on the way were
 * counted already.
 */
@Component
class MovementArriveHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MovementArriveHandler.class);

    private final MovementRepository movements;
    private final UnitStackRepository stacks;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final BattleService battleService;
    private final VillageBanners villageBanners;
    private final ReportService reportService;
    private final ApplicationEventPublisher events;

    MovementArriveHandler(
            MovementRepository movements,
            UnitStackRepository stacks,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            BattleService battleService,
            VillageBanners villageBanners,
            ReportService reportService,
            ApplicationEventPublisher events) {
        this.movements = movements;
        this.stacks = stacks;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.battleService = battleService;
        this.villageBanners = villageBanners;
        this.reportService = reportService;
        this.events = events;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.MOVEMENT_ARRIVE;
    }

    @Override
    public void handle(GameEvent event) {
        long movementId = event.getRefId();
        // Only the ids here, not the movement: a request holding a village lock may still change
        // it. It is read after the locks, so we see the result.
        MovementVillages ids = movements.findVillagesById(movementId).orElse(null);
        if (ids == null) {
            log.warn("Movement {} is gone, nothing arrives", movementId);
            return;
        }
        List<Long> toLock = new ArrayList<>(List.of(ids.homeVillageId(), ids.targetVillageId()));
        if (movements.findAttackingNobles(movementId).orElse(0) > 0) {
            // A conquest deletes the target's supports elsewhere (ConquestService): lock their
            // villages now, in id order with the other two.
            toLock.addAll(stacks.findSupportLocationIds(List.of(ids.targetVillageId())));
        }
        List<Village> locked = locker.lock(toLock.toArray(Long[]::new));
        Movement movement = movements.findById(movementId).orElse(null);
        if (movement == null) {
            log.warn("Movement {} is gone, nothing arrives", movementId);
            return;
        }
        Instant at = event.getDueAt();
        for (Village village : locked) {
            if (village.getId().equals(ids.homeVillageId())
                    || village.getId().equals(ids.targetVillageId())) {
                stateService.catchUp(village, at);
            }
        }

        switch (movement.getKind()) {
            case SUPPORT -> supportArrived(movement, at);
            case RETURN -> returnArrived(movement);
            case ATTACK -> {
                if (attacksOwnVillage(movement)) {
                    supportArrived(movement, at);
                } else {
                    battleService.resolve(movement, at);
                }
            }
        }
        movements.delete(movement);
    }

    private static boolean attacksOwnVillage(Movement attack) {
        Player owner = attack.getTargetVillage().getPlayer();
        return owner != null && owner.getId() == attack.getOwnerPlayerId();
    }

    /**
     * The units join their home village's stack in the target, which is created if needed. The
     * sender and the target's owner get a report (one if it is the same player).
     */
    private void supportArrived(Movement support, Instant at) {
        Village home = support.getHomeVillage();
        Village target = support.getTargetVillage();
        UnitStack stack =
                stacks.findByHomeVillageIdAndLocationVillageId(home.getId(), target.getId())
                        .orElseGet(() -> stacks.save(new UnitStack(home, target)));
        UnitCounts units = stack.getUnits();
        support.getUnits().forEach(units::add);
        Player targetOwner = target.getPlayer();
        reportService.create(
                ReportKind.SUPPORT_ARRIVED,
                at,
                new SupportReport(
                        VillageRefDto.of(home),
                        VillageRefDto.of(target),
                        support.getUnits().toMap()),
                support.getOwnerPlayerId(),
                targetOwner == null ? null : targetOwner.getId());
        // Each owner hears about his own village; a support between two villages of the same
        // player tells him about both (the target's troops and the home's units elsewhere).
        PlayerNotification.toOwnerOf(
                target, new GameNotification.SupportArrived(target.getId()), events);
        events.publishEvent(
                new PlayerNotification(
                        support.getOwnerPlayerId(),
                        new GameNotification.SupportArrived(home.getId())));
    }

    /** The units join the home stack; the loot is capped at the warehouse (02-game-rules §3). */
    private void returnArrived(Movement back) {
        Village home = back.getHomeVillage();
        UnitCounts homeUnits = unitService.homeUnits(home.getId());
        back.getUnits().forEach(homeUnits::add);
        home.receiveResources(back.getLoot(), villageBanners.warehouseCapacity(home));
        PlayerNotification.toOwnerOf(
                home, new GameNotification.VillageUpdated(home.getId()), events);
    }
}
