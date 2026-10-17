package de.bannerfall.village;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.banner.VillageBanners;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.recruit.DismissOrder;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitOrderRepository;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.rules.Production;
import de.bannerfall.rules.Storage;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Lazy village state (01-architecture §4.4): brings a village forward to a point in time. Call it
 * at the start of every transaction that reads or changes a village's resources, units or loyalty,
 * on a <b>locked</b> village: user requests with {@code t = now}, event handlers with {@code t =
 * event.dueAt}.
 */
@Service
public class VillageStateService {

    private final WorldService worldService;
    private final RecruitOrderRepository recruitOrders;
    private final DismissOrderRepository dismissOrders;
    private final UnitService unitService;
    private final AchievementService achievements;
    private final VillageBanners villageBanners;

    VillageStateService(
            WorldService worldService,
            RecruitOrderRepository recruitOrders,
            DismissOrderRepository dismissOrders,
            UnitService unitService,
            AchievementService achievements,
            VillageBanners villageBanners) {
        this.worldService = worldService;
        this.recruitOrders = recruitOrders;
        this.dismissOrders = dismissOrders;
        this.unitService = unitService;
        this.achievements = achievements;
        this.villageBanners = villageBanners;
    }

    /**
     * Resources and loyalty up to {@code now}, and the units of recruit orders that are done by
     * {@code now} into the home stack; in a world that has ended only up to its end. Idempotent and
     * never goes backwards.
     *
     * @return the village's recruit orders that are still open after the catch-up, each building's
     *     queue in running order (like {@code RecruitOrderRepository
     *     .findByVillageIdOrderByEndAtAscIdAsc}); pass them on instead of reading them again
     */
    public List<RecruitOrder> catchUp(Village village, Instant now) {
        // A frozen world (v1.3, 02-game-rules §22) stands still at its end: reading a village then
        // must not fill its warehouse or finish its units.
        Instant t = worldService.gameTime(now);
        advance(village, t, worldService.settings(), villageBanners.effectOf(village));
        completeDismissals(village.getId(), t);
        return completeRecruitment(village, t);
    }

    /**
     * Recruitment needs no events (02-game-rules §5.2): whoever brings the village forward moves
     * the units done by {@code t} into the home stack, one order after another, and deletes the
     * orders whose last unit is done. Population doesn't change: units in training were counted
     * already. An earlier {@code t} than before moves nothing ({@code completedCount} only grows).
     *
     * @return the orders that are still open
     */
    private List<RecruitOrder> completeRecruitment(Village village, Instant t) {
        long villageId = village.getId();
        List<RecruitOrder> open = new ArrayList<>();
        UnitCounts home = null;
        for (RecruitOrder order : recruitOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            int finished = order.completeUpTo(t);
            if (finished > 0) {
                if (home == null) {
                    home = unitService.homeUnits(villageId);
                }
                home.add(order.getUnit(), finished);
                unitsFinished(village, order.getUnit(), finished);
            }
            if (order.isComplete()) {
                recruitOrders.delete(order);
            } else {
                open.add(order);
            }
        }
        return open;
    }

    /**
     * Siegecraft, Noble Blood and Standing Army (02-game-rules §19). A catch-up of a village the
     * owner doesn't look at (an attack on it) can finish his units too: they are his all the same.
     */
    private void unitsFinished(Village village, UnitType unit, int count) {
        Player owner = village.getPlayer();
        if (owner == null) {
            return;
        }
        switch (unit) {
            case RAM, CATAPULT -> achievements.grant(owner, Achievement.SIEGECRAFT);
            case NOBLE -> achievements.grant(owner, Achievement.NOBLE_BLOOD);
            default -> {}
        }
        if (unit != UnitType.SCOUT && unit != UnitType.NOBLE) {
            achievements.count(owner, Achievement.STANDING_ARMY, count);
        }
    }

    /**
     * Dismissing needs no events either (02-game-rules §5.2a): the units left the home stack when
     * the order was placed, so the catch-up only counts the finished ones (their population is free
     * from then on, {@code UnitService.unitsOf} counts what remains) and deletes the orders whose
     * last unit is gone.
     */
    private void completeDismissals(long villageId, Instant t) {
        for (DismissOrder order : dismissOrders.findByVillageIdOrderByEndAtAscIdAsc(villageId)) {
            order.completeUpTo(t);
            if (order.isComplete()) {
                dismissOrders.delete(order);
            }
        }
    }

    /** {@link #advance(Village, Instant, WorldSettings, BannerEffect)} for a village without. */
    static void advance(Village village, Instant t, WorldSettings world) {
        advance(village, t, world, BannerEffect.NONE);
    }

    /**
     * The pure part of {@link #catchUp}: no database, unit-testable.
     *
     * @param banner what the village's banner does (Harvest and Treasury, 02-game-rules §21.2)
     */
    static void advance(Village village, Instant t, WorldSettings world, BannerEffect banner) {
        Instant resourcesFrom = village.getResourcesUpdatedAt();
        double capacity =
                Storage.warehouseCapacity(
                        village.getBuildings().get(BuildingType.WAREHOUSE), banner.treasury());
        village.setResources(
                Production.advance(
                        village.getWood(),
                        productionPerHour(village, ResourceType.WOOD, world, banner),
                        capacity,
                        resourcesFrom,
                        t),
                Production.advance(
                        village.getClay(),
                        productionPerHour(village, ResourceType.CLAY, world, banner),
                        capacity,
                        resourcesFrom,
                        t),
                Production.advance(
                        village.getIron(),
                        productionPerHour(village, ResourceType.IRON, world, banner),
                        capacity,
                        resourcesFrom,
                        t),
                Production.newAnchor(resourcesFrom, t));

        Instant loyaltyFrom = village.getLoyaltyUpdatedAt();
        village.setLoyalty(
                Loyalty.regen(village.getLoyalty(), Loyalty.regenPerHour(world), loyaltyFrom, t),
                Loyalty.newAnchor(loyaltyFrom, t));
    }

    /**
     * Production per hour of one resource with the village's current levels and the owner's
     * production factor (1.0 for humans and barbarian villages, an NPC lord's own, 09 §8), and the
     * village's Harvest banner.
     */
    static double productionPerHour(
            Village village, ResourceType resource, WorldSettings world, BannerEffect banner) {
        Player owner = village.getPlayer();
        double factor = owner == null ? 1.0 : owner.getProductionFactor();
        return BannerRules.production(
                Production.perHour(
                        village.getBuildings().get(resource.producer()), world.speed(), factor),
                banner.harvest());
    }
}
