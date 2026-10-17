package de.bannerfall.market;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.Coordinates;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.rules.Merchants;
import de.bannerfall.rules.Travel;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The market (02-game-rules §12): merchants, sending resources, offers and accepting them. Every
 * transport gets a TRADE_ARRIVE event in the same transaction; {@link TradeArriveHandler} handles
 * the arrival.
 *
 * <p>Busy merchants aren't stored: they are the merchants of the village's transports whose {@code
 * returnAt} lies ahead plus the merchants its open offers reserve, counted by query whenever they
 * are needed. No event brings merchants home.
 *
 * <p>Locks: sending and creating or cancelling an offer lock the village only; the target of a
 * transport is read without a lock (nothing of it changes before the arrival, which locks it).
 * Accepting locks both villages, the acceptor's and the offer's.
 *
 * <p>Notifications (after commit): the owner of a transport's target hears of it when it is sent
 * (VILLAGE_UPDATED, his incoming list changed), the owner of an offer when it is accepted.
 */
@Service
public class MarketService {

    /** Largest amount of one resource in a request (a level 30 warehouse holds about 400 000). */
    static final int MAX_AMOUNT = 1_000_000;

    /** Largest {@code count} of a new offer (so that count × amount stays an int). */
    static final int MAX_OFFER_COUNT = 1000;

    private final TradeOfferRepository offers;
    private final TradeTransportRepository transports;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final VillageBanners villageBanners;
    private final EventScheduler scheduler;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AchievementService achievements;
    private final MarketExchangeService exchange;

    MarketService(
            TradeOfferRepository offers,
            TradeTransportRepository transports,
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            VillageBanners villageBanners,
            EventScheduler scheduler,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock,
            AchievementService achievements,
            MarketExchangeService exchange) {
        this.offers = offers;
        this.transports = transports;
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.villageBanners = villageBanners;
        this.scheduler = scheduler;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
        this.achievements = achievements;
        this.exchange = exchange;
    }

    /** The village's merchants, transports, own offers and trading with the market. */
    @Transactional
    public MarketDto market(long villageId, long playerId) {
        Village village = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<TradeOffer> own = offers.findByVillageIdOrderByIdAsc(villageId);
        int total = Merchants.total(village.getBuildings().get(BuildingType.MARKET));
        return new MarketDto(
                new MarketDto.MerchantsDto(total, freeMerchants(village, own, now)),
                transports.findOutgoing(villageId, now).stream().map(TransportDto::of).toList(),
                transports.findIncoming(villageId).stream().map(TransportDto::of).toList(),
                own.stream().map(OfferDto::own).toList(),
                exchange.status(village, now));
    }

