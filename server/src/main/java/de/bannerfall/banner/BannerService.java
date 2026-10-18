package de.bannerfall.banner;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.ConquestReport;
import de.bannerfall.research.UnitLevelService;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.village.VillageSummaryDto;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The banners of a player (02-game-rules §21.1, §21.4): the stash and the slots, equipping,
 * swapping and unequipping, and what happens to a banner when its village changes hands. Every
 * method that moves a banner locks the village first and then the player row ({@code village →
 * player}, like the paladin slots), so two requests of one player can't put one banner into two
 * villages.
 *
 * <p>Equipping and unequipping change what a village produces and stores, so they catch the village
 * up first, exactly like a building level change (§21.2).
 */
@Service
public class BannerService {

    private final BannerRepository banners;
    private final BannerEventRepository events;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final MovementRepository movements;
    private final PlayerRepository players;
    private final BannerCatalog catalog;
    private final GameData gameData;
    private final WorldService worldService;
    private final AchievementService achievements;
    private final UnitLevelService unitLevels;
    private final Clock clock;

    BannerService(
            BannerRepository banners,
            BannerEventRepository events,
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            MovementRepository movements,
            PlayerRepository players,
            BannerCatalog catalog,
            GameData gameData,
            WorldService worldService,
            AchievementService achievements,
            UnitLevelService unitLevels,
            Clock clock) {
        this.banners = banners;
        this.events = events;
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.movements = movements;
        this.players = players;
        this.catalog = catalog;
        this.gameData = gameData;
        this.worldService = worldService;
        this.achievements = achievements;
        this.unitLevels = unitLevels;
        this.clock = clock;
    }

    /**
     * The player's banners with his villages' halls and slots (what the Banner Hall page shows).
     */
    @Transactional(readOnly = true)
    public BannersDto overview(long playerId) {
        return overviewOf(playerId);
    }

