package de.bannerfall.village;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildOrderDto;
import de.bannerfall.building.BuildService;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.unit.UnitService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading and renaming a player's own villages. */
@Service
public class VillageService {

    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final BuildService buildService;
    private final MovementRepository movements;
    private final VillageBanners villageBanners;
    private final VillageTagService villageTags;
    private final WorldService worldService;
    private final Clock clock;

    VillageService(
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            BuildService buildService,
            MovementRepository movements,
            VillageBanners villageBanners,
            VillageTagService villageTags,
            WorldService worldService,
            Clock clock) {
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.buildService = buildService;
        this.movements = movements;
        this.villageBanners = villageBanners;
        this.villageTags = villageTags;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * The village with its state brought up to now. Locks it, because catching up writes (and from
     * M5 on moves finished units).
     */
    @Transactional
    public VillageDto get(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        List<RecruitOrder> recruiting = stateService.catchUp(village, clock.instant());
        return toDto(village, recruiting);
    }

    @Transactional
    public VillageDto rename(long villageId, long playerId, String name) {
        Village village = locker.lockOwned(villageId, playerId);
        List<RecruitOrder> recruiting = stateService.catchUp(village, clock.instant());
        // trim() like @NotBlank, so the result is never empty
        village.rename(name.trim());
        return toDto(village, recruiting);
    }

    @Transactional(readOnly = true)
    public List<VillageSummaryDto> summariesOf(long playerId) {
        Map<Long, String> tags = villageTags.tagsOf(playerId);
        return villages.findSummariesByPlayerId(playerId).stream()
                .map(summary -> summary.withTag(tags.get(summary.id())))
                .toList();
    }

    /** {@code recruiting}: the open recruit orders the catch-up returned. */
    private VillageDto toDto(Village village, List<RecruitOrder> recruiting) {
        WorldSettings world = worldService.settings();
        BuildingLevels buildings = village.getBuildings();
        long villageId = village.getId();
        List<BuildOrder> queue = buildService.queueOf(villageId);
        // All the village's units: home and support stacks, on the way, in recruit orders.
        UnitService.VillageUnits units = unitService.unitsOf(villageId, recruiting);
        BannerEffect banner = villageBanners.effectOf(village);
        return new VillageDto(
                villageId,
                village.getName(),
                village.getX(),
                village.getY(),
                village.getPoints(),
                (int) Math.floor(village.getLoyalty()),
                new VillageDto.VillageResourcesDto(
                        village.getWood(),
                        village.getClay(),
                        village.getIron(),
                        village.getResourcesUpdatedAt()),
                new VillageDto.ResourceRatesDto(
                        VillageStateService.productionPerHour(
                                village, ResourceType.WOOD, world, banner),
                        VillageStateService.productionPerHour(
                                village, ResourceType.CLAY, world, banner),
                        VillageStateService.productionPerHour(
                                village, ResourceType.IRON, world, banner)),
                villageBanners.warehouseCapacity(village),
                villageBanners.hidingCapacity(village),
                new VillageDto.PopulationDto(
                        buildService.populationUsed(village, queue, units.all()),
                        BuildService.farmCapacity(village, queue)),
                buildings.toMap(),
                queue.stream().map(BuildOrderDto::of).toList(),
                units.home().toMap(),
                Math.toIntExact(movements.countIncomingAttacks(villageId)),
                villageBanners.flying(village));
    }
}