    /**
     * Sends resources to any other village (02-game-rules §12): they leave the village now, arrive
     * after the merchants' travel time and are received there like loot (capped at the warehouse).
     * The merchants are back after twice the travel time.
     *
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     * @throws GameRuleException {@code EMPTY_TRANSPORT}, {@code SAME_VILLAGE}, {@code
     *     NOT_ENOUGH_RESOURCES}, {@code NOT_ENOUGH_MERCHANTS}
     */
    @Transactional
    public TransportDto send(long villageId, long playerId, TransportRequest request) {
        Village source = locker.lockOwned(villageId, playerId);
        Coordinates at = request.target();
        Village target =
                villages.findByXAndY(at.x(), at.y())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.TARGET_NOT_FOUND));
        ResourceAmounts resources =
                new ResourceAmounts(request.wood(), request.clay(), request.iron());
        if (resources.total() == 0) {
            throw new GameRuleException(ErrorCode.EMPTY_TRANSPORT, "Nothing to send");
        }
        if (target.getId().equals(source.getId())) {
            throw new GameRuleException(
                    ErrorCode.SAME_VILLAGE, "Resources can't be sent to their own village");
        }
        Instant now = clock.instant();
        stateService.catchUp(source, now);
        int merchants = Merchants.needed(resources.total());
        pay(source, resources, merchants, now);
        TradeTransport transport = start(source, target, resources, merchants, now);
        notifyOwnerOf(target, playerId);
        return TransportDto.of(transport);
    }

    /**
     * Creates an offer (02-game-rules §12): pays {@code count × offerAmount} of the offered
     * resource now and reserves {@code count × ceil(offerAmount / 1000)} merchants.
     *
     * @throws GameRuleException {@code SAME_RESOURCE}, {@code INVALID_RATIO}, {@code
     *     NOT_ENOUGH_RESOURCES}, {@code NOT_ENOUGH_MERCHANTS}
     */
    @Transactional
    public OfferDto createOffer(long villageId, long playerId, OfferRequest request) {
        Village village = locker.lockOwned(villageId, playerId);
        if (request.offerResource() == request.wantResource()) {
            throw new GameRuleException(
                    ErrorCode.SAME_RESOURCE, "An offer must want another resource");
        }
        double maxRatio = worldService.settings().tradeMaxRatio();
        if (!Merchants.ratioAllowed(request.offerAmount(), request.wantAmount(), maxRatio)) {
            throw new GameRuleException(
                    ErrorCode.INVALID_RATIO,
                    "want/offer must be between 1/" + maxRatio + " and " + maxRatio);
        }
        Instant now = clock.instant();
        stateService.catchUp(village, now);
        TradeOffer offer =
                new TradeOffer(
                        village,
                        request.offerResource(),
                        request.offerAmount(),
                        request.wantResource(),
                        request.wantAmount(),
                        request.count(),
                        now);
        pay(village, offer.offered(request.count()), offer.reservedMerchants(), now);
        return OfferDto.own(offers.save(offer));
    }

    /**
     * Cancels one of the player's offers: the resources of what is left of it come back (capped at
     * the warehouse, like every refund), its merchants are free again.
     *
     * @throws NotFoundException {@code OFFER_NOT_FOUND} if none of the player's villages has it
     */
    @Transactional
    public void cancelOffer(long offerId, long playerId) {
        long villageId =
                offers.findVillageIdOfOwner(offerId, playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.OFFER_NOT_FOUND));
        locker.lock(villageId);
        // Read after the lock: it may have been accepted to the end, or the village conquered
        // (which deletes its offers), meanwhile.
        TradeOffer offer =
                offers.findById(offerId)
                        .filter(o -> isOwnedBy(o.getVillage(), playerId))
                        .orElseThrow(() -> new NotFoundException(ErrorCode.OFFER_NOT_FOUND));
        Village village = offer.getVillage();
        stateService.catchUp(village, clock.instant());
        village.receiveResources(
                offer.offered(offer.getRemainingCount()),
                villageBanners.warehouseCapacity(village));
        offers.delete(offer);
    }

    /**
     * Open offers of other players, the nearest first, with distance and merchant travel time from
     * the player's village.
     *
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} if the village isn't the player's
     */
    @Transactional(readOnly = true)
    public List<OfferDto> offersOfOthers(long fromVillageId, long playerId) {
        Village from =
                villages.findById(fromVillageId)
                        .filter(v -> isOwnedBy(v, playerId))
                        .orElseThrow(() -> new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND));
        var world = worldService.settings();
        return offers.findOpenOffersOfOthers(playerId).stream()
                .map(
                        offer -> {
                            double distance =
                                    Travel.distance(
                                            from.coordinates(), offer.getVillage().coordinates());
                            return OfferDto.of(
                                    offer, distance, Merchants.travelSeconds(distance, world));
                        })
                .sorted(
                        Comparator.comparingDouble(OfferDto::distance)
                                .thenComparingLong(OfferDto::id))
                .toList();
    }

    /**
     * Accepts an offer {@code count} times from one of the player's villages (02-game-rules §12):
     * the acceptor pays {@code count × wantAmount} now, and two transports start – the offer's
     * resources to the acceptor (with the merchants the offer reserved) and the payment to the
     * offer's village (with the acceptor's merchants). An offer accepted to the end is deleted.
     *
     * @return the transport to the acceptor, then the one to the offer's village
     * @throws NotFoundException {@code OFFER_NOT_FOUND}, {@code VILLAGE_NOT_FOUND} (the village
     *     isn't the player's)
     * @throws GameRuleException {@code OWN_OFFER}, {@code OFFER_EXHAUSTED}, {@code
     *     NOT_ENOUGH_RESOURCES}, {@code NOT_ENOUGH_MERCHANTS}
     */
    @Transactional
    public List<TransportDto> accept(long offerId, long playerId, AcceptRequest request) {
        long offerVillageId =
                offers.findVillageIdById(offerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.OFFER_NOT_FOUND));
        long acceptorId = request.villageId();
        List<Village> locked = locker.lock(offerVillageId, acceptorId);
        Village acceptor =
                locked.stream()
                        .filter(v -> v.getId() == acceptorId && isOwnedBy(v, playerId))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException(ErrorCode.VILLAGE_NOT_FOUND));
        // Read after the locks: it may have been accepted to the end or cancelled meanwhile.
        TradeOffer offer =
                offers.findById(offerId)
                        .filter(o -> o.getVillage().getId() == offerVillageId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.OFFER_NOT_FOUND));
        Village offerVillage = offer.getVillage();
        if (isOwnedBy(offerVillage, playerId)) {
            throw new GameRuleException(
                    ErrorCode.OWN_OFFER, "Offers of your own villages can't be accepted");
        }
        int count = request.count();
        if (count > offer.getRemainingCount()) {
            throw new GameRuleException(
                    ErrorCode.OFFER_EXHAUSTED,
                    "Offer " + offerId + " has only " + offer.getRemainingCount() + " left");
        }
        Instant now = clock.instant();
        stateService.catchUp(acceptor, now);
        ResourceAmounts payment = offer.wanted(count);
        pay(acceptor, payment, Merchants.needed(payment.total()), now);

        int offerMerchants = Merchants.forOffer(offer.getOfferAmount(), count);
        offer.take(count);
        TradeTransport toAcceptor =
                start(offerVillage, acceptor, offer.offered(count), offerMerchants, now);
        TradeTransport toOffer =
                start(acceptor, offerVillage, payment, Merchants.needed(payment.total()), now);
        if (offer.getRemainingCount() == 0) {
            offers.delete(offer);
        }
        // First Deal and Merchant Prince: what each side gives away (02-game-rules §19)
        deal(offerVillage.getPlayer(), offer.offered(count).total());
        deal(acceptor.getPlayer(), payment.total());
        notifyOwnerOf(offerVillage, playerId);
        return List.of(TransportDto.of(toAcceptor), TransportDto.of(toOffer));
    }

    private void deal(Player giver, int resources) {
        achievements.grant(giver, Achievement.FIRST_DEAL);
        achievements.count(giver, Achievement.MERCHANT_PRINCE, resources);
    }

    /**
     * Checks free merchants and resources (in this order) and pays the resources. The village is
     * locked and caught up.
     */
    private void pay(Village village, ResourceAmounts resources, int merchants, Instant now) {
        int free = freeMerchants(village, offers.findByVillageIdOrderByIdAsc(village.getId()), now);
        if (merchants > free) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_MERCHANTS,
                    "Needs " + merchants + " merchants, " + free + " are free");
        }
        if (!resources.fitsInto(village.floorResources())) {
            throw new GameRuleException(ErrorCode.NOT_ENOUGH_RESOURCES, "Not enough resources");
        }
        village.spend(resources);
    }

    /** Total of the market level minus merchants away and merchants reserved by its offers. */
    private int freeMerchants(Village village, List<TradeOffer> ownOffers, Instant now) {
        long busy = transports.sumBusyMerchants(village.getId(), now);
        for (TradeOffer offer : ownOffers) {
            busy += offer.reservedMerchants();
        }
        int total = Merchants.total(village.getBuildings().get(BuildingType.MARKET));
        return Merchants.free(total, Math.toIntExact(busy));
    }

    /** A transport that leaves now, with its TRADE_ARRIVE event. */
    private TradeTransport start(
            Village source, Village target, ResourceAmounts resources, int merchants, Instant now) {
        long seconds =
                Merchants.travelSeconds(
                        Travel.distance(source.coordinates(), target.coordinates()),
                        worldService.settings());
        TradeTransport transport =
                transports.save(
                        new TradeTransport(source, target, resources, merchants, now, seconds));
        scheduler.schedule(GameEventKind.TRADE_ARRIVE, transport.getId(), transport.getArriveAt());
        return transport;
    }

    /** VILLAGE_UPDATED to the village's owner, unless it is the acting player himself. */
    private void notifyOwnerOf(Village village, long actingPlayerId) {
        Player owner = village.getPlayer();
        if (owner != null && owner.getId() != actingPlayerId) {
            events.publishEvent(
                    new PlayerNotification(
                            owner.getId(), new GameNotification.VillageUpdated(village.getId())));
        }
    }

    /** False for barbarian villages. Reading the id doesn't load the player. */
    private static boolean isOwnedBy(Village village, long playerId) {
        Player owner = village.getPlayer();
        return owner != null && Objects.equals(owner.getId(), playerId);
    }
}
