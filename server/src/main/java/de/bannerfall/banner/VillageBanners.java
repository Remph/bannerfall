package de.bannerfall.banner;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildBannerLevels;
import de.bannerfall.movement.CommandBanner;
import de.bannerfall.player.Player;
import de.bannerfall.research.UnitLevelService;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.GuildEffect;
import de.bannerfall.rules.StandardEffect;
import de.bannerfall.rules.Storage;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageBannerDto;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * What the banner in a village does right now (02-game-rules §21.1, §21.2): the lookup the economy
 * code calls wherever it reads production, capacities or times. It also answers for the village's
 * paladin standard (1.4, §21.18), which the battle code reads. It costs a query only for a village
 * of a human with a Banner Hall; every other village is answered from what is loaded already.
 *
 * <p>The effect is {@link BannerEffect#NONE} when banners are switched off in the world, when the
 * village belongs to nobody or to an NPC lord while lord banners are off (a banner a lord conquered
 * then stays in the village dormant, §21.6.1; 09-npc-lords §14), when the hall is gone (the banner
 * is dormant), or when the banner's owner is not the village's owner.
 */
@Component
public class VillageBanners {

    private final BannerRepository banners;
    private final BannerCatalog catalog;
    private final WorldService worldService;
    private final UnitLevelService unitLevels;
    private final GuildBannerLevels guildBanners;

    VillageBanners(
            BannerRepository banners,
            BannerCatalog catalog,
            WorldService worldService,
            UnitLevelService unitLevels,
            GuildBannerLevels guildBanners) {
        this.banners = banners;
        this.catalog = catalog;
        this.worldService = worldService;
        this.unitLevels = unitLevels;
        this.guildBanners = guildBanners;
    }

    /**
     * The effect of the banner in {@code village} with the owner's guild banner added (1.7,
     * 02-game-rules §16.4); the caller has locked the village.
     */
    public BannerEffect effectOf(Village village) {
        BannerEffect own = ownEffectOf(village);
        GuildEffect guild = guildBanners.effectOf(village.getPlayer());
        return guild.equals(GuildEffect.NONE) ? own : own.withGuild(guild);
    }

    /**
     * The guild banner's bonus on a support sent from a village of {@code from} to one of {@code
     * to} (1.7, 02-game-rules §16.4, level 2): travel time −, only between two guild mates.
     */
    public double guildSupportHaste(Player from, Player to) {
        return guildBanners.supportHaste(from, to);
    }

    /** The effect of the banner that flies in {@code village} itself (§21). */
    private BannerEffect ownEffectOf(Village village) {
        Player owner = village.getPlayer();
        int hall = village.getBuildings().get(BuildingType.BANNER_HALL);
        if (owner == null || hall < 1 || !worksFor(owner)) {
            return BannerEffect.NONE;
        }
        return banners.findFlying(village.getId())
                .filter(banner -> banner.getOwnerId() == owner.getId())
                .map(banner -> effectOf(banner, hall))
                .orElse(BannerEffect.NONE);
    }

    /**
     * Whether banners work in the villages of {@code owner}: while banners are on, for a human
     * always, for an NPC lord only with lord banners on (1.4, 09-npc-lords §14).
     */
    private boolean worksFor(Player owner) {
        var settings = worldService.settings();
        return settings.bannersEnabled() && (!owner.isNpc() || settings.lordBanners());
    }

    /** The effect of {@code banner} in a village whose hall has {@code hallLevel}. */
    BannerEffect effectOf(Banner banner, int hallLevel) {
        return catalog.flying(banner.getType(), banner.getTier(), hallLevel);
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
        return banners.findFlying(village.getId())
                .map(
                        banner ->
                                new VillageBannerDto(
                                        banner.getType(),
                                        banner.getTier(),
                                        BannerRules.effectiveTier(banner.getTier(), hall)))
                .orElse(null);
    }

    /**
     * The paladin standard of {@code village} as its paladins carry it at {@code at} (1.4,
     * 02-game-rules §21.18): its kind, tier and the tier it works at ({@link
     * BannerRules#standardTier}, capped by the owner's paladin level then). Empty when none is
     * equipped there, or when it can't work at all: banners switched off, a village of nobody or of
     * an NPC lord while lord banners are off, or a standard that isn't the village owner's. A
     * sleeping one (no Banner Hall) is there with effective tier 0. The caller decides whether a
     * paladin carries it.
     */
    public Optional<CommandBanner.Standard> standard(Village village, Instant at) {
        Player owner = village.getPlayer();
        if (owner == null || !worksFor(owner)) {
            return Optional.empty();
        }
        int hall = village.getBuildings().get(BuildingType.BANNER_HALL);
        return banners.findStandard(village.getId())
                .filter(standard -> standard.getOwnerId() == owner.getId())
                .map(
                        standard ->
                                new CommandBanner.Standard(
                                        standard.getId(),
                                        standard.getType(),
                                        standard.getTier(),
                                        BannerRules.standardTier(
                                                standard.getTier(),
                                                unitLevels
                                                        .levelsOf(owner, at)
                                                        .get(UnitType.PALADIN),
                                                hall)));
    }

    /**
     * The paladin standard equipped in a village, whoever's it is and whether it works (what a
     * successful scout sees, §21.18); empty if none. Nothing while banners are switched off.
     */
    public Optional<Banner> equippedStandard(Village village) {
        return worldService.settings().bannersEnabled()
                ? banners.findStandard(village.getId())
                : Optional.empty();
    }

    /**
     * What a stack of the village's units does with its standard in a battle at {@code at}
     * (§21.18): nothing without a paladin in the stack.
     */
    public StandardEffect standardEffectOf(Village home, boolean withPaladin, Instant at) {
        if (!withPaladin) {
            return StandardEffect.NONE;
        }
        return standard(home, at).map(this::effectOf).orElse(StandardEffect.NONE);
    }

    /** The effect of a standard as it was carried (an attack's snapshot); NONE for null. */
    public StandardEffect effectOf(CommandBanner.Standard standard) {
        if (standard == null) {
            return StandardEffect.NONE;
        }
        return catalog.standard(standard.type(), standard.tier(), standard.effectiveTier());
    }

    /** Warehouse capacity per resource with the Treasury bonus (02-game-rules §3, §21.2). */
    public int warehouseCapacity(Village village) {
        return Storage.warehouseCapacity(
                village.getBuildings().get(BuildingType.WAREHOUSE),
                ownEffectOf(village).treasury());
    }

    /** Hiding place capacity per resource with the Treasury bonus. */
    public int hidingCapacity(Village village) {
        return Storage.hidingCapacity(
                village.getBuildings().get(BuildingType.HIDING_PLACE),
                ownEffectOf(village).treasury());
    }
}
