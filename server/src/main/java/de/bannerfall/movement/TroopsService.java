package de.bannerfall.movement;

import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The troops overview of a village (04-api, the rally point screen of M7b). */
@Service
public class TroopsService {

    /** Stacks in a stable order, so the lists don't jump around. */
    private static final Comparator<UnitStack> BY_ID = Comparator.comparing(UnitStack::getId);

    private final MovementRepository movements;
    private final UnitStackRepository stacks;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final WorldService worldService;
    private final Clock clock;

    TroopsService(
            MovementRepository movements,
            UnitStackRepository stacks,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            WorldService worldService,
            Clock clock) {
        this.movements = movements;
        this.stacks = stacks;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Units at home, supports here, own units elsewhere, outgoing and incoming movements. Locks the
     * village and catches it up, so recruited units are at home.
     */
    @Transactional
    public TroopsDto troops(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        stateService.catchUp(village, clock.instant());
        int cancelWindow = worldService.settings().cancelWindowSeconds();

        List<TroopsDto.SupportHereDto> supportsHere =
                stacks.findByLocationVillageId(villageId).stream()
                        .filter(stack -> !stack.isHome())
                        .sorted(BY_ID)
                        .map(
                                stack ->
                                        new TroopsDto.SupportHereDto(
                                                stack.getId(),
                                                VillageRefDto.of(stack.getHomeVillage()),
                                                stack.getUnits().toMap()))
                        .toList();
        List<TroopsDto.UnitsElsewhereDto> elsewhere =
                stacks.findByHomeVillageId(villageId).stream()
                        .filter(stack -> !stack.isHome())
                        .sorted(BY_ID)
                        .map(
                                stack ->
                                        new TroopsDto.UnitsElsewhereDto(
                                                stack.getId(),
                                                VillageRefDto.of(stack.getLocationVillage()),
                                                stack.getUnits().toMap()))
                        .toList();
        return new TroopsDto(
                unitService.homeUnits(villageId).toMap(),
                supportsHere,
                elsewhere,
                movements.findOutgoing(villageId).stream()
                        .map(movement -> MovementDto.of(movement, cancelWindow))
                        .toList(),
                movements.findByTargetVillageIdOrderByArriveAtAscIdAsc(villageId).stream()
                        .map(IncomingDto::of)
                        .toList());
    }
}
