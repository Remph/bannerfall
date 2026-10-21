package de.bannerfall.npc;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.market.MarketService;
import de.bannerfall.market.TradeTransport;
import de.bannerfall.market.TradeTransportRepository;
import de.bannerfall.market.TransportRequest;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.Merchants;
import de.bannerfall.rules.Production;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lord shipments (09-npc-lords §5.5, 1.6): after building and recruiting, a village whose stock
 * would overflow sends it by merchant to the lord's own villages that still build, through {@link
 * MarketService#send} like a human on the market page. Only a resource at {@link #OVERFLOW} of the
 * warehouse or more is shipped, down to {@link #KEEP} and never below the village's own next build
 * step; receivers are filled up to {@link #FILL}, counting what is on the way and what they produce
 * until the merchants arrive. The pure parts ({@link #spare}, {@link #room}, {@link #load}) are
 * unit-tested ({@code NpcShipmentsTest}).
 */
@Component
class NpcShipments {

    /** A resource at this share of the warehouse or more would overflow: it is shipped. */
    static final double OVERFLOW = 0.9;

    /** The donor keeps this share of its warehouse of a shipped resource. */
    static final double KEEP = 0.7;

    /** Receivers are filled up to this share of their warehouse. */
    static final double FILL = 0.8;

    private final MarketService market;
    private final VillageRepository villages;
    private final BuildService buildService;
    private final TradeTransportRepository transports;
    private final VillageBanners villageBanners;
    private final NpcEconomy economy;
    private final NpcDefence defence;
    private final NpcBalance balance;

    NpcShipments(
            MarketService market,
            VillageRepository villages,
            BuildService buildService,
            TradeTransportRepository transports,
            VillageBanners villageBanners,
            NpcEconomy economy,
            NpcDefence defence,
            NpcBalance balance) {
        this.market = market;
        this.villages = villages;
        this.buildService = buildService;
        this.transports = transports;
        this.villageBanners = villageBanners;
        this.economy = economy;
        this.defence = defence;
        this.balance = balance;
    }

    /**
     * The village's shipments of this round (§5.5). Off while the world has them off and in the
     * build quiet hours (§5.1): a receiver couldn't build with it overnight. The caller holds the
     * village's lock; {@code send} locks only the sending village. {@code wallGoal} is the
     * village's wall goal of this round (09 §15.4, null if none), so the reserve is the next step
     * the turn planned.
     */
    void ship(NpcTurn turn, Integer wallGoal) {
        WorldSettings world = turn.view.world();
        if (!world.npcShipmentsEnabled() || balance.inBuildQuietHours(turn.now)) {
            return;
        }
        Village donor = turn.village;
        long lordId = turn.view.lordId();
        ResourceAmounts reserve =
                economy.nextCost(
                        donor,
                        lordId,
                        turn.now,
                        turn.view.template(),
                        turn.wantsNobles,
                        turn.view.lesson(),
                        wallGoal);
        ResourceAmounts spare =
                spare(donor.floorResources(), villageBanners.warehouseCapacity(donor), reserve);
        if (spare.total() < Merchants.CAPACITY) {
            return;
        }
        int free = market.freeMerchants(donor, turn.now);
        if (free == 0) {
            return;
        }
        double maxHaste = defence.maxHaste(turn);
        List<Village> receivers = new ArrayList<>();
        for (Village village : villages.findByPlayerIdOrderByIdAsc(lordId)) {
            if (village.getId() != turn.villageId()
                    && turn.view.incoming().stream()
                            .noneMatch(
                                    a ->
                                            a.targetVillageId() == village.getId()
                                                    && NpcDefence.isThreat(turn, a, maxHaste))
                    && stillBuilds(village, turn)) {
                receivers.add(village);
            }
        }
        Coordinates from = donor.coordinates();
        receivers.sort(
                Comparator.comparingDouble((Village v) -> from.distanceTo(v.coordinates()))
                        .thenComparing(Village::getId));
        for (Village receiver : receivers) {
            ResourceAmounts load = load(spare, roomIn(receiver, from, world, turn.now), free);
            if (load.total() == 0) {
                continue;
            }
            market.send(
                    donor.getId(),
                    lordId,
                    new TransportRequest(
                            receiver.coordinates(), load.wood(), load.clay(), load.iron()));
            spare = spare.minus(load);
            free -= Merchants.needed(load.total());
            if (free == 0 || spare.total() < Merchants.CAPACITY) {
                return;
            }
        }
    }

    /**
     * A receiver (§5.5): its template isn't done and it has no academy. A village with an academy
     * may save for nobles, and shipments pay for buildings only (decided for 1.6).
     */
    private boolean stillBuilds(Village village, NpcTurn turn) {
        Map<BuildingType, Integer> current = village.getBuildings().toMap();
        if (current.get(BuildingType.ACADEMY) > 0) {
            return false;
        }
        List<BuildOrder> queue = buildService.queueOf(village.getId());
        Map<BuildingType, Integer> levels = BuildService.levelsWithQueued(current, queue);
        return NpcPlanning.stepCandidate(
                        turn.view.template().buildSteps(turn.view.world().bannerHallAvailable()),
                        levels)
                != null;
    }

    /**
     * The receiver's room up to {@link #FILL}: its stock now (the row isn't locked, so it is
     * estimated from the last catch-up), plus what it produces until merchants from {@code from}
     * arrive (with its Harvest banner, like {@code VillageStateService}), plus what is already on
     * the way to it.
     */
    private ResourceAmounts roomIn(
            Village receiver, Coordinates from, WorldSettings world, Instant now) {
        int capacity = villageBanners.warehouseCapacity(receiver);
        double factor = receiver.getPlayer().getProductionFactor();
        double harvest = villageBanners.effectOf(receiver).harvest();
        double travelHours =
                Merchants.travelSeconds(from.distanceTo(receiver.coordinates()), world) / 3600.0;
        double[] stored = {receiver.getWood(), receiver.getClay(), receiver.getIron()};
        BuildingType[] mines = {
            BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE
        };
        int[] expected = new int[3];
        for (int i = 0; i < 3; i++) {
            double perHour =
                    BannerRules.production(
                            Production.perHour(
                                    receiver.getBuildings().get(mines[i]), world.speed(), factor),
                            harvest);
            double nowStock =
                    Production.advance(
                            stored[i], perHour, capacity, receiver.getResourcesUpdatedAt(), now);
            expected[i] = (int) Math.ceil(Math.min(capacity, nowStock + perHour * travelHours));
        }
        ResourceAmounts incoming = ResourceAmounts.ZERO;
        for (TradeTransport transport : transports.findIncoming(receiver.getId())) {
            incoming = incoming.plus(transport.getResources());
        }
        return room(new ResourceAmounts(expected[0], expected[1], expected[2]), incoming, capacity);
    }

    /**
     * What the donor may ship (§5.5): of each resource at {@link #OVERFLOW} of the warehouse or
     * more, what is above {@link #KEEP} and above the next build step's price; nothing of the
     * others.
     */
    static ResourceAmounts spare(ResourceAmounts stock, int capacity, ResourceAmounts reserve) {
        return new ResourceAmounts(
                spareOf(stock.wood(), capacity, reserve.wood()),
                spareOf(stock.clay(), capacity, reserve.clay()),
                spareOf(stock.iron(), capacity, reserve.iron()));
    }

    private static int spareOf(int stock, int capacity, int reserve) {
        if (stock < OVERFLOW * capacity) {
            return 0;
        }
        int keep = Math.max((int) Math.ceil(KEEP * capacity), reserve);
        return Math.max(0, stock - keep);
    }

    /**
     * The receiver's room (§5.5): up to {@link #FILL} of its warehouse, minus what it will hold
     * when the merchants arrive and what is already on the way.
     */
    static ResourceAmounts room(ResourceAmounts expected, ResourceAmounts incoming, int capacity) {
        int fill = (int) Math.floor(FILL * capacity);
        return new ResourceAmounts(
                Math.max(0, fill - expected.wood() - incoming.wood()),
                Math.max(0, fill - expected.clay() - incoming.clay()),
                Math.max(0, fill - expected.iron() - incoming.iron()));
    }

    /**
     * One shipment: of each resource the smaller of the donor's spare and the receiver's room,
     * scaled down evenly to what the free merchants carry. Nothing if that is less than one full
     * merchant: a trip for a few hundred isn't worth it.
     */
    static ResourceAmounts load(ResourceAmounts spare, ResourceAmounts room, int freeMerchants) {
        int wood = Math.min(spare.wood(), room.wood());
        int clay = Math.min(spare.clay(), room.clay());
        int iron = Math.min(spare.iron(), room.iron());
        long carry = (long) freeMerchants * Merchants.CAPACITY;
        long total = (long) wood + clay + iron;
        if (total > carry) {
            double scale = (double) carry / total;
            wood = (int) Math.floor(wood * scale);
            clay = (int) Math.floor(clay * scale);
            iron = (int) Math.floor(iron * scale);
        }
        if (wood + clay + iron < Merchants.CAPACITY) {
            return ResourceAmounts.ZERO;
        }
        return new ResourceAmounts(wood, clay, iron);
    }
}
