package de.bannerfall.research;

import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitLevelDef;
import de.bannerfall.gamedata.UnitStats;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Requirement;
import de.bannerfall.rules.ResearchRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Researching unit levels at the smithy (M30, 02-game-rules §5.4) and training the paladin's and
 * the banner thief's levels in the Banner Hall (Hall training, 1.4): starting and cancelling a
 * research, the smithy overview and the hall's. The levels belong to the player; a research runs in
 * a village (that village's smithy, and its Banner Hall, each do one at a time) and a unit is
 * researched in one village at a time. There are no events: a research is done when its {@code
 * endAt} has passed ({@link UnitLevelService} reads the levels at the time it needs them).
 *
 * <p>Every method locks the village first and catches it up to now, like every request that reads
 * or changes a village.
 */
@Service
public class ResearchService {

    /** Postgres' name for {@code unique (player_id, unit, level)} of V25. */
    private static final String UNIQUE_STEP = "research_order_player_id_unit_level_key";

    private final ResearchOrderRepository orders;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final BannerCatalog banners;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;

    ResearchService(
            ResearchOrderRepository orders,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            BannerCatalog banners,
            GameData gameData,
            WorldService worldService,
            Clock clock) {
        this.orders = orders;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.banners = banners;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Starts the research of the unit's next level in this village: in the Smithy, or in the Banner
     * Hall for the paladin and the banner thief ({@link ResearchRules#building}). Pays the cost
     * now; the time uses that building's level of right now. The checks run in this order, the
     * first one that fails is the answer.
     *
     * @throws GameRuleException {@code UNIT_LEVELS_DISABLED} (a Smithy unit with unit levels off),
     *     {@code PALADIN_DISABLED} / {@code BANNERS_DISABLED} (the paladin or the banner thief in a
     *     world without him), {@code MAX_LEVEL_REACHED} (also for a unit without levels), {@code
     *     RESEARCH_BUSY} (this village's smithy or hall, or this unit, is busy), {@code
     *     REQUIREMENTS_NOT_MET} (current levels of this village), {@code NOT_ENOUGH_RESOURCES}
     */
    @Transactional
    public ResearchOrderDto start(long villageId, long playerId, UnitType type) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        UnitDef def = gameData.unit(type);
        BuildingType building = ResearchRules.building(def);
        requireAvailable(type, building, world);

        List<ResearchOrder> mine = orders.findByPlayerId(playerId);
        int next = levelOf(mine, type, now) + 1;
        if (next > def.maxLevel()) {
            throw new GameRuleException(
                    ErrorCode.MAX_LEVEL_REACHED, type + " has no level " + next + " to research");
        }
        for (ResearchOrder order : mine) {
            if (!order.isDoneBy(now)
                    && (order.getUnit() == type || runsIn(order, villageId, building))) {
                throw new GameRuleException(
                        ErrorCode.RESEARCH_BUSY,
                        order.getUnit() == type
                                ? type + " is being researched already"
                                : "The " + building + " of this village is busy");
            }
        }
        Map<BuildingType, Integer> levels = village.getBuildings().toMap();
        if (!BuildRules.missingRequirements(ResearchRules.requirements(def, next), levels)
                .isEmpty()) {
            throw new GameRuleException(
                    ErrorCode.REQUIREMENTS_NOT_MET,
                    "Requirements for " + type + " level " + next + " are not met");
        }
        UnitLevelDef step = def.levelDef(next);
        if (!step.cost().fitsInto(village.floorResources())) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_RESOURCES,
                    "Not enough resources to research " + type + " level " + next);
        }

        long seconds =
                ResearchRules.seconds(step, Math.max(1, levels.get(building)), world.speed());
        village.spend(step.cost());
        try {
            // flushed here so a second village researching the same unit at the same moment (the
            // villages are locked one by one) runs into the unique key and not into a duplicate
            return ResearchOrderDto.of(
                    orders.saveAndFlush(
                            ResearchOrder.start(
                                    playerId, villageId, type, next, seconds, step.cost(), now)));
        } catch (DataIntegrityViolationException e) {
            // only the unique key (player, unit, level) means "researched already"; anything
            // else is a real error and stays one
            if (!Objects.toString(e.getMostSpecificCause().getMessage(), "")
                    .contains(UNIQUE_STEP)) {
                throw e;
            }
            throw new GameRuleException(
                    ErrorCode.RESEARCH_BUSY, type + " is being researched already");
        }
    }

    /**
     * Cancels a running research and gives the whole cost back, received like incoming resources:
     * up to the warehouse capacity, the rest is lost (as for build and recruit orders).
     *
     * @throws NotFoundException {@code RESEARCH_ORDER_NOT_FOUND} if the village has no such running
     *     research (never had, done already, or cancelled)
     */
    @Transactional
    public void cancel(long villageId, long orderId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        ResearchOrder order =
                orders.findByVillageIdAndEndAtAfter(villageId, now).stream()
                        .filter(o -> o.getId() == orderId && o.getPlayerId() == playerId)
                        .findFirst()
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.RESEARCH_ORDER_NOT_FOUND));
        village.receiveResources(order.refund(), villageBanners.warehouseCapacity(village));
        orders.delete(order);
    }

    /**
     * The smithy page (04-api): every unit with its stats, its level and what the next research
     * would need and cost. With unit levels off only the base stats; the paladin only in a world
     * that has him, the banner thief only with banners on. Those two are listed with the levels the
     * Banner Hall trains (also with unit levels off) and no next research here.
     */
    @Transactional
    public SmithyDto smithy(long villageId, long playerId) {
        Overview overview = overview(villageId, playerId, BuildingType.SMITHY);
        return new SmithyDto(
                overview.level(),
                overview.world().unitLevelsEnabled(),
                overview.units(),
                overview.running());
    }

    /**
     * The Banner Hall's training (Hall training 1.4, 04-api): the paladin (in a world that has him)
     * and the banner thief (with banners on) with their levels and the next training, in every
     * world that has the hall.
     *
     * @throws GameRuleException {@code BANNERS_DISABLED} in a world with neither banners nor the
     *     paladin (it has no Banner Hall)
     */
    @Transactional
    public HallTrainingDto hallTraining(long villageId, long playerId) {
        if (!worldService.settings().bannerHallAvailable()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "This world has neither banners nor the paladin");
        }
        Overview overview = overview(villageId, playerId, BuildingType.BANNER_HALL);
        return new HallTrainingDto(
                overview.level(),
                overview.units(),
                overview.running(),
                overview.world().bannersEnabled() ? banners.theft().chancePerThief() : null);
    }

    /** What the smithy page and the hall's training show. */
    private record Overview(
            WorldSettings world,
            int level,
            List<SmithyDto.SmithyUnitDto> units,
            ResearchOrderDto running) {}

    /**
     * The units a building's page lists: the Smithy lists every unit of the world, the Banner Hall
     * only its own two. The next research is offered only where the unit is researched.
     */
    private Overview overview(long villageId, long playerId, BuildingType page) {
        Village village = locker.lockOwned(villageId, playerId);
        // a frozen world (v1.3, §22) shows its researches as they were at the end
        Instant now = worldService.gameTime(clock.instant());
        stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        List<ResearchOrder> mine = orders.findByPlayerId(playerId);
        Map<BuildingType, Integer> buildings = village.getBuildings().toMap();
        ResourceAmounts stock = village.floorResources();
        ResearchOrder runningHere =
                mine.stream()
                        .filter(o -> !o.isDoneBy(now) && runsIn(o, villageId, page))
                        .findFirst()
                        .orElse(null);

        List<SmithyDto.SmithyUnitDto> units = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            // a world without paladins (02-game-rules §5.5) does not list him
            if (type == UnitType.PALADIN && world.paladinMaxSlots() < 1) {
                continue;
            }
            // the banner thief belongs to the banners (M32b): not listed in a world without them
            if (type == UnitType.BANNER_THIEF && !world.bannersEnabled()) {
                continue;
            }
            UnitDef def = gameData.unit(type);
            BuildingType building = ResearchRules.building(def);
            if (page == BuildingType.BANNER_HALL && building != page) {
                continue;
            }
            // the hall's levels count in every world, the smithy's only with unit levels on
            boolean leveled = building == BuildingType.BANNER_HALL || world.unitLevelsEnabled();
            int maxLevel = leveled ? def.maxLevel() : 1;
            int level = leveled ? Math.min(levelOf(mine, type, now), maxLevel) : 1;
            List<UnitStats> stats = new ArrayList<>();
            for (int l = 1; l <= maxLevel; l++) {
                stats.add(def.stats(l));
            }
            ResearchOrder researching =
                    mine.stream()
                            .filter(o -> !o.isDoneBy(now) && o.getUnit() == type)
                            .findFirst()
                            .orElse(null);
            SmithyDto.NextResearchDto next = null;
            if (building == page && researching == null && level < maxLevel) {
                next =
                        nextResearch(
                                def,
                                building,
                                level + 1,
                                buildings,
                                stock,
                                runningHere != null,
                                world);
            }
            units.add(
                    new SmithyDto.SmithyUnitDto(
                            type,
                            building,
                            def.attackType(),
                            def.minutesPerField() / (world.speed() * world.unitSpeed()),
                            def.carry(),
                            level,
                            maxLevel,
                            stats,
                            researching == null ? null : ResearchOrderDto.of(researching),
                            next));
        }
        return new Overview(
                world,
                buildings.get(page),
                units,
                runningHere == null ? null : ResearchOrderDto.of(runningHere));
    }

    /** True if a research runs in that village's building (its smithy or its hall). */
    private boolean runsIn(ResearchOrder order, long villageId, BuildingType building) {
        return order.getVillageId() == villageId
                && ResearchRules.building(gameData.unit(order.getUnit())) == building;
    }

    /**
     * A Smithy unit needs unit levels switched on; the Banner Hall's units only need to exist in
     * the world (Hall training is part of the paladin and the banners, 1.4).
     */
    private static void requireAvailable(
            UnitType type, BuildingType building, WorldSettings world) {
        if (building == BuildingType.SMITHY && !world.unitLevelsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.UNIT_LEVELS_DISABLED, "Unit levels are switched off in this world");
        }
        if (type == UnitType.PALADIN && world.paladinMaxSlots() < 1) {
            throw new GameRuleException(
                    ErrorCode.PALADIN_DISABLED, "There are no paladins in this world");
        }
        if (type == UnitType.BANNER_THIEF && !world.bannersEnabled()) {
            throw new GameRuleException(
                    ErrorCode.BANNERS_DISABLED, "Banners are switched off in this world");
        }
    }

    private static SmithyDto.NextResearchDto nextResearch(
            UnitDef def,
            BuildingType building,
            int level,
            Map<BuildingType, Integer> buildings,
            ResourceAmounts stock,
            boolean villageBusy,
            WorldSettings world) {
        UnitLevelDef step = def.levelDef(level);
        Map<BuildingType, Integer> requires = ResearchRules.requirements(def, level);
        List<Requirement> all = new ArrayList<>();
        requires.forEach((type, needed) -> all.add(new Requirement(type, needed)));
        return new SmithyDto.NextResearchDto(
                level,
                step.cost(),
                ResearchRules.seconds(step, Math.max(1, buildings.get(building)), world.speed()),
                all,
                BuildRules.missingRequirements(requires, buildings),
                step.cost().fitsInto(stock),
                villageBusy);
    }

    /**
     * The level of a unit from a player's researches: the highest one that is done by {@code t}.
     */
    private static int levelOf(List<ResearchOrder> researches, UnitType type, Instant t) {
        int level = 1;
        for (ResearchOrder order : researches) {
            if (order.getUnit() == type && order.isDoneBy(t)) {
                level = Math.max(level, order.getLevel());
            }
        }
        return level;
    }
}
