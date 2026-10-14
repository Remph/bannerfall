package de.bannerfall.barbarian;

import de.bannerfall.common.Coordinates;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.player.Player;
import de.bannerfall.rules.Barbarians;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.MapPlacement;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.OwnerChange;
import de.bannerfall.village.PointsService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passive barbarian villages (02-game-rules §11): spawning them (at world start, around new
 * players, by the dev cheat), growing them ({@link BarbarianGrowthHandler}) and turning a deleted
 * player's villages into barbarian villages (admin, M14a).
 */
@Service
public class BarbarianService {

    private static final Logger log = LoggerFactory.getLogger(BarbarianService.class);

    private final VillageRepository villages;
    private final UnitStackRepository stacks;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final PointsService pointsService;
    private final OwnerChange ownerChange;
    private final WorldService worldService;
    private final GameData gameData;
    private final RandomSource random;
    private final Clock clock;

    BarbarianService(
            VillageRepository villages,
            UnitStackRepository stacks,
            VillageLocker locker,
            VillageStateService stateService,
            PointsService pointsService,
            OwnerChange ownerChange,
            WorldService worldService,
            GameData gameData,
            RandomSource random,
            Clock clock) {
        this.villages = villages;
        this.stacks = stacks;
        this.locker = locker;
        this.stateService = stateService;
        this.pointsService = pointsService;
        this.ownerChange = ownerChange;
        this.worldService = worldService;
        this.gameData = gameData;
        this.random = random;
        this.clock = clock;
    }

    /**
     * The initial barbarian villages around the map center (02-game-rules §2, {@code r = random(4,
     * 30)}). Stops early if the map has no more room; the world starts anyway.
     *
     * @return the new villages
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Village> spawnInitial(int count) {
        int mapSize = worldService.settings().mapSize();
        return spawn(
                count,
                occupied ->
                        MapPlacement.findInitialBarbarianSpot(mapSize, occupied::contains, random));
    }

    /**
     * Barbarian villages near a point, e.g. a new player's village ({@code r = random(3, 8)}).
     * Fewer if the area has no more room: a registration never fails because of them.
     *
     * @return the new villages
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Village> spawnAround(Coordinates center, int count) {
        int mapSize = worldService.settings().mapSize();
        return spawn(
                count,
                occupied ->
                        MapPlacement.findBarbarianSpotNear(
                                center, mapSize, occupied::contains, random));
    }

    /**
     * One barbarian village on exactly this field (dev cheat). Only the field itself must be free
     * and on the map; the spacing rule is not checked.
     *
     * @throws GameRuleException {@code FIELD_NOT_FREE} if the field is off the map or taken
     */
    @Transactional
    public Village spawnAt(Coordinates spot) {
        worldService.lockMapForPlacement();
        int mapSize = worldService.settings().mapSize();
        boolean onMap = spot.x() >= 0 && spot.x() < mapSize && spot.y() >= 0 && spot.y() < mapSize;
        if (!onMap || villages.findByXAndY(spot.x(), spot.y()).isPresent()) {
            throw new GameRuleException(
                    ErrorCode.FIELD_NOT_FREE, "No room for a village on " + spot);
        }
        return create(spot);
    }

