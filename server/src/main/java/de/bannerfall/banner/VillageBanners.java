package de.bannerfall.banner;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.player.Player;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.Storage;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageBannerDto;
import de.bannerfall.world.WorldService;
import org.springframework.stereotype.Component;

/**
 * What the banner in a village does right now (02-game-rules §21.1, §21.2): the lookup the economy
 * code calls wherever it reads production, capacities or times. It costs a query only for a village
 * of a human with a Banner Hall; every other village is answered from what is loaded already.
 *
 * <p>The effect is {@link BannerEffect#NONE} when banners are switched off in the world, when the
 * village belongs to nobody or to an NPC lord (a banner a lord conquered stays in the village
 * dormant, §21.6.1), when the hall is gone (the banner is dormant), or when the banner's owner is
 * not the village's owner.
 */
@Component
public class VillageBanners {

    private final BannerRepository banners;
    private final BannerCatalog catalog;
    private final WorldService worldService;

    VillageBanners(BannerRepository banners, BannerCatalog catalog, WorldService worldService) {
        this.banners = banners;
        this.catalog = catalog;
        this.worldService = worldService;
    }

    /** The effect of the banner in {@code village}; the caller has locked the village. */
    public BannerEffect effectOf(Village village) {
        Player owner = village.getPlayer();
        int hall = village.getBuildings().get(BuildingType.BANNER_HALL);
        if (owner == null
                || hall < 1
                || owner.isNpc()
                || !worldService.settings().bannersEnabled()) {
            return BannerEffect.NONE;
        }
        return banners.findByEquippedVillageId(village.getId())
                .filter(banner -> banner.getOwnerId() == owner.getId())
                .map(banner -> effectOf(banner, hall))
                .orElse(BannerEffect.NONE);
    }

    /** The effect of {@code banner} in a village whose hall has {@code hallLevel}. */
    BannerEffect effectOf(Banner banner, int hallLevel) {
        int effective = BannerRules.effectiveTier(banner.getTier(), hallLevel);
        double bonus = effective == 0 ? 0 : catalog.effect(banner.getType(), effective);
        return new BannerEffect(banner.getType(), banner.getTier(), effective, bonus);
    }

    /**
     * The banner for the owner's village page: type, tier and the tier it works at; null if none
     * flies there. Shown to the owner only. Unlike {@link #effectOf} a dormant banner shows (that
     * is how the owner learns that the hall is missing), and so does one in a world with banners
     * off, there with effective tier 0 because it does nothing.
     */
    public VillageBannerDto flying(Village village) {
        int hall =
                worldService.settings().bannersEnabled()
                        ? village.getBuildings().get(BuildingType.BANNER_HALL)
                        : 0;
        return banners.findByEquippedVillageId(village.getId())
                .map(
                        banner ->
                                new VillageBannerDto(
                                        banner.getType(),
                                        banner.getTier(),
                                        BannerRules.effectiveTier(banner.getTier(), hall)))
                .orElse(null);
    }

    /** Warehouse capacity per resource with the Treasury bonus (02-game-rules §3, §21.2). */
    public int warehouseCapacity(Village village) {
        return Storage.warehouseCapacity(
                village.getBuildings().get(BuildingType.WAREHOUSE), effectOf(village).treasury());
    }

    /** Hiding place capacity per resource with the Treasury bonus. */
    public int hidingCapacity(Village village) {
        return Storage.hidingCapacity(
                village.getBuildings().get(BuildingType.HIDING_PLACE),
                effectOf(village).treasury());
    }
}
