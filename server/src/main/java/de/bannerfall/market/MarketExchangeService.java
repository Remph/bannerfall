package de.bannerfall.market;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.rules.MarketExchange;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trading with the market itself (02-game-rules §12.1): {@code marketExchangeRatio} of one resource
 * for 1 of another, at once and without merchants, up to {@link MarketExchange#dailyCap} of each
 * resource per village and day. The day ends at midnight in {@code bannerfall.market.day-zone}. The
 * village is locked, so two trades at the same time can't both use the rest of the day's cap.
 */
@Service
public class MarketExchangeService {

    private final MarketExchangeRepository exchanges;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final WorldService worldService;
    private final Clock clock;
    private final ZoneId dayZone;

    MarketExchangeService(
            MarketExchangeRepository exchanges,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            WorldService worldService,
            Clock clock,
            @Value("${bannerfall.market.day-zone:Europe/Berlin}") String dayZone) {
        this.exchanges = exchanges;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.worldService = worldService;
        this.clock = clock;
        this.dayZone = ZoneId.of(dayZone);
    }

    /**
     * Trades {@link ExchangeRequest#amount()} of {@code receive} for {@code ceil(amount × ratio)}
     * of {@code give}.
     *
     * @return the village's exchange state after the trade
     * @throws GameRuleException {@code EXCHANGE_OFF}, {@code SAME_RESOURCE}, {@code
     *     EXCHANGE_LIMIT}, {@code NOT_ENOUGH_RESOURCES}, {@code WAREHOUSE_FULL} (in this order)
     */
    @Transactional
    public ExchangeDto exchange(long villageId, long playerId, ExchangeRequest request) {
        Village village = locker.lockOwned(villageId, playerId);
        WorldSettings world = worldService.settings();
        if (world.marketExchangeRatio() == 0) {
            throw new GameRuleException(
                    ErrorCode.EXCHANGE_OFF, "Trading with the market is off in this world");
        }
        if (request.give() == request.receive()) {
            throw new GameRuleException(
                    ErrorCode.SAME_RESOURCE, "The market trades one resource for another");
        }
        Instant now = clock.instant();
        Instant dayStart = MarketExchange.dayStart(now, dayZone);
        exchanges.deleteOlderThan(villageId, dayStart);
        int amount = request.amount();
        int left = dailyCap(village, world) - received(villageId, dayStart).get(request.receive());
        if (amount > left) {
            throw new GameRuleException(
                    ErrorCode.EXCHANGE_LIMIT,
                    "The market gives " + Math.max(0, left) + " more of it today");
        }
        stateService.catchUp(village, now);
        int cost = MarketExchange.cost(amount, world.marketExchangeRatio());
        ResourceAmounts payment = only(request.give(), cost);
        if (!payment.fitsInto(village.floorResources())) {
            throw new GameRuleException(ErrorCode.NOT_ENOUGH_RESOURCES, "Not enough resources");
        }
        int capacity = villageBanners.warehouseCapacity(village);
        if (village.floorResources().get(request.receive()) + amount > capacity) {
            throw new GameRuleException(
                    ErrorCode.WAREHOUSE_FULL, "The warehouse can't take that much of it");
        }
        village.spend(payment);
        village.receiveResources(only(request.receive(), amount), capacity);
        exchanges.save(
                new MarketExchangeEntry(
                        villageId, request.give(), cost, request.receive(), amount, now));
        return status(village, now);
    }

    /** The village's exchange state now: ratio, cap, what it got today, the next reset. */
    ExchangeDto status(Village village, Instant now) {
        WorldSettings world = worldService.settings();
        Map<ResourceType, Integer> received =
                received(village.getId(), MarketExchange.dayStart(now, dayZone));
        return new ExchangeDto(
                world.marketExchangeRatio(),
                dailyCap(village, world),
                new ResourceAmounts(
                        received.get(ResourceType.WOOD),
                        received.get(ResourceType.CLAY),
                        received.get(ResourceType.IRON)),
                MarketExchange.nextReset(now, dayZone));
    }

    /**
     * Per resource, what the market still gives the village today (all 0 while trading is off). For
     * the NPC lords (v1.3), which check a trade before they make it.
     */
    public Map<ResourceType, Integer> leftToday(Village village) {
        WorldSettings world = worldService.settings();
        int cap = world.marketExchangeRatio() == 0 ? 0 : dailyCap(village, world);
        Map<ResourceType, Integer> received =
                received(village.getId(), MarketExchange.dayStart(clock.instant(), dayZone));
        Map<ResourceType, Integer> left = new EnumMap<>(ResourceType.class);
        received.forEach((type, amount) -> left.put(type, Math.max(0, cap - amount)));
        return left;
    }

    private static int dailyCap(Village village, WorldSettings world) {
        return MarketExchange.dailyCap(
                village.getBuildings().get(BuildingType.MARKET), world.speed());
    }

    /** Per resource, what the village got from the market since the day began (0 if nothing). */
    private Map<ResourceType, Integer> received(long villageId, Instant dayStart) {
        Map<ResourceType, Integer> received = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            received.put(type, 0);
        }
        for (var row : exchanges.sumReceivedSince(villageId, dayStart)) {
            received.put(row.getResource(), Math.toIntExact(row.getAmount()));
        }
        return received;
    }

    private static ResourceAmounts only(ResourceType type, int amount) {
        return switch (type) {
            case WOOD -> new ResourceAmounts(amount, 0, 0);
            case CLAY -> new ResourceAmounts(0, amount, 0);
            case IRON -> new ResourceAmounts(0, 0, amount);
        };
    }
}
