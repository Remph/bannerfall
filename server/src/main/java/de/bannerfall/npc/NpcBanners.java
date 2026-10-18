package de.bannerfall.npc;

import de.bannerfall.banner.Banner;
import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.banner.BannerRepository;
import de.bannerfall.banner.BannerService;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.research.ResearchOrder;
import de.bannerfall.research.UnitLevelService;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.unit.UnitService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The banners of an NPC lord (lord banners, 1.4, 09-npc-lords §14): which banner and standard fly
 * in a village, fusing the stash, and the rules for its thieves, its paladin on retaliations and
 * his Hall training. Everything goes through {@link BannerService}, checked first so that nothing
 * throws. While {@link WorldSettings#lordBanners()} is off it does nothing.
 */
@Component
class NpcBanners {

    /** A banner as the lord's choice sees it. */
    record Held(long id, BannerType type, int tier) {

        static Held of(Banner banner) {
            return new Held(banner.getId(), banner.getType(), banner.getTier());
        }
    }

    /** One fusion: the three banners and the type of the new one. */
    record Fusion(List<Long> ids, BannerType type) {}

    private final BannerRepository banners;
    private final BannerService bannerService;
    private final BannerCatalog catalog;
    private final UnitService unitService;
    private final UnitLevelService unitLevels;
    private final WorldService worldService;
    private final GameData gameData;

    NpcBanners(
            BannerRepository banners,
            BannerService bannerService,
            BannerCatalog catalog,
            UnitService unitService,
            UnitLevelService unitLevels,
            WorldService worldService,
            GameData gameData) {
        this.banners = banners;
        this.bannerService = bannerService;
        this.catalog = catalog;
        this.unitService = unitService;
        this.unitLevels = unitLevels;
        this.worldService = worldService;
        this.gameData = gameData;
    }

    // ---- choosing (§14.3) ----

    /**
     * §14.3, in the village's turn after the defence step: the slot gets the banner with the
     * highest effective tier among the one flying there and the stash, and the standard slot the
     * same with the standards, if a paladin of the village is home or out. A locked slot stays as
     * it is. The caller holds the village's lock.
     */
    void fly(NpcTurn turn) {
        WorldSettings world = turn.view.world();
        int hall = turn.village.getBuildings().get(BuildingType.BANNER_HALL);
        if (!world.lordBanners() || hall < 1) {
            return;
        }
        List<Held> stash =
                banners.findByOwnerIdOrderByIdAsc(turn.lordId()).stream()
                        .filter(banner -> !banner.isEquipped())
                        .map(Held::of)
                        .toList();
        if (stash.isEmpty()) {
            return;
        }
        fill(turn, stash, false, hall, 0);
        if (world.paladinMaxSlots() >= 1
                && unitService.unitsOf(turn.villageId(), List.of()).all().get(UnitType.PALADIN)
                        > 0) {
            int paladinLevel =
                    unitLevels.levelsOf(turn.village.getPlayer(), turn.now).get(UnitType.PALADIN);
            fill(turn, stash, true, hall, paladinLevel);
        }
    }

    private void fill(
            NpcTurn turn, List<Held> stash, boolean standards, int hall, int paladinLevel) {
        Held current =
                (standards
                                ? banners.findStandard(turn.villageId())
                                : banners.findFlying(turn.villageId()))
                        .filter(banner -> banner.getOwnerId() == turn.lordId())
                        .map(Held::of)
                        .orElse(null);
        Optional<Held> best = best(current, stash, standards, hall, paladinLevel, turn.template());
        if (best.isEmpty()
                || current != null && best.get().id() == current.id()
                || bannerService.slotLocked(turn.villageId(), standards)) {
            return;
        }
        bannerService.equip(turn.villageId(), turn.lordId(), best.get().id());
    }

    /**
     * The banner (or with {@code standards} the standard) a slot should hold (§14.3): the highest
     * effective tier among {@code current} and the stash, ties to the earlier favourite, then the
     * lower id. Empty if there is none of the family.
     *
     * @param current what flies in the slot now, null if nothing
     * @param paladinLevel the lord's paladin level (caps a standard's tier)
     */
    static Optional<Held> best(
            Held current,
            List<Held> stash,
            boolean standards,
            int hall,
            int paladinLevel,
            NpcTemplates.Template template) {
        List<Held> candidates = new ArrayList<>();
        if (current != null) {
            candidates.add(current);
        }
        for (Held held : stash) {
            if (held.type().isStandard() == standards) {
                candidates.add(held);
            }
        }
        return candidates.stream()
                .min(
                        Comparator.comparingInt(
                                        (Held held) -> -effectiveTier(held, hall, paladinLevel))
                                .thenComparingInt(held -> template.rank(held.type()))
                                .thenComparingLong(Held::id));
    }

    static int effectiveTier(Held held, int hall, int paladinLevel) {
        return held.type().isStandard()
                ? BannerRules.standardTier(held.tier(), paladinLevel, hall)
                : BannerRules.effectiveTier(held.tier(), hall);
    }

    // ---- fusing (§14.3) ----

    /**
     * §14.3, after the finish step: while the stash holds enough banners of one family and tier
     * below the highest, the lord fuses its least favourite ones into its best favourite of them.
     */
    void fuse(long lordId, NpcTemplates.Template template) {
        if (!worldService.settings().lordBanners()) {
            return;
        }
        int needed = catalog.fusing().bannersNeeded();
        while (true) {
            List<Held> stash =
                    banners.findByOwnerIdOrderByIdAsc(lordId).stream()
                            .filter(banner -> !banner.isEquipped())
                            .map(Held::of)
                            .toList();
            Optional<Fusion> next = nextFusion(stash, template, needed);
            if (next.isEmpty()) {
                return;
            }
            bannerService.fuse(lordId, next.get().ids(), next.get().type());
        }
    }

    /**
     * The next fusion of a stash (§14.3): the lowest tier first, banners before standards; of that
     * tier the {@code needed} least favourite (ties: the lower id), the new one of the best
     * favourite type among them. Empty if no tier below the highest holds enough.
     */
    static Optional<Fusion> nextFusion(
            List<Held> stash, NpcTemplates.Template template, int needed) {
        for (int tier = 1; tier < BannerCatalog.TIERS; tier++) {
            for (boolean standards : new boolean[] {false, true}) {
                int t = tier;
                List<Held> parts =
                        stash.stream()
                                .filter(h -> h.tier() == t && h.type().isStandard() == standards)
                                .sorted(
                                        Comparator.comparingInt(
                                                        (Held h) -> -template.rank(h.type()))
                                                .thenComparingLong(Held::id))
                                .limit(needed)
                                .toList();
                if (parts.size() == needed) {
                    BannerType type =
                            parts.stream()
                                    .map(Held::type)
                                    .min(Comparator.comparingInt(template::rank))
                                    .orElseThrow();
                    return Optional.of(new Fusion(parts.stream().map(Held::id).toList(), type));
                }
            }
        }
        return Optional.empty();
    }

    // ---- thieves and the paladin (§14.4, §14.5) ----

    /**
     * §14.4: the banner thieves a village should still recruit: up to the template's {@code
     * thieves}, with a Banner Hall and lord banners on.
     *
     * @param have the village's thieves, home, out and in recruit orders
     */
    static int thievesWanted(
            NpcTemplates.Template template, WorldSettings world, int hall, int have) {
        if (!world.lordBanners() || template.thieves() <= 0 || hall < 1) {
            return 0;
        }
        return Math.max(0, template.thieves() - have);
    }

    /**
     * §14.4, §14.5: what a retaliation takes along on top of its attack units, from the units at
     * home (the home guard doesn't apply): all thieves if the target's intel saw a banner, and the
     * paladin where the template says so. Nothing while lord banners are off.
     */
    static Map<UnitType, Integer> retaliationExtras(
            NpcTemplates.Template template,
            WorldSettings world,
            Intel intel,
            Map<UnitType, Integer> home) {
        Map<UnitType, Integer> extras = new EnumMap<>(UnitType.class);
        if (!world.lordBanners()) {
            return extras;
        }
        int thieves = home.getOrDefault(UnitType.BANNER_THIEF, 0);
        if (template.thieves() > 0 && thieves > 0 && intel != null && intel.banner() != null) {
            extras.put(UnitType.BANNER_THIEF, thieves);
        }
        int paladins = home.getOrDefault(UnitType.PALADIN, 0);
        if (template.paladinRetaliates() && world.paladinMaxSlots() >= 1 && paladins > 0) {
            extras.put(UnitType.PALADIN, paladins);
        }
        return extras;
    }

    // ---- Hall training (§14.6) ----

    /**
     * §14.6: the paladin level a village should start training now, or 0: with lord banners and
     * paladins on, Banner Hall 2 or more, the lord's level below the hall's and the unit's highest,
     * and no paladin training running in the lord's account or any training in this village's hall.
     * The caller checks the stock.
     *
     * @param researches all researches of the lord
     */
    int paladinLevelToTrain(
            WorldSettings world,
            long villageId,
            int hall,
            List<ResearchOrder> researches,
            Instant now) {
        if (!world.lordBanners() || world.paladinMaxSlots() < 1 || hall < 2) {
            return 0;
        }
        int level = 1;
        for (ResearchOrder order : researches) {
            boolean hallUnit =
                    gameData.unit(order.getUnit()).building() == BuildingType.BANNER_HALL;
            if (!order.isDoneBy(now)) {
                if (order.getUnit() == UnitType.PALADIN
                        || hallUnit && order.getVillageId() == villageId) {
                    return 0;
                }
            } else if (order.getUnit() == UnitType.PALADIN) {
                level = Math.max(level, order.getLevel());
            }
        }
        int next = level + 1;
        return next <= Math.min(hall, gameData.unit(UnitType.PALADIN).maxLevel()) ? next : 0;
    }
}
