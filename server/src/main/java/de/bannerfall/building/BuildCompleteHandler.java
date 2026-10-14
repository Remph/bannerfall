package de.bannerfall.building;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.village.PointsService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * An upgrade is done (01-architecture §4.6): the village is brought up to the moment the order
 * ended (so production up to then still uses the old level), the building gets one level more, the
 * order is deleted, the points are recalculated and the owner is notified (BUILD_COMPLETE).
 * Population needs no booking: queued levels were already counted.
 *
 * <p>Runs in the event worker's transaction: first the event row is locked (by the processor), then
 * the village (here), like in every handler.
 */
@Component
class BuildCompleteHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BuildCompleteHandler.class);

    private final BuildOrderRepository orders;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final PointsService pointsService;
    private final ApplicationEventPublisher events;
    private final AchievementService achievements;

    BuildCompleteHandler(
            BuildOrderRepository orders,
            VillageLocker locker,
            VillageStateService stateService,
            PointsService pointsService,
            ApplicationEventPublisher events,
            AchievementService achievements) {
        this.orders = orders;
        this.locker = locker;
        this.stateService = stateService;
        this.pointsService = pointsService;
        this.events = events;
        this.achievements = achievements;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.BUILD_COMPLETE;
    }

    @Override
    public void handle(GameEvent event) {
        long orderId = event.getRefId();
        // Only the village id here, not the order: a cancel that holds the village lock may still
        // change or delete the order. It is read after the lock, so we see its result.
        Long villageId = orders.findVillageIdById(orderId).orElse(null);
        if (villageId == null) {
            log.warn("Build order {} is gone (cancelled?), nothing to complete", orderId);
            return;
        }
        List<Village> locked = locker.lock(villageId);
        BuildOrder order = orders.findById(orderId).orElse(null);
        if (locked.isEmpty() || order == null) {
            log.warn("Build order {} is gone (cancelled?), nothing to complete", orderId);
            return;
        }
        Village village = locked.get(0);

        // Normally the event is due exactly when the order ends. If a re-timing could not move the
        // event (the worker held it), the order says earlier than the event: the order's end
        // counts.
        Instant completedAt = earlier(event.getDueAt(), order.getEndAt());
        stateService.catchUp(village, completedAt);
        village.getBuildings().add(order.getBuilding(), 1);
        orders.delete(order);
        pointsService.recalculate(village);
        building(village, order.getBuilding());
        PlayerNotification.toOwnerOf(
                village,
                new GameNotification.BuildComplete(
                        village.getId(),
                        order.getBuilding(),
                        village.getBuildings().get(order.getBuilding())),
                events);
    }

    /** Foreman and Scholar (02-game-rules §19). */
    private void building(Village village, BuildingType building) {
        int level = village.getBuildings().get(building);
        if (building == BuildingType.HEADQUARTERS && level >= AchievementService.FOREMAN_LEVEL) {
            achievements.grant(village.getPlayer(), Achievement.FOREMAN);
        } else if (building == BuildingType.ACADEMY) {
            achievements.grant(village.getPlayer(), Achievement.SCHOLAR);
        }
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
