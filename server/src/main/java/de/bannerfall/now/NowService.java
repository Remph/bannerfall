package de.bannerfall.now;

import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildOrderDto;
import de.bannerfall.building.BuildOrderRepository;
import de.bannerfall.market.TradeTransportRepository;
import de.bannerfall.market.TransportDto;
import de.bannerfall.movement.IncomingDto;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.recruit.DismissOrder;
import de.bannerfall.recruit.DismissOrderDto;
import de.bannerfall.recruit.DismissOrderRepository;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitOrderDto;
import de.bannerfall.recruit.RecruitOrderRepository;
import de.bannerfall.research.ResearchOrder;
import de.bannerfall.research.ResearchOrderDto;
import de.bannerfall.research.ResearchOrderRepository;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Now list (04-api <i>Now</i>): the running things of all villages of a player, read with one
 * query per kind. It takes no village lock and catches nothing up: orders whose time has passed but
 * that the village has not completed yet are left out (they are done), and the units a recruit
 * order has finished so far are computed from its times, the way the catch-up will count them.
 */
@Service
public class NowService {

    private final VillageRepository villages;
    private final BuildOrderRepository buildOrders;
    private final RecruitOrderRepository recruitOrders;
    private final DismissOrderRepository dismissOrders;
    private final ResearchOrderRepository researchOrders;
    private final MovementRepository movements;
    private final TradeTransportRepository transports;
    private final WorldService worldService;
    private final Clock clock;

    NowService(
            VillageRepository villages,
            BuildOrderRepository buildOrders,
            RecruitOrderRepository recruitOrders,
            DismissOrderRepository dismissOrders,
            ResearchOrderRepository researchOrders,
            MovementRepository movements,
            TradeTransportRepository transports,
            WorldService worldService,
            Clock clock) {
        this.villages = villages;
        this.buildOrders = buildOrders;
        this.recruitOrders = recruitOrders;
        this.dismissOrders = dismissOrders;
        this.researchOrders = researchOrders;
        this.movements = movements;
        this.transports = transports;
        this.worldService = worldService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NowDto now(long playerId) {
        // a world that has ended stands still: nothing runs on after its end
        Instant t = worldService.gameTime(clock.instant());
        int cancelWindow = worldService.settings().cancelWindowSeconds();
        List<Village> own = villages.findByPlayerIdOrderByIdAsc(playerId);
        if (own.isEmpty()) {
            return new NowDto(List.of());
        }
        List<Long> ids = own.stream().map(Village::getId).toList();

        Map<Long, List<BuildOrderDto>> builds =
                grouped(
                        buildOrders.findByVillageIdInOrderByEndAtAscIdAsc(ids).stream()
                                .filter(order -> order.getEndAt().isAfter(t))
                                .toList(),
                        BuildOrder::getVillageId,
                        BuildOrderDto::of);
        Map<Long, List<RecruitOrderDto>> training =
                grouped(
                        recruitOrders.findByVillageIdInAndEndAtAfterOrderByEndAtAscIdAsc(ids, t),
                        RecruitOrder::getVillageId,
                        order -> trainingOf(order, t));
        Map<Long, List<DismissOrderDto>> dismissals =
                grouped(
                        dismissOrders.findByVillageIdInAndEndAtAfterOrderByEndAtAscIdAsc(ids, t),
                        DismissOrder::getVillageId,
                        order -> dismissalOf(order, t));
        Map<Long, List<ResearchOrderDto>> research =
                grouped(
                        researchOrders.findByPlayerIdAndEndAtAfterOrderByEndAtAscIdAsc(playerId, t),
                        ResearchOrder::getVillageId,
                        ResearchOrderDto::of);
        Map<Long, List<MovementDto>> outgoing =
                grouped(
                        movements.findOutgoingOf(ids),
                        movement -> movement.getHomeVillage().getId(),
                        movement -> MovementDto.of(movement, cancelWindow));
        Map<Long, List<IncomingDto>> incoming =
                grouped(
                        movements.findIncomingTo(ids),
                        movement -> movement.getTargetVillage().getId(),
                        IncomingDto::of);
        Map<Long, List<TransportDto>> merchants =
                grouped(
                        transports.findOutgoingOf(ids, t),
                        transport -> transport.getSourceVillage().getId(),
                        TransportDto::of);
        Map<Long, List<TransportDto>> incomingMerchants =
                grouped(
                        transports.findIncomingFromOthers(ids, playerId),
                        transport -> transport.getTargetVillage().getId(),
                        TransportDto::of);

        return new NowDto(
                own.stream()
                        .map(
                                village ->
                                        new NowDto.NowVillageDto(
                                                VillageRefDto.of(village),
                                                of(builds, village),
                                                of(training, village),
                                                of(dismissals, village),
                                                of(research, village),
                                                of(outgoing, village),
                                                of(incoming, village),
                                                of(merchants, village),
                                                of(incomingMerchants, village)))
                        .toList());
    }

    private static RecruitOrderDto trainingOf(RecruitOrder order, Instant t) {
        return new RecruitOrderDto(
                order.getId(),
                order.getBuilding(),
                order.getUnit(),
                order.getCount(),
                Math.max(
                        order.getCompletedCount(),
                        RecruitRules.finishedBy(
                                order.getStartAt(),
                                order.getSecondsPerUnit(),
                                order.getCount(),
                                t)),
                order.getSecondsPerUnit(),
                order.getStartAt(),
                order.getEndAt());
    }

    private static DismissOrderDto dismissalOf(DismissOrder order, Instant t) {
        return new DismissOrderDto(
                order.getId(),
                order.getBuilding(),
                order.getUnit(),
                order.getCount(),
                Math.max(
                        order.getCompletedCount(),
                        RecruitRules.finishedBy(
                                order.getStartAt(),
                                order.getSecondsPerUnit(),
                                order.getCount(),
                                t)),
                order.getSecondsPerUnit(),
                order.getStartAt(),
                order.getEndAt());
    }

    private static <E, D> Map<Long, List<D>> grouped(
            List<E> entities, Function<E, Long> villageOf, Function<E, D> dto) {
        return entities.stream()
                .collect(
                        Collectors.groupingBy(
                                villageOf, Collectors.mapping(dto, Collectors.toList())));
    }

    private static <D> List<D> of(Map<Long, List<D>> byVillage, Village village) {
        return byVillage.getOrDefault(village.getId(), List.of());
    }
}