    /**
     * Puts a banner of the stash into the village's slot; a banner that flies there goes back into
     * the stash (a swap). A paladin standard (1.4, 02-game-rules §21.18) goes into the village's
     * standard slot the same way. The checks run in this order, the first one that fails is the
     * answer.
     *
     * @throws GameRuleException {@code BANNERS_DISABLED}, {@code BANNER_NOT_IN_STASH}, {@code
     *     PALADIN_DISABLED} (a standard in a world without paladins), {@code BANNER_HALL_REQUIRED},
     *     {@code BANNER_LOCKED} (an attack is on its way to the village; for a swap of standards
     *     also an attack with a paladin from the village)
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND}, {@code BANNER_NOT_FOUND} (not his, or
     *     gone)
     */
    @Transactional
    public BannersDto equip(long villageId, long playerId, long bannerId) {
        Village village = locker.lockOwned(villageId, playerId);
        players.lockById(playerId);
        Instant now = clock.instant();
        // before the change, like a building level change: what the village made so far was made
        // without the new banner
        stateService.catchUp(village, now);

        if (!worldService.settings().bannersEnabled()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "Banners are switched off in this world");
        }
        Banner banner =
                banners.findByIdAndOwnerId(bannerId, playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.BANNER_NOT_FOUND));
        if (banner.isEquipped()) {
            throw new GameRuleException(
                    ErrorCode.BANNER_NOT_IN_STASH, "That banner flies in a village already");
        }
        boolean standard = banner.isStandard();
        if (standard && worldService.settings().paladinMaxSlots() <= 0) {
            throw new GameRuleException(
                    ErrorCode.PALADIN_DISABLED, "There is no paladin to carry a standard");
        }
        if (village.getBuildings().get(BuildingType.BANNER_HALL) < 1) {
            throw new GameRuleException(
                    ErrorCode.BANNER_HALL_REQUIRED, "The village needs a Banner Hall");
        }
        requireNoIncomingAttack(villageId);
        Optional<Banner> equipped =
                standard ? banners.findStandard(villageId) : banners.findFlying(villageId);
        // only a standard that is there can be swapped out of an attack; the attack took its
        // standard along when it left, so an empty slot can be filled (§21.18)
        if (standard && equipped.isPresent()) {
            requireNoPaladinAttack(villageId);
        }

        equipped.ifPresent(
                current -> {
                    current.unequip();
                    // flushed so the slot is free when the new banner takes it (unique)
                    banners.saveAndFlush(current);
                    log(
                            current,
                            BannerEventKind.UNEQUIPPED,
                            playerId,
                            playerId,
                            villageId,
                            now,
                            null);
                });
        banner.equip(villageId, now);
        log(banner, BannerEventKind.EQUIPPED, playerId, playerId, villageId, now, null);
        if (!standard) {
            // the hall is at least 1 and banners are on: the banner works from now on (§21.8)
            achievements.grant(playerId, Achievement.COLOURS_RAISED);
        }
        return overviewOf(playerId);
    }

    /**
     * Whether {@link #equip} would refuse to change the village's banner slot, or its standard slot
     * for {@code standard}, with {@code BANNER_LOCKED} right now: an NPC lord checks first, so its
     * turn doesn't throw (09-npc-lords §14.3). The caller holds the village's lock.
     */
    public boolean slotLocked(long villageId, boolean standard) {
        if (movements.countIncomingAttacks(villageId) > 0) {
            return true;
        }
        return standard
                && banners.findStandard(villageId).isPresent()
                && movements.countPaladinAttacksUnderWay(villageId) > 0;
    }

    /**
     * Takes the banner out of the village's slot into the stash.
     *
     * @throws GameRuleException {@code NO_BANNER_EQUIPPED}, {@code BANNER_LOCKED}
     */
    @Transactional
    public BannersDto unequip(long villageId, long playerId) {
        return unequip(villageId, playerId, false);
    }

    /**
     * Takes the paladin standard out of the village's slot into the stash (1.4, 02-game-rules
     * §21.18).
     *
     * @throws GameRuleException {@code NO_BANNER_EQUIPPED} (no standard there), {@code
     *     BANNER_LOCKED} (an attack on its way to the village, or one with a paladin from it)
     */
    @Transactional
    public BannersDto unequipStandard(long villageId, long playerId) {
        return unequip(villageId, playerId, true);
    }

    private BannersDto unequip(long villageId, long playerId, boolean standard) {
        Village village = locker.lockOwned(villageId, playerId);
        players.lockById(playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);

        Banner banner =
                (standard ? banners.findStandard(villageId) : banners.findFlying(villageId))
                        .filter(b -> b.getOwnerId() == playerId)
                        .orElseThrow(
                                () ->
                                        new GameRuleException(
                                                ErrorCode.NO_BANNER_EQUIPPED,
                                                standard
                                                        ? "No standard is equipped in this village"
                                                        : "No banner flies in this village"));
        requireNoIncomingAttack(villageId);
        if (standard) {
            requireNoPaladinAttack(villageId);
        }
        banner.unequip();
        log(banner, BannerEventKind.UNEQUIPPED, playerId, playerId, villageId, now, null);
        return overviewOf(playerId);
    }

    /**
     * Fuses banners of the stash (02-game-rules §21.4.2): {@code fusing.bannersNeeded} (3) banners
     * of one tier become one banner of the next tier, of a type one of them has (the player picks
     * it). Paladin standards (1.4, §21.18) fuse the same way, but only with standards. The result
     * is a new banner (new id, origin FUSED) in the stash; the three are gone. Each of the three
     * leaves a FUSED line with nobody as its new owner, the new banner a FUSED line that gives it
     * to the player. Instant and free. Only the player row is locked: stash banners fly in no
     * village, and equipping locks the player row too.
     *
     * @throws GameRuleException {@code BANNERS_DISABLED}, {@code BANNER_NOT_IN_STASH} (one of them
     *     flies in a village), {@code BANNER_FAMILIES_DIFFER} (banners and standards mixed), {@code
     *     BANNER_TIERS_DIFFER}, {@code BANNER_TIER_MAX} (tier III), {@code BANNER_FUSE_TYPE} (none
     *     of them has that type)
     * @throws NotFoundException {@code BANNER_NOT_FOUND} (not his, or gone)
     * @throws ApiException 400 {@code VALIDATION} unless exactly three different banners are named
     */
    @Transactional
    public BannersDto fuse(long playerId, List<Long> bannerIds, BannerType type) {
        players.lockById(playerId);
        if (!worldService.settings().bannersEnabled()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "Banners are switched off in this world");
        }
        int needed = catalog.fusing().bannersNeeded();
        Set<Long> ids = new LinkedHashSet<>(bannerIds);
        if (ids.size() != needed || bannerIds.size() != needed) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "Fusing takes exactly " + needed + " different banners");
        }
        List<Banner> parts = new ArrayList<>();
        for (long id : ids) {
            parts.add(
                    banners.findByIdAndOwnerId(id, playerId)
                            .orElseThrow(() -> new NotFoundException(ErrorCode.BANNER_NOT_FOUND)));
        }
        if (parts.stream().anyMatch(Banner::isEquipped)) {
            throw new GameRuleException(
                    ErrorCode.BANNER_NOT_IN_STASH, "Only banners in the stash can be fused");
        }
        boolean standards = parts.get(0).isStandard();
        if (parts.stream().anyMatch(part -> part.isStandard() != standards)) {
            throw new GameRuleException(
                    ErrorCode.BANNER_FAMILIES_DIFFER,
                    "Banners fuse with banners, standards with standards");
        }
        int tier = parts.get(0).getTier();
        if (parts.stream().anyMatch(part -> part.getTier() != tier)) {
            throw new GameRuleException(
                    ErrorCode.BANNER_TIERS_DIFFER, "The banners must have the same tier");
        }
        if (tier >= BannerCatalog.TIERS) {
            throw new GameRuleException(
                    ErrorCode.BANNER_TIER_MAX, "Banners of the highest tier can't be fused");
        }
        if (parts.stream().noneMatch(part -> part.getType() == type)) {
            throw new GameRuleException(
                    ErrorCode.BANNER_FUSE_TYPE,
                    "The new banner takes the type of one of the three");
        }

        Instant now = clock.instant();
        Banner fused = banners.save(new Banner(playerId, type, tier + 1, BannerOrigin.FUSED, now));
        for (Banner part : parts) {
            log(part, BannerEventKind.FUSED, playerId, null, null, now, "into " + fused.getId());
        }
        banners.deleteAll(parts);
        String from =
                parts.stream()
                        .map(part -> part.getId().toString())
                        .collect(Collectors.joining(", "));
        log(fused, BannerEventKind.FUSED, null, playerId, null, now, "from " + from);
        achievements.checkBanners(playerId);
        return overviewOf(playerId);
    }

    /**
     * What a conquest did with the village's banner and its paladin standard, for the conquest
     * reports; each null if none was there.
     */
    public record Conquered(
            ConquestReport.ConqueredBanner banner, ConquestReport.ConqueredBanner standard) {}

    /**
     * A village changes owner by conquest (02-game-rules §21.6.1): its banner goes, intact, to the
     * conqueror's stash. An NPC lord takes it into its stash only with lord banners on (1.4,
     * 09-npc-lords §14.1); otherwise the banner stays in the village, now the lord's and dormant
     * ({@link VillageBanners}), until a human takes the village or the banner. Barbarian villages
     * carry none. With banners switched off nothing is taken (§21.9): the banner stays in the
     * village and goes with it to the new owner, asleep until banners are on again. The village's
     * paladin standard (1.4, §21.18) goes the same way, without the banner achievements. The caller
     * holds the village's lock and has already given it to {@code conqueror}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Conquered villageConquered(Village village, Player conqueror, Instant at) {
        return new Conquered(
                conquered(banners.findFlying(village.getId()).orElse(null), village, conqueror, at),
                conquered(
                        banners.findStandard(village.getId()).orElse(null),
                        village,
                        conqueror,
                        at));
    }

    /** One banner or standard of a conquered village; null if there is none. */
    private ConquestReport.ConqueredBanner conquered(
            Banner banner, Village village, Player conqueror, Instant at) {
        if (banner == null) {
            return null;
        }
        boolean enabled = worldService.settings().bannersEnabled();
        long from = banner.getOwnerId();
        boolean toStash = enabled && (!conqueror.isNpc() || worldService.settings().lordBanners());
        if (toStash) {
            banner.passTo(conqueror.getId());
        } else {
            banner.holdFor(conqueror.getId());
        }
        BannerEvent line =
                log(
                        banner,
                        BannerEventKind.CONQUERED,
                        from,
                        conqueror.getId(),
                        village.getId(),
                        at,
                        null);
        // with banners off the banner isn't taken (§21.9), so no banner achievement either;
        // standards count for none (§21.18)
        if (enabled && !banner.isStandard()) {
            if (toStash) {
                achievements.bannerTaken(conqueror.getId(), banner.getId(), line.getId());
            }
            achievements.grant(from, Achievement.COLOURS_LOST);
        }
        return new ConquestReport.ConqueredBanner(banner.getType(), banner.getTier(), toStash);
    }

    /**
     * A banned or deleted player's banners are gone for good (02-game-rules §21.4.1): the ones in
     * his villages, which turn barbarian, and his stash. Each one leaves a DESTROYED line in the
     * banner log.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int destroyAllOf(long playerId, String reason) {
        Instant now = clock.instant();
        List<Banner> owned = banners.findByOwnerIdOrderByIdAsc(playerId);
        for (Banner banner : owned) {
            log(
                    banner,
                    BannerEventKind.DESTROYED,
                    playerId,
                    null,
                    banner.getEquippedVillageId(),
                    now,
                    reason);
        }
        banners.deleteAll(owned);
        return owned.size();
    }

    // ---- the host (08-host-console): grant, list, revoke ----

    /** All banners of a player, for the host. */
    @Transactional(readOnly = true)
    public List<BannerDto> bannersOf(long playerId) {
        return banners.findByOwnerIdOrderByIdAsc(playerId).stream().map(BannerDto::of).toList();
    }

    /**
     * Gives a player or an NPC lord a new banner in the stash (origin GRANTED).
     *
     * @param reason goes into the banner log
     */
    @Transactional
    public BannerDto grant(long playerId, BannerType type, int tier, String reason) {
        Banner banner = new Banner(playerId, type, tier, BannerOrigin.GRANTED, clock.instant());
        banners.save(banner);
        log(banner, BannerEventKind.GRANTED, null, playerId, null, clock.instant(), reason);
        // a granted banner is owned, not found: Collector and Full Set, but not Standard Bearer
        achievements.checkBanners(playerId);
        return BannerDto.of(banner);
    }

    /**
     * Takes a banner away for good, wherever it is. A banner in a village changes what the village
     * makes, so the village is caught up first.
     *
     * @throws NotFoundException {@code BANNER_NOT_FOUND}
     * @throws GameRuleException {@code BANNER_LOCKED} if the banner moved while this waited
     */
    @Transactional
    public BannerDto revoke(long bannerId, String reason) {
        // Village first, then the owner, like equip and unequip: then the banner can't move
        // between the catch-up and the delete. Its place is read without loading it, so the read
        // after the locks is a fresh one.
        BannerRepository.Place place =
                banners.findPlace(bannerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.BANNER_NOT_FOUND));
        Instant now = clock.instant();
        Long villageId = place.villageId();
        if (villageId != null) {
            locker.lock(villageId).forEach(village -> stateService.catchUp(village, now));
        }
        players.lockById(place.ownerId());
        Banner banner =
                banners.findById(bannerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.BANNER_NOT_FOUND));
        if (banner.getOwnerId() != place.ownerId()
                || !Objects.equals(banner.getEquippedVillageId(), villageId)) {
            // equipped, taken down or stolen while this waited for the locks
            throw new GameRuleException(
                    ErrorCode.BANNER_LOCKED, "The banner moved meanwhile, try again");
        }
        BannerDto removed = BannerDto.of(banner);
        log(banner, BannerEventKind.DESTROYED, banner.getOwnerId(), null, villageId, now, reason);
        banners.delete(banner);
        return removed;
    }

    // ---- helpers ----

    private BannersDto overviewOf(long playerId) {
        List<Banner> mine = banners.findByOwnerIdOrderByIdAsc(playerId);
        Map<Long, Banner> inVillage =
                mine.stream()
                        .filter(b -> b.isEquipped() && !b.isStandard())
                        .collect(Collectors.toMap(Banner::getEquippedVillageId, b -> b));
        Map<Long, Banner> standards =
                mine.stream()
                        .filter(b -> b.isEquipped() && b.isStandard())
                        .collect(Collectors.toMap(Banner::getEquippedVillageId, b -> b));
        Map<Long, VillageSummaryDto> summaries =
                villages.findSummariesByPlayerId(playerId).stream()
                        .collect(Collectors.toMap(VillageSummaryDto::id, s -> s));
        WorldSettings world = worldService.settings();
        boolean enabled = world.bannersEnabled();
        boolean paladins = world.paladinMaxSlots() > 0;
        // the paladin level caps the standards' tier (1.4, §21.18)
        int paladinLevel =
                unitLevels
                        .levelsOf(players.findById(playerId).orElse(null), clock.instant())
                        .get(UnitType.PALADIN);
        List<Village> own = villages.findByPlayerIdOrderByIdAsc(playerId);
        Set<Long> paladinAttacks =
                own.isEmpty()
                        ? Set.of()
                        : Set.copyOf(
                                movements.findVillagesWithPaladinAttacks(
                                        own.stream().map(Village::getId).toList()));
        List<BannersDto.BannerVillageDto> rows =
                own.stream()
                        .map(
                                village -> {
                                    VillageSummaryDto summary = summaries.get(village.getId());
                                    int hall = village.getBuildings().get(BuildingType.BANNER_HALL);
                                    Banner banner = inVillage.get(village.getId());
                                    BannerEffect effect =
                                            banner == null || !enabled
                                                    ? BannerEffect.NONE
                                                    : villageBanners.effectOf(banner, hall);
                                    Banner standard = standards.get(village.getId());
                                    boolean incoming =
                                            summary != null && summary.incomingAttacks() > 0;
                                    return new BannersDto.BannerVillageDto(
                                            village.getId(),
                                            village.getName(),
                                            village.getX(),
                                            village.getY(),
                                            hall,
                                            banner == null ? null : banner.getId(),
                                            effect.effectiveTier(),
                                            incoming,
                                            standard == null ? null : standard.getId(),
                                            standard == null || !enabled || !paladins
                                                    ? 0
                                                    : BannerRules.standardTier(
                                                            standard.getTier(), paladinLevel, hall),
                                            incoming
                                                    || standard != null
                                                            && paladinAttacks.contains(
                                                                    village.getId()));
                                })
                        .toList();
        return new BannersDto(
                enabled,
                gameData.building(BuildingType.BANNER_HALL).maxLevel(),
                catalog.effects(),
                mine.stream().map(BannerDto::of).toList(),
                rows,
                paladins,
                paladinLevel,
                world.standardTheftEnabled());
    }

    private void requireNoIncomingAttack(long villageId) {
        if (movements.countIncomingAttacks(villageId) > 0) {
            throw new GameRuleException(
                    ErrorCode.BANNER_LOCKED,
                    "The banner can't be changed while an attack is on its way");
        }
    }

    /**
     * A paladin standard stays where it is while an attack with a paladin from its village is under
     * way (§21.18): it can't be pulled out of a battle that is going badly.
     */
    private void requireNoPaladinAttack(long villageId) {
        if (movements.countPaladinAttacksUnderWay(villageId) > 0) {
            throw new GameRuleException(
                    ErrorCode.BANNER_LOCKED,
                    "The standard can't be changed while an attack with a paladin is under way");
        }
    }

    private BannerEvent log(
            Banner banner,
            BannerEventKind kind,
            Long from,
            Long to,
            Long villageId,
            Instant at,
            String detail) {
        return events.save(new BannerEvent(banner, kind, from, to, villageId, at, detail));
    }
}
