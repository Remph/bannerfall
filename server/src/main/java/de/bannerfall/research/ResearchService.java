package de.bannerfall.research;

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
 * Researching unit levels at the smithy (M30, 02-game-rules §5.4): starting and cancelling a
 * research, and the smithy overview. The levels belong to the player; a research runs in a village
 * (that village's smithy does one at a time) and a unit is researched in one village at a time.
 * There are no events: a research is done when its {@code endAt} has passed ({@link
 * UnitLevelService} reads the levels at the time it needs them).
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
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;

    ResearchService(
            ResearchOrderRepository orders,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            GameData gameData,
            WorldService worldService,
            Clock clock) {
        this.orders = orders;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Starts the research of the unit's next level in this village. Pays the cost now; the time
     * uses the smithy level of right now. The checks run in this order, the first one that fails is
     * the answer.
     *
     * @throws GameRuleException {@code UNIT_LEVELS_DISABLED}, {@code MAX_LEVEL_REACHED} (also for a
     *     unit without levels), {@code RESEARCH_BUSY} (this village's smithy or this unit is busy),
     *     {@code REQUIREMENTS_NOT_MET} (current levels of this village), {@code
     *     NOT_ENOUGH_RESOURCES}
     */
    @Transactional
    public ResearchOrderDto start(long villageId, long playerId, UnitType type) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        if (!world.unitLevelsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.UNIT_LEVELS_DISABLED, "Unit levels are switched off in this world");
        }

        UnitDef def = gameData.unit(type);
        List<ResearchOrder> mine = orders.findByPlayerId(playerId);
        int next = levelOf(mine, type, now) + 1;
        if (next > def.maxLevel()) {
            throw new GameRuleException(
                    ErrorCode.MAX_LEVEL_REACHED, type + " has no level " + next + " to research");
        }
        for (ResearchOrder order : mine) {
            if (!order.isDoneBy(now)
                    && (order.getUnit() == type || order.getVillageId() == villageId)) {
                throw new GameRuleException(
                        ErrorCode.RESEARCH_BUSY,
                        order.getUnit() == type
                                ? type + " is being researched already"
                                : "The smithy of this village is busy");
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
                ResearchRules.seconds(
                        step, Math.max(1, levels.get(BuildingType.SMITHY)), world.speed());
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
     * that has him, the banner thief only with banners on.
     */
    @Transactional
    public SmithyDto smithy(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        // a frozen world (v1.3, §22) shows its researches as they were at the end
        Instant now = worldService.gameTime(clock.instant());
        stateService.catchUp(village, now);
        WorldSettings world = worldService.settings();
        boolean enabled = world.unitLevelsEnabled();
        List<ResearchOrder> mine = enabled ? orders.findByPlayerId(playerId) : List.of();
        Map<BuildingType, Integer> buildings = village.getBuildings().toMap();
        ResourceAmounts stock = village.floorResources();
        ResearchOrder runningHere =
                mine.stream()
                        .filter(o -> !o.isDoneBy(now) && o.getVillageId() == villageId)
                        .findFirst()
                        .orElse(null);

        List<SmithyDto.SmithyUnitDto> units = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            // a world without paladins (02-game-rules §5.5) does not list him, as the barracks
            if (type == UnitType.PALADIN && world.paladinMaxSlots() < 1) {
                continue;
            }
            // the banner thief belongs to the banners (M32b): not listed in a world without them
            if (type == UnitType.BANNER_THIEF && !world.bannersEnabled()) {
                continue;
            }
            UnitDef def = gameData.unit(type);
            int maxLevel = enabled ? def.maxLevel() : 1;
            int level = enabled ? Math.min(levelOf(mine, type, now), maxLevel) : 1;
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
            if (researching == null && level < maxLevel) {
                next = nextResearch(def, level + 1, buildings, stock, runningHere != null, world);
            }
            units.add(
                    new SmithyDto.SmithyUnitDto(
                            type,
                            def.attackType(),
                            def.minutesPerField() / (world.speed() * world.unitSpeed()),
                            def.carry(),
                            level,
                            maxLevel,
                            stats,
                            researching == null ? null : ResearchOrderDto.of(researching),
                            next));
        }
        return new SmithyDto(
                buildings.get(BuildingType.SMITHY),
                enabled,
                units,
                runningHere == null ? null : ResearchOrderDto.of(runningHere));
    }

    private static SmithyDto.NextResearchDto nextResearch(
            UnitDef def,
            int level,
            Map<BuildingType, Integer> buildings,
            ResourceAmounts stock,
            boolean villageBusy,
            WorldSettings world) {
        UnitLevelDef step = def.levelDef(level);
        Map<BuildingType, Integer> requires = ResearchRules.requirements(def, level);
        List<Requirement> all = new ArrayList<>();
        requires.forEach((building, needed) -> all.add(new Requirement(building, needed)));
        return new SmithyDto.NextResearchDto(
                level,
                step.cost(),
                ResearchRules.seconds(
                        step, Math.max(1, buildings.get(BuildingType.SMITHY)), world.speed()),
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
