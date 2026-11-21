package de.bannerfall.npc;

import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.common.Savepoint;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.market.MarketService;
import de.bannerfall.market.TransportDto;
import de.bannerfall.market.TransportRequest;
import de.bannerfall.message.LordLetter;
import de.bannerfall.message.MessageService;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.npc.NpcHelpStore.Request;
import de.bannerfall.npc.NpcWorld.Incoming;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportsCreated;
import de.bannerfall.report.SupportReport;
import de.bannerfall.report.TradeReport;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Help requests from allied lords (2.1 S4, 12-solo-and-allies §9, 02-game-rules §31). In the lord's
 * round, after its village turns ("the help step", solo worlds only): its open request is looked at
 * (ended at the deadline, called off when the attack turned back), its thanks for an answer in time
 * go out, and it may ask: for defenders when an attack it can't hold is on the way to one of its
 * villages, or for resources when a shortage has held its next noble or wall for 12 hours. Each
 * part runs in a transaction of its own and never throws out of {@link #run}.
 *
 * <p>An answer in time is seen in the reports (like trust, {@link NpcTrust}): the battle at the
 * village with the human's support standing there, the merchants' delivery that completes what was
 * asked. Lock order: a village (the battle, the delivery, the thank-you shipment), then the request
 * row, then the trust row.
 */
@Component
class NpcHelp {

    private static final Logger log = LoggerFactory.getLogger(NpcHelp.class);

    /** The defender types the human's support can bring (the client fills these in). */
    static final Set<UnitType> DEFENDERS =
            Set.of(UnitType.SPEARMAN, UnitType.SWORDSMAN, UnitType.ARCHER, UnitType.HEAVY_CAVALRY);

    private final TransactionTemplate tx;

    /**
     * A savepoint inside the battle's transaction for the report listener (2.1 feature review,
     * finding 11): a bug here rolls back only its own writes, never the battle.
     */
    private final Savepoint savepoint;

    private final JdbcClient jdbc;
    private final NpcHelpStore store;
    private final NpcHelpSettings settings;
    private final NpcOrderSettings orderSettings;
    private final NpcTrust trust;
    private final NpcBrainRepository brains;
    private final PlayerRepository players;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitStackRepository stacks;
    private final NpcEconomy economy;
    private final NpcShipments shipments;
    private final NpcDefence defence;
    private final MarketService market;
    private final MessageService messages;
    private final WorldService worldService;
    private final GameData gameData;
    private final Chronicle chronicle;
    private final ApplicationEventPublisher events;

    NpcHelp(
            PlatformTransactionManager transactionManager,
            JdbcClient jdbc,
            NpcHelpStore store,
            NpcHelpSettings settings,
            NpcOrderSettings orderSettings,
            NpcTrust trust,
            NpcBrainRepository brains,
            PlayerRepository players,
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            UnitStackRepository stacks,
            NpcEconomy economy,
            NpcShipments shipments,
            NpcDefence defence,
            MarketService market,
            MessageService messages,
            WorldService worldService,
            GameData gameData,
            ApplicationEventPublisher events,
            Chronicle chronicle,
            Savepoint savepoint) {
        this.chronicle = chronicle;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.savepoint = savepoint;
        this.jdbc = jdbc;
        this.store = store;
        this.settings = settings;
        this.orderSettings = orderSettings;
        this.trust = trust;
        this.brains = brains;
        this.players = players;
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.stacks = stacks;
        this.economy = economy;
        this.shipments = shipments;
        this.defence = defence;
        this.market = market;
        this.messages = messages;
        this.worldService = worldService;
        this.gameData = gameData;
        this.events = events;
    }

    /** The help step of the lord's round at {@code now}, in a solo world. Never throws. */
    void run(NpcTurn.View view, Instant now) {
        long lordId = view.lordId();
        part(lordId, "looking at its help request", () -> review(lordId, now));
        part(lordId, "thanking", () -> thank(view, now));
        part(lordId, "asking for help", () -> ask(view, now));
    }

    private void part(long lordId, String what, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            log.warn("NPC lord {}: {} failed and was rolled back", lordId, what, e);
        }
    }

    // ---- the open request ----

    /**
     * The lord's open request at {@code now}: called off when the lord isn't the human's ally any
     * more, lost the village, or the attack turned back before it landed; at the deadline (once the
     * attack has landed) ignored, or late when the human answered too late.
     */
    void review(long lordId, Instant now) {
        tx.executeWithoutResult(
                status -> {
                    Request open = store.openOf(lordId).orElse(null);
                    if (open == null) {
                        return;
                    }
                    Request request = store.lock(open.id()).orElseThrow();
                    if (request.state() != NpcHelpState.OPEN) {
                        return; // answered meanwhile
                    }
                    Village village = villages.findById(request.villageId()).orElse(null);
                    boolean owns =
                            village != null
                                    && village.getPlayer() != null
                                    && village.getPlayer().getId() == lordId;
                    if (!owns || !allies(lordId, request.playerId())) {
                        end(request, NpcHelpState.CANCELLED, village, now);
                        return;
                    }
                    boolean attackUnderWay =
                            request.kind() == NpcHelpKind.SUPPORT
                                    && attackOnTheWay(request.attackId(), request.villageId());
                    if (now.isBefore(request.deadline())) {
                        if (request.kind() == NpcHelpKind.SUPPORT && !attackUnderWay) {
                            end(request, NpcHelpState.CANCELLED, village, now); // turned back
                        }
                        return;
                    }
                    if (attackUnderWay) {
                        return; // not landed yet (the event worker is behind): next round
                    }
                    end(request, NpcHelpRules.atDeadline(tried(request)), village, now);
                });
    }

    /** Ends a request with its trust and tells the human's client. */
    private void end(Request request, NpcHelpState state, Village village, Instant now) {
        store.close(request.id(), state, now);
        if (state == NpcHelpState.ANSWERED || state == NpcHelpState.IGNORED) {
            chronicle.record(
                    state == NpcHelpState.ANSWERED
                            ? ChronicleKind.HELP_ANSWERED
                            : ChronicleKind.HELP_IGNORED,
                    now,
                    ChronicleFacts.empty()
                            .withLord(chronicle.player(request.lordId()))
                            .withPlayer(chronicle.player(request.playerId()))
                            .withVillage(chronicle.village(request.villageId())));
        }
        int delta = NpcHelpRules.trustFor(state, settings, outOfReach(request));
        if (delta != 0) {
            trust.change(
                    request.lordId(),
                    request.playerId(),
                    delta,
                    switch (state) {
                        case ANSWERED -> TrustReason.HELP_ANSWERED;
                        case DECLINED -> TrustReason.HELP_DECLINED;
                        default -> TrustReason.HELP_IGNORED;
                    },
                    village == null ? null : village.getName(),
                    now);
        }
        events.publishEvent(
                new PlayerNotification(
                        request.playerId(), new GameNotification.HelpRequestsChanged()));
        log.info(
                "Help request {} of NPC lord {} to {}: {}",
                request.id(),
                request.lordId(),
                request.playerId(),
                state);
    }

    /**
     * The human answered, if not in time: for support, troops he sent since the request arrived
     * there or are on the way (a support he left there before does not count); for resources, part
     * of it arrived or his merchants are on the way.
     */
    private boolean tried(Request request) {
        if (request.kind() == NpcHelpKind.SUPPORT) {
            // a support that arrived after the request, or one sent since that is still on the way
            if (request.supported()) {
                return true;
            }
            return jdbc.sql(
                                    """
                                    select count(*) from movement m
                                    where m.target_village_id = :village
                                      and m.owner_player_id = :player and m.kind = 'SUPPORT'
                                      and m.depart_at >= :since
                                    """)
                            .param("village", request.villageId())
                            .param("player", request.playerId())
                            .param("since", Timestamp.from(request.createdAt()))
                            .query(Long.class)
                            .single()
                    > 0;
        }
        if (request.delivered().total() > 0) {
            return true;
        }
        return jdbc.sql(
                                """
                                select count(*) from trade_transport t
                                join village s on s.id = t.source_village_id
                                where t.target_village_id = :village and not t.arrived
                                  and s.player_id = :player
                                """)
                        .param("village", request.villageId())
                        .param("player", request.playerId())
                        .query(Long.class)
                        .single()
                > 0;
    }

    /**
     * Whether the attack is still on the way to the village. A cancelled attack keeps its id and
     * turns into a RETURN home, so the kind and the target are checked too.
     */
    private boolean attackOnTheWay(Long movementId, long villageId) {
        return movementId != null
                && jdbc.sql(
                                        "select count(*) from movement where id = :id and kind ="
                                                + " 'ATTACK' and target_village_id = :village")
                                .param("id", movementId)
                                .param("village", villageId)
                                .query(Long.class)
                                .single()
                        > 0;
    }

    /** The lord and the human are in the same guild. */
    private boolean allies(long lordId, long playerId) {
        return jdbc.sql(
                                """
                                select count(*) from guild_member x join guild_member y
                                    on y.guild_id = x.guild_id
                                where x.player_id = :lord and y.player_id = :player
                                """)
                        .param("lord", lordId)
                        .param("player", playerId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    // ---- an answer in time, seen in the reports ----

    /**
     * A battle at a lord's village or a delivery to it: a request it answers in time. It runs in
     * the report's (the battle's) transaction, behind a savepoint: an error here is logged and
     * leaves the request as it was, the battle stands.
     */
    @EventListener
    void onReports(ReportsCreated created) {
        if (!worldService.settings().soloMode()) {
            return;
        }
        try {
            savepoint.run(() -> answeredBy(created));
        } catch (RuntimeException e) {
            log.error("Help requests: the {} report could not be checked", created.kind(), e);
        }
    }

    private void answeredBy(ReportsCreated created) {
        switch (created.payload()) {
            case BattleReport battle when created.kind() == ReportKind.DEFENSE ->
                    defended(battle, created);
            case SupportReport support when created.kind() == ReportKind.SUPPORT_ARRIVED ->
                    supportArrived(support, created.happenedAt());
            case TradeReport trade -> delivered(trade, created.happenedAt());
            default -> {}
        }
    }

    /**
     * A support of the human arrived at the village a lord asked him to defend, after the request
     * and by its deadline: from now on his support there answers it. One he left there before the
     * request does not (the lord asked because it was not enough). One that arrives after the
     * deadline ends the request as late at once: the movement is gone by the lord's next round,
     * which would otherwise count it as ignored.
     */
    private void supportArrived(SupportReport support, Instant at) {
        Long from = support.from().playerId();
        Long to = support.to().playerId();
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        Request open = store.openOf(to).orElse(null);
        if (open == null
                || open.kind() != NpcHelpKind.SUPPORT
                || open.playerId() != from
                || open.villageId() != support.to().id()) {
            return;
        }
        if (!at.isAfter(open.deadline())) {
            store.markSupported(open.id());
            return;
        }
        Request request = store.lock(open.id()).orElse(null);
        if (request != null && request.state() == NpcHelpState.OPEN) {
            end(
                    request,
                    NpcHelpState.LATE,
                    villages.findById(request.villageId()).orElse(null),
                    at);
        }
    }

    /**
     * A battle at a lord's village: the human it asked answered in time when a support he sent
     * after the request arrived and his support fought there (the supporters' copy of the report)
     * or stood there as the attack came (a battle of scouts only sends no copy), if the battle came
     * by the deadline. Only the battle of the attack the lord asked about counts (2.1 feature
     * review, finding 09): a scouting or another attacker landing first leaves the request open.
     */
    private void defended(BattleReport battle, ReportsCreated created) {
        Long lord = battle.defender().playerId();
        if (lord == null) {
            return;
        }
        Request open = store.openOf(lord).orElse(null);
        Instant at = created.happenedAt();
        if (open == null
                || open.kind() != NpcHelpKind.SUPPORT
                || open.villageId() != battle.defender().id()
                || !open.supported()
                || at.isAfter(open.deadline())) {
            return;
        }
        if (!theAskedAttack(open, battle.attacker(), at)) {
            return;
        }
        boolean fought = created.recipients().contains(open.playerId());
        if (!fought && !supportStands(open.villageId(), open.playerId())) {
            return;
        }
        Request request = store.lock(open.id()).orElse(null);
        if (request != null && request.state() == NpcHelpState.OPEN) {
            end(
                    request,
                    NpcHelpState.ANSWERED,
                    villages.findById(request.villageId()).orElse(null),
                    at);
        }
    }

    /**
     * Whether the battle is the one of the attack the request is about: that movement (still in the
     * table while its battle is written; the caller deletes it afterwards) is an attack on the
     * village from the battle's attacker that has landed by now. A cancelled attack keeps its id as
     * a RETURN, hence the kind and the target.
     */
    private boolean theAskedAttack(Request request, VillageRefDto attacker, Instant at) {
        return request.attackId() != null
                && jdbc.sql(
                                        """
                                        select count(*) from movement where id = :id
                                          and kind = 'ATTACK' and target_village_id = :village
                                          and home_village_id = :attacker and arrive_at <= :at
                                        """)
                                .param("id", request.attackId())
                                .param("village", request.villageId())
                                .param("attacker", attacker.id())
                                .param("at", Timestamp.from(at))
                                .query(Long.class)
                                .single()
                        > 0;
    }

    /** Whether a support stack of the human stands in the village. */
    private boolean supportStands(long villageId, long playerId) {
        return jdbc.sql(
                                """
                                select count(*) from unit_stack s join village h
                                    on h.id = s.home_village_id
                                where s.location_village_id = :village
                                  and s.home_village_id <> s.location_village_id
                                  and h.player_id = :player
                                """)
                        .param("village", villageId)
                        .param("player", playerId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    /**
     * The human's merchants delivered to a lord's village: added to his open request for resources
     * there; once everything asked for has arrived by the deadline, it is answered.
     */
    private void delivered(TradeReport trade, Instant at) {
        Long from = trade.from().playerId();
        Long to = trade.to().playerId();
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        for (Request request : store.lockOpenAt(trade.to().id(), from)) {
            if (request.kind() != NpcHelpKind.RESOURCES || request.lordId() != to) {
                continue;
            }
            store.addDelivered(request.id(), trade.resources());
            ResourceAmounts total = request.delivered().plus(trade.resources());
            if (NpcHelpRules.covered(request.asked(), total) && !at.isAfter(request.deadline())) {
                end(
                        request,
                        NpcHelpState.ANSWERED,
                        villages.findById(request.villageId()).orElse(null),
                        at);
            }
        }
    }

    // ---- the human says no ----

    /** "Not this time": the human's open request ends declined. False if it isn't open. */
    boolean decline(long playerId, long requestId, Instant now) {
        Request request = store.lock(requestId).orElse(null);
        if (request == null
                || request.playerId() != playerId
                || request.state() != NpcHelpState.OPEN
                || !now.isBefore(request.deadline())) {
            return false;
        }
        end(
                request,
                NpcHelpState.DECLINED,
                villages.findById(request.villageId()).orElse(null),
                now);
        return true;
    }

    // ---- thanks ----

    /**
     * Thanks for each request answered in time: a letter, with a shipment from the lord's surplus
     * when it sent none to that human in the last day (÷ speed).
     */
    void thank(NpcTurn.View view, Instant now) {
        List<Long> due =
                tx.execute(
                        status ->
                                jdbc.sql(
                                                "select id from npc_help_request where lord_id ="
                                                        + " :lord and thanks_due order by id")
                                        .param("lord", view.lordId())
                                        .query(Long.class)
                                        .list());
        for (long id : due) {
            tx.executeWithoutResult(status -> thankFor(view, id, now));
        }
    }

    /** One thanks, in its own transaction: the donor village first, then the request row. */
    private void thankFor(NpcTurn.View view, long requestId, Instant now) {
        Request request = store.find(requestId).orElse(null);
        if (request == null || !request.thanksDue()) {
            return;
        }
        double speed = view.world().speed();
        boolean ships =
                NpcHelpRules.shipsThanks(
                        store.lastThanks(view.lordId(), request.playerId()), now, speed, settings);
        Shipped shipped = ships ? ship(view, request, now) : null;
        Request locked = store.lock(requestId).orElse(null);
        if (locked == null || !locked.thanksDue()) {
            // thanked meanwhile: the shipment goes back with the transaction
            throw new IllegalStateException("help request " + requestId + " thanked twice");
        }
        store.thanked(requestId, shipped == null ? locked.thankedAt() : now);
        VillageRefDto target =
                villages.findById(request.villageId()).map(VillageRefDto::of).orElse(null);
        messages.sendLetter(
                view.lordId(),
                request.playerId(),
                new LordLetter(
                        LordLetter.Kind.THANKS,
                        null,
                        shipped == null ? target : shipped.to(),
                        null,
                        shipped == null ? null : shipped.load(),
                        shipped == null ? null : shipped.arriveAt(),
                        null));
    }

    private record Shipped(VillageRefDto to, ResourceAmounts load, Instant arriveAt) {}

    /**
     * The thank-you shipment: {@code thanks-share} of what the lord's village nearest the human's
     * holds above its own next goal, as the human's room and the free merchants allow; null if none
     * of its villages near him has anything to spare.
     */
    private Shipped ship(NpcTurn.View view, Request request, Instant now) {
        Village receiver = nearestOf(request.playerId(), request.villageId()).orElse(null);
        if (receiver == null) {
            return null;
        }
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        List<Long> donors =
                view.villages().values().stream()
                        .filter(v -> Objects.equals(v.playerId(), view.lordId()))
                        .sorted(
                                Comparator.comparingDouble(
                                                (MapVillage v) ->
                                                        v.coordinates()
                                                                .distanceTo(receiver.coordinates()))
                                        .thenComparingLong(MapVillage::id))
                        .limit(orderSettings.candidates())
                        .map(MapVillage::id)
                        .toList();
        for (long donorId : donors) {
            Village donor = locker.lock(donorId).stream().findFirst().orElse(null);
            if (donor == null
                    || donor.getPlayer() == null
                    || donor.getPlayer().getId() != view.lordId()) {
                continue;
            }
            stateService.catchUp(donor, now);
            NpcMemory memory = brain.getMemory();
            Integer wallGoal =
                    view.world().npcLearningEnabled()
                            ? NpcLearning.wallGoal(memory.lessons(), donorId, now)
                            : null;
            ResourceAmounts goal =
                    economy.nextCost(
                            donor,
                            view.lordId(),
                            now,
                            view.template(),
                            memory.conquest().containsKey(donorId),
                            view.lesson(),
                            wallGoal);
            ResourceAmounts surplus = share(NpcOrderRules.surplus(donor.floorResources(), goal));
            ResourceAmounts load =
                    NpcShipments.load(
                            surplus,
                            shipments.roomIn(receiver, donor.coordinates(), view.world(), now),
                            market.freeMerchants(donor, now));
            if (load.total() == 0) {
                continue;
            }
            TransportDto transport =
                    market.send(
                            donorId,
                            view.lordId(),
                            new TransportRequest(
                                    receiver.coordinates(), load.wood(), load.clay(), load.iron()));
            return new Shipped(VillageRefDto.of(receiver), load, transport.arriveAt());
        }
        return null;
    }

    private ResourceAmounts share(ResourceAmounts surplus) {
        double share = settings.thanksShare();
        return new ResourceAmounts(
                (int) Math.floor(surplus.wood() * share),
                (int) Math.floor(surplus.clay() * share),
                (int) Math.floor(surplus.iron() * share));
    }

    /** The human's village nearest to {@code villageId}, if he has one. */
    private Optional<Village> nearestOf(long playerId, long villageId) {
        Village to = villages.findById(villageId).orElse(null);
        if (to == null) {
            return Optional.empty();
        }
        return villages.findByPlayerIdOrderByIdAsc(playerId).stream()
                .min(
                        Comparator.comparingDouble(
                                        (Village v) -> v.coordinates().distanceTo(to.coordinates()))
                                .thenComparingLong(Village::getId));
    }

    // ---- asking ----

    /**
     * Asks a human of its guild for help when it may (12 §9): first for defenders against an attack
     * it can't hold (the soonest), else for the resources a shortage has held its noble or wall
     * for. The human with the highest trust is asked (ties: the lower id).
     */
    void ask(NpcTurn.View view, Instant now) {
        tx.executeWithoutResult(status -> askNow(view, now));
    }

    private void askNow(NpcTurn.View view, Instant now) {
        long lordId = view.lordId();
        Long human = humanToAsk(lordId, now);
        double speed = view.world().speed();
        int today = human == null ? 0 : store.countSince(human, now.minus(NpcHelpRules.day(speed)));
        if (!NpcHelpRules.mayAsk(
                view.world().soloMode(),
                human != null,
                store.openOf(lordId).isPresent(),
                today,
                settings)) {
            return;
        }
        if (askForDefenders(view, human, now)) {
            return;
        }
        askForResources(view, human, now);
    }

    /** The human of the lord's guild it trusts most; null when it is no human's ally. */
    private Long humanToAsk(long lordId, Instant now) {
        List<Long> humans =
                jdbc.sql(
                                """
                                select o.player_id from guild_member me
                                join guild_member o on o.guild_id = me.guild_id
                                join player p on p.id = o.player_id
                                where me.player_id = :lord and not p.npc and p.village_count > 0
                                order by o.player_id
                                """)
                        .param("lord", lordId)
                        .query(Long.class)
                        .list();
        Long best = null;
        double bestTrust = Double.NEGATIVE_INFINITY;
        for (long human : humans) {
            double value = trust.trust(lordId, human, now);
            if (value > bestTrust) {
                best = human;
                bestTrust = value;
            }
        }
        return best;
    }

    /** A request for defenders, if an attack the lord can't hold is on the way. */
    private boolean askForDefenders(NpcTurn.View view, long human, Instant now) {
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcMemory memory = brain.getMemory();
        List<Incoming> threats =
                view.incoming().stream()
                        .filter(a -> owner(view, a.targetVillageId()) == view.lordId())
                        .filter(a -> owner(view, a.sourceVillageId()) != human)
                        .sorted(
                                Comparator.comparing(Incoming::arriveAt)
                                        .thenComparingLong(Incoming::id))
                        .toList();
        for (Incoming attack : threats) {
            Village village = villages.findById(attack.targetVillageId()).orElse(null);
            if (village == null) {
                continue;
            }
            NpcTurn turn =
                    new NpcTurn(
                            view, village, now, memory.draft(), Set.of(village.getId()), Set.of());
            if (!NpcDefence.isThreat(turn, attack, defence.maxHaste(turn))) {
                continue;
            }
            int wanted = defendersWanted(view, attack, village, memory);
            if (wanted <= 0) {
                continue;
            }
            MapVillage source = view.villages().get(attack.sourceVillageId());
            String attacker =
                    source == null || source.playerId() == null
                            ? null
                            : players.findById(source.playerId()).map(Player::getName).orElse(null);
            long id =
                    store.insert(
                            view.lordId(),
                            human,
                            NpcHelpKind.SUPPORT,
                            village.getId(),
                            attack.id(),
                            wanted,
                            null,
                            ResourceAmounts.ZERO,
                            now,
                            attack.arriveAt());
            send(
                    view,
                    human,
                    id,
                    NpcHelpKind.SUPPORT,
                    new LordLetter(
                            LordLetter.Kind.HELP_SUPPORT,
                            null,
                            VillageRefDto.of(village),
                            null,
                            null,
                            attack.arriveAt(),
                            null,
                            null,
                            id,
                            wanted,
                            attacker,
                            null,
                            null));
            return true;
        }
        return false;
    }

    /**
     * Whether nothing the human sends could have landed in time (review 2026-11-19): spearmen from
     * his village nearest the lord's, leaving when the lord asked, arrive after the attack. Saying
     * no to such a request, or leaving it, costs him no trust.
     */
    boolean outOfReach(Request request) {
        if (request.kind() != NpcHelpKind.SUPPORT) {
            return false;
        }
        Village village = villages.findById(request.villageId()).orElse(null);
        Village from = nearestOf(request.playerId(), request.villageId()).orElse(null);
        return village != null
                && from != null
                && !NpcHelpRules.reachable(
                        request.createdAt(),
                        Travel.travelSeconds(
                                gameData,
                                worldService.settings(),
                                from.coordinates(),
                                village.coordinates(),
                                Map.of(UnitType.SPEARMAN, 1)),
                        request.deadline());
    }

    /**
     * The defenders the lord asks for against {@code attack} (12 §9), 0 if it expects to hold: the
     * army its intel saw at home in the attacking village against what stands in its village; with
     * no intel there it fears an attacker with more points than itself (09 §6.1 needs intel).
     */
    private int defendersWanted(
            NpcTurn.View view, Incoming attack, Village village, NpcMemory memory) {
        Map<UnitType, Integer> standing = new EnumMap<>(UnitType.class);
        for (UnitStack stack : stacks.findByLocationVillageId(village.getId())) {
            stack.getUnits().toMap().forEach((t, c) -> standing.merge(t, c, Integer::sum));
        }
        int wall = village.getBuildings().get(BuildingType.WALL);
        double margin = view.template().margin();
        NpcMemory.Intel seen = memory.intel().get(attack.sourceVillageId());
        if (seen != null && seen.units() != null && !seen.units().isEmpty()) {
            Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
            seen.units()
                    .forEach(
                            (type, count) -> {
                                if (type != UnitType.SCOUT && count > 0) {
                                    army.put(type, count);
                                }
                            });
            if (army.isEmpty()
                    || !NpcHelpRules.cannotHold(gameData, army, standing, wall, margin)) {
                return 0;
            }
            return NpcHelpRules.defendersNeeded(gameData, army, standing, wall, margin, settings);
        }
        MapVillage source = view.villages().get(attack.sourceVillageId());
        if (source == null || !NpcHelpRules.fearsUnseen(source.ownerPoints(), view.lordPoints())) {
            return 0;
        }
        int count = standing.values().stream().mapToInt(Integer::intValue).sum();
        return NpcHelpRules.unseenDefenders(count, settings);
    }

    /** A request for resources, if a shortage has held a noble or the wall long enough. */
    private void askForResources(NpcTurn.View view, long human, Instant now) {
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcMemory.Draft memory = brain.getMemory().draft();
        double speed = view.world().speed();
        Map.Entry<Long, NpcMemory.Shortage> held =
                memory.shortages.entrySet().stream()
                        .filter(
                                e ->
                                        NpcHelpRules.shortageHeld(
                                                e.getValue().since(), now, speed, settings))
                        .filter(e -> e.getValue().missing().total() > 0)
                        .min(
                                Comparator.comparing(
                                                (Map.Entry<Long, NpcMemory.Shortage> e) ->
                                                        e.getValue().since())
                                        .thenComparing(Map.Entry::getKey))
                        .orElse(null);
        if (held == null) {
            return;
        }
        Village village = villages.findById(held.getKey()).orElse(null);
        if (village == null
                || village.getPlayer() == null
                || village.getPlayer().getId() != view.lordId()) {
            return;
        }
        NpcMemory.Shortage shortage = held.getValue();
        String goal = shortage.goal().startsWith("WALL") ? "WALL" : "NOBLE";
        Instant deadline = now.plus(NpcHelpRules.resourceWindow(speed, settings));
        long id =
                store.insert(
                        view.lordId(),
                        human,
                        NpcHelpKind.RESOURCES,
                        village.getId(),
                        null,
                        0,
                        goal,
                        shortage.missing(),
                        now,
                        deadline);
        // the wait starts again: the next request for this step comes after another 12 hours
        memory.shortages.put(
                village.getId(), new NpcMemory.Shortage(shortage.goal(), now, shortage.missing()));
        brain.setMemory(memory.build());
        send(
                view,
                human,
                id,
                NpcHelpKind.RESOURCES,
                new LordLetter(
                        LordLetter.Kind.HELP_RESOURCES,
                        null,
                        VillageRefDto.of(village),
                        null,
                        shortage.missing(),
                        deadline,
                        null,
                        null,
                        id,
                        null,
                        null,
                        goal,
                        null));
    }

    /** The request's letter (no message notice: the request's own pushes) and its notice. */
    private void send(NpcTurn.View view, long human, long id, NpcHelpKind kind, LordLetter letter) {
        messages.sendLetter(view.lordId(), human, letter, false);
        String name = players.findById(view.lordId()).map(Player::getName).orElse("");
        events.publishEvent(
                new PlayerNotification(
                        human,
                        new GameNotification.HelpRequested(id, view.lordId(), name, kind.name())));
        log.info("NPC lord {} asks {} for help: {} (request {})", view.lordId(), human, kind, id);
    }

    private static long owner(NpcTurn.View view, long villageId) {
        MapVillage village = view.villages().get(villageId);
        return village == null || village.playerId() == null ? -1 : village.playerId();
    }
}
