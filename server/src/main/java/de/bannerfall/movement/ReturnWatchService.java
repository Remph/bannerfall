package de.bannerfall.movement;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Notify me when they're back" (1.6, 02-game-rules §6, 01-architecture §4.8b): a {@link
 * ReturnWatch} over the attacks the player sent together, and one ATTACKS_RETURNED for the player
 * when the last of them is home or died. The push for it has its own switch ({@code
 * PushKind.ATTACK_RETURNED}); the toast comes either way.
 *
 * <p>Everything here runs under the lock of the attacks' home village (sending, cancelling, the
 * battle and the arrival all hold it), so the last one is counted exactly once.
 */
@Service
public class ReturnWatchService {

    private final ReturnWatchRepository watches;
    private final MovementRepository movements;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReturnWatchService(
            ReturnWatchRepository watches,
            MovementRepository movements,
            VillageRepository villages,
            VillageLocker locker,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock) {
        this.watches = watches;
        this.movements = movements;
        this.villages = villages;
        this.locker = locker;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Watches attacks that have just left {@code home} together; nothing for none.
     *
     * @param target the one target of a single attack or a train; null for a farm list run
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void watch(
            long playerId,
            Village home,
            Village target,
            boolean farmRun,
            List<Movement> attacks,
            Instant now) {
        if (attacks.isEmpty()) {
            return;
        }
        ReturnWatch watch =
                watches.save(
                        new ReturnWatch(
                                playerId,
                                home.getId(),
                                target == null ? null : target.getId(),
                                farmRun,
                                attacks.size(),
                                now));
        attacks.forEach(attack -> attack.watchReturn(watch.getId()));
    }

    /** A farm list run (1.6): {@link #watch} for the attacks it sent, by their ids. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void watchFarmRun(long playerId, Village home, List<Long> movementIds, Instant now) {
        watch(playerId, home, null, true, movements.findAllById(movementIds), now);
    }

    /**
     * The bell on one of the player's movements (PUT /api/movements/{id}/return-push): on watches
     * it alone, off takes it out of its watch (a farm list run goes on without it).
     *
     * @throws NotFoundException {@code MOVEMENT_NOT_FOUND} if the player has no such movement
     *     (never had, or it has arrived)
     * @throws GameRuleException {@code BAD_REQUEST} for a support, which doesn't come back
     */
    @Transactional
    public MovementDto setWatched(long movementId, long playerId, boolean on) {
        long homeId =
                movements
                        .findHomeVillageIdOfOwner(movementId, playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MOVEMENT_NOT_FOUND));
        locker.lock(homeId);
        // Read after the lock: the arrival may have deleted it meanwhile.
        Movement movement =
                movements
                        .findById(movementId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MOVEMENT_NOT_FOUND));
        if (movement.getKind() == MovementKind.SUPPORT) {
            throw new GameRuleException(ErrorCode.BAD_REQUEST, "A support doesn't come back");
        }
        Long watchId = movement.getReturnWatchId();
        if (on && watchId == null) {
            // where the units are coming back from: the target of an attack, the source of a RETURN
            Village from =
                    movement.getKind() == MovementKind.ATTACK
                            ? movement.getTargetVillage()
                            : movement.getSourceVillage();
            watch(playerId, movement.getHomeVillage(), from, false, List.of(movement), now());
        } else if (!on && watchId != null) {
            watches.findById(watchId).ifPresent(watch -> leave(watch, movement));
        }
        return MovementDto.of(movement, worldService.settings().cancelWindowSeconds());
    }

    /** A RETURN arrived and its units are home. */
    @Transactional(propagation = Propagation.MANDATORY)
    void arrived(Movement back) {
        watchOf(back)
                .ifPresent(
                        watch -> {
                            watch.cameBack(back.getLoot());
                            finishIfLast(watch, back);
                        });
    }

    /** An attack's battle left no survivors: nothing walks home. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void nobodyBack(Movement attack) {
        watchOf(attack)
                .ifPresent(
                        watch -> {
                            watch.lostOne();
                            finishIfLast(watch, attack);
                        });
    }

    /**
     * A watched attack won't come back (its survivors stay in the village it conquered, or it
     * arrived at a village of the player's own): the watch waits for the others only.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dropped(Movement movement) {
        watchOf(movement).ifPresent(watch -> leave(watch, movement));
    }

    private Optional<ReturnWatch> watchOf(Movement movement) {
        Long watchId = movement.getReturnWatchId();
        return watchId == null ? Optional.empty() : watches.findById(watchId);
    }

    /**
     * The movement leaves the watch without a word. If it was the last one on the way, the others
     * of its group are all home or dead: they get their notification now; a watch left with none
     * goes quietly.
     */
    private void leave(ReturnWatch watch, Movement movement) {
        movement.watchReturn(null);
        watch.leftOne();
        if (movements.countByReturnWatchIdAndIdNot(watch.getId(), movement.getId()) > 0) {
            return;
        }
        if (watch.getAttacks() > 0) {
            finish(watch);
        } else {
            watches.delete(watch);
        }
    }

    /** If {@code movement} was the last one on the way: the notification, and the watch goes. */
    private void finishIfLast(ReturnWatch watch, Movement movement) {
        if (movements.countByReturnWatchIdAndIdNot(watch.getId(), movement.getId()) > 0) {
            return;
        }
        movement.watchReturn(null);
        finish(watch);
    }

    /** Every attack of the watch is home or dead: the notification, and the watch goes. */
    private void finish(ReturnWatch watch) {
        VillageRefDto target =
                watch.getTargetVillageId() == null
                        ? null
                        : villages.findById(watch.getTargetVillageId())
                                .map(VillageRefDto::of)
                                .orElse(null);
        events.publishEvent(
                new PlayerNotification(
                        watch.getPlayerId(),
                        new GameNotification.AttacksReturned(
                                watch.getVillageId(),
                                target,
                                watch.getAttacks(),
                                watch.getLost(),
                                watch.getLoot(),
                                watch.isFarmRun())));
        watches.delete(watch);
    }

    private Instant now() {
        return clock.instant();
    }
}
