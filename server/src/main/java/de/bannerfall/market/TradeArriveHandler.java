package de.bannerfall.market;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventHandler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.report.TradeReport;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageStateService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Merchants arrive (02-game-rules §12): the target is locked and brought up to the arrival, the
 * resources are received like loot (capped at the warehouse, §3), the transport is marked arrived
 * (its merchants walk home until {@code returnAt}). Whoever owns the target now gets a
 * TRADE_ARRIVED report and VILLAGE_UPDATED; a barbarian village gets the resources and nobody hears
 * of it.
 */
@Component
class TradeArriveHandler implements GameEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TradeArriveHandler.class);

    private final TradeTransportRepository transports;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final ReportService reportService;
    private final ApplicationEventPublisher events;

    TradeArriveHandler(
            TradeTransportRepository transports,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            ReportService reportService,
            ApplicationEventPublisher events) {
        this.transports = transports;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.reportService = reportService;
        this.events = events;
    }

    @Override
    public GameEventKind kind() {
        return GameEventKind.TRADE_ARRIVE;
    }

    @Override
    public void handle(GameEvent event) {
        long transportId = event.getRefId();
        Long targetId = transports.findTargetVillageIdById(transportId).orElse(null);
        if (targetId == null) {
            log.warn("Transport {} is gone, nothing arrives", transportId);
            return;
        }
        locker.lock(targetId);
        TradeTransport transport = transports.findById(transportId).orElse(null);
        if (transport == null || transport.isArrived()) {
            log.warn("Transport {} is gone or has arrived already", transportId);
            return;
        }
        Instant at = event.getDueAt();
        Village target = transport.getTargetVillage();
        stateService.catchUp(target, at);
        ResourceAmounts lost =
                target.receiveResources(
                        transport.getResources(), villageBanners.warehouseCapacity(target));
        transport.markArrived();

        Player owner = target.getPlayer();
        if (owner != null) {
            reportService.create(
                    ReportKind.TRADE_ARRIVED,
                    at,
                    new TradeReport(
                            VillageRefDto.of(transport.getSourceVillage()),
                            VillageRefDto.of(target),
                            transport.getResources(),
                            lost),
                    owner.getId());
            events.publishEvent(
                    new PlayerNotification(
                            owner.getId(), new GameNotification.VillageUpdated(target.getId())));
        }
    }
}