    private List<Village> spawn(
            int count, Function<Set<Coordinates>, Optional<Coordinates>> finder) {
        if (count <= 0) {
            return List.of();
        }
        worldService.lockMapForPlacement();
        Set<Coordinates> occupied = new HashSet<>(villages.findAllCoordinates());
        List<Village> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<Coordinates> spot = finder.apply(occupied);
            if (spot.isEmpty()) {
                log.warn("No free spot for a barbarian village, {} of {} spawned", i, count);
                break;
            }
            occupied.add(spot.get());
            created.add(create(spot.get()));
        }
        return created;
    }

    /**
     * 02-game-rules §11: random mines and wall (no wall with "barbarians build walls", M29),
     * half-full warehouse, no units, full loyalty.
     */
    private Village create(Coordinates spot) {
        Map<BuildingType, Integer> start =
                Barbarians.startLevels(random, worldService.settings().barbarianWallsEnabled());
        var levels = new BuildingLevels();
        start.forEach(levels::set);
        Village village =
                villages.save(
                        new Village(
                                null,
                                null,
                                spot,
                                Barbarians.startResources(start.get(BuildingType.WAREHOUSE)),
                                levels,
                                BuildRules.villagePoints(gameData, start),
                                clock.instant()));
        stacks.save(UnitStack.homeOf(village));
        return village;
    }

    /**
     * One growth round at {@code at} (the event's due time): every barbarian village gets one level
     * on a random mine that is still below 12 (when growth is on) and one level of wall (when walls
     * may be built at {@code at}, {@link Barbarians#wallsBuildAt}), after catching up its
     * production with the old levels. Does nothing when neither applies.
     *
     * @return how many villages grew
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int grow(Instant at) {
        WorldSettings world = worldService.settings();
        boolean minesGrow = world.barbarianGrowthEnabled();
        boolean wallsGrow = Barbarians.wallsBuildAt(world, worldService.initializedAt(), at);
        if (!minesGrow && !wallsGrow) {
            return 0;
        }
        List<Long> ids = villages.findBarbarianIds();
        int grown = 0;
        for (Village village : locker.lock(ids.toArray(Long[]::new))) {
            if (village.getPlayer() != null) {
                continue; // conquered between reading the ids and the lock
            }
            Optional<BuildingType> mine =
                    minesGrow
                            ? Barbarians.mineToGrow(village.getBuildings().toMap(), random)
                            : Optional.empty();
            boolean wall =
                    wallsGrow
                            && Barbarians.wallGrows(
                                    world,
                                    village.getBuildings().get(BuildingType.WALL),
                                    village.isWallRazed());
            if (mine.isEmpty() && !wall) {
                continue;
            }
            stateService.catchUp(village, at);
            mine.ifPresent(type -> village.getBuildings().add(type, 1));
            if (wall) {
                village.getBuildings().add(BuildingType.WALL, 1);
            }
            pointsService.recalculate(village);
            grown++;
        }
        return grown;
    }

    /**
     * A deleted player's villages become barbarian villages (02-game-rules §11): their units are
     * gone (at home, supporting elsewhere, on the way), their build and recruit orders are deleted
     * with their events, owner and name are cleared. Supports of other players standing in them
     * stay; their owners can withdraw them. The player keeps his row (reports point at it) with no
     * villages and 0 points. Their market offers are deleted with the orders ({@code OwnerChange}).
     *
     * @return the converted villages
     */
    @Transactional
    public List<Village> convertVillagesOf(long playerId) {
        List<Long> own = villages.findIdsByPlayerId(playerId);
        if (own.isEmpty()) {
            return List.of();
        }
        // Their supports elsewhere are deleted, so the villages they stand in are locked too.
        Set<Long> toLock = new TreeSet<>(own);
        toLock.addAll(stacks.findSupportLocationIds(own));
        locker.lock(toLock.toArray(Long[]::new));
        // A support may have arrived somewhere new before we had the lock: lock that place too
        // (rare, so the id order across the two calls doesn't matter much).
        List<Long> late =
                stacks.findSupportLocationIds(own).stream()
                        .filter(id -> !toLock.contains(id))
                        .toList();
        if (!late.isEmpty()) {
            locker.lock(late.toArray(Long[]::new));
        }

        Instant now = clock.instant();
        List<Village> converted = new ArrayList<>();
        Player player = null;
        for (long villageId : own) {
            Village village = villages.findById(villageId).orElseThrow();
            if (village.getPlayer() == null || village.getPlayer().getId() != playerId) {
                continue; // lost between reading the ids and the lock
            }
            player = village.getPlayer();
            stateService.catchUp(village, now);
            ownerChange.dropOrders(villageId, now);
            ownerChange.dropUnits(village, playerId);
            village.becomeBarbarian();
            player.loseVillage();
            converted.add(village);
        }
        if (player != null) {
            player.updatePoints(Math.toIntExact(villages.sumPointsOfPlayer(playerId)));
        }
        return converted;
    }
}
