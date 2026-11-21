package de.bannerfall.npc;

import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.market.MarketService;
import de.bannerfall.market.TransportDto;
import de.bannerfall.market.TransportRequest;
import de.bannerfall.message.LordLetter;
import de.bannerfall.message.MessageService;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandPreviewDto;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcOrderStore.Order;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orders to allied lords in the lord's round (2.1 S3, 12-solo-and-allies §8): the orders step runs
 * before the village turns, so an order is carried out in the round that answers it. The lord
 * weighs an order with its own rules ({@link NpcOrderRules}): "No" below a trust of 20 or against
 * its own guild, an attack only when its estimate is a clear win (09 §6.1) with up to its
 * personality's share of attack units and within the caps (09 §7), a support with up to its support
 * share doubled, resources only above its own next goal. Every answer is a letter. Its villages are
 * tried nearest to the target first, each in a transaction of its own that locks only that village.
 */
@Component
class NpcOrders {

    private static final Logger log = LoggerFactory.getLogger(NpcOrders.class);

    private final TransactionTemplate tx;
    private final NpcOrderStore store;
    private final NpcOrderSettings settings;
    private final NpcTrust trust;
    private final NpcBrainRepository brains;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final NpcWarfare warfare;
    private final NpcDefence defence;
    private final NpcEconomy economy;
    private final NpcShipments shipments;
    private final CommandService commands;
    private final MarketService market;
    private final MessageService messages;
    private final EventScheduler scheduler;
    private final NpcOrderSheet sheet;
    private final NpcWorld world;
    private final Chronicle chronicle;
    private final NpcGuilds guilds;

    NpcOrders(
            PlatformTransactionManager transactionManager,
            NpcOrderStore store,
            NpcOrderSettings settings,
            NpcTrust trust,
            NpcBrainRepository brains,
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            NpcWarfare warfare,
            NpcDefence defence,
            NpcEconomy economy,
            NpcShipments shipments,
            CommandService commands,
            MarketService market,
            MessageService messages,
            EventScheduler scheduler,
            NpcOrderSheet sheet,
            NpcWorld world,
            NpcGuilds guilds,
            Chronicle chronicle) {
        this.chronicle = chronicle;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.store = store;
        this.settings = settings;
        this.trust = trust;
        this.brains = brains;
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.warfare = warfare;
        this.defence = defence;
        this.economy = economy;
        this.shipments = shipments;
        this.commands = commands;
        this.market = market;
        this.messages = messages;
        this.scheduler = scheduler;
        this.sheet = sheet;
        this.world = world;
        this.guilds = guilds;
    }

    /**
     * What the lord's current order means for its own turns.
     *
     * @param held the villages it leaves alone (a {@code HOLD} order it said yes to)
     * @param supportAt the village its ordered support stands in, which it doesn't withdraw; null
     * @param supportFrom the home village of that support; null
     * @param reserved per village: the units a planned "land together" march waits for, which the
     *     village neither sends elsewhere nor counts as available
     */
    record Standing(
            Set<Long> held,
            Long supportAt,
            Long supportFrom,
            Map<Long, Map<UnitType, Integer>> reserved) {

        static final Standing NONE = new Standing(Set.of(), null, null, Map.of());

        /** The units {@code villageId} keeps for a planned march (empty if none). */
        Map<UnitType, Integer> reservedAt(long villageId) {
            return reserved.getOrDefault(villageId, Map.of());
        }

        /** The support stack from {@code homeId} in {@code locationId} stands there on order. */
        boolean ordered(long homeId, long locationId) {
            return supportAt != null
                    && supportAt == locationId
                    && supportFrom != null
                    && supportFrom == homeId;
        }
    }

    /**
     * The lord's standing order, inside the caller's transaction. An order of a human who is no
     * guild mate any more counts for nothing (the orders step drops it).
     */
    Standing standing(long lordId, Set<Long> mates) {
        Order order = store.find(lordId).orElse(null);
        if (order == null || !mates.contains(order.playerId())) {
            return Standing.NONE;
        }
        return switch (order.state()) {
            case ACTIVE ->
                    switch (order.kind()) {
                        case HOLD ->
                                new Standing(Set.of(order.targetVillageId()), null, null, Map.of());
                        case SUPPORT ->
                                new Standing(
                                        Set.of(),
                                        order.targetVillageId(),
                                        order.sourceVillageId(),
                                        Map.of());
                        default -> Standing.NONE;
                    };
            case PLANNED ->
                    order.sourceVillageId() == null || order.army() == null
                            ? Standing.NONE
                            : new Standing(
                                    Set.of(),
                                    null,
                                    null,
                                    Map.of(order.sourceVillageId(), Map.copyOf(order.army())));
            default -> Standing.NONE;
        };
    }

    /**
     * The orders step of the round at {@code now}: an open order is answered and carried out. Never
     * throws: a failure is logged and the order waits for the next round.
     */
    void run(NpcTurn.View view, Instant now) {
        try {
            Order order = tx.execute(status -> store.find(view.lordId()).orElse(null));
            if (order == null
                    || (order.state() != NpcOrderState.OPEN
                            && order.state() != NpcOrderState.SCOUTING)) {
                return;
            }
            decide(order, view, now);
        } catch (OrderChanged e) {
            log.info("NPC lord {}: its order changed while it weighed it", view.lordId());
        } catch (RuntimeException e) {
            log.warn("NPC lord {}: the orders step failed", view.lordId(), e);
        }
    }

    private void decide(Order order, NpcTurn.View view, Instant now) {
        long lordId = view.lordId();
        MapVillage target = view.villages().get(order.targetVillageId());
        if (!view.world().soloMode()
                || !view.guildMates().contains(order.playerId())
                || target == null) {
            // the human left the guild (or the lord did), or the world isn't solo any more
            dropIfUnchanged(order);
            return;
        }
        if (NpcOrderRules.refuses(trust.trust(lordId, order.playerId(), now), settings)) {
            refuse(order, LordLetter.Kind.ORDER_REFUSED_TRUST, now);
            return;
        }
        switch (order.kind()) {
            case HOLD -> hold(order, now);
            case ATTACK -> attack(order, view, target, now);
            case SUPPORT -> support(order, view, target, now);
            case SEND -> send(order, view, target, now);
        }
    }

    private void hold(Order order, Instant now) {
        tx.executeWithoutResult(
                status -> {
                    requireUnchanged(order);
                    store.save(
                            order.answered(NpcOrderState.ACTIVE, LordLetter.Kind.ORDER_HOLD, now));
                    letter(order, LordLetter.order(LordLetter.Kind.ORDER_HOLD, ref(order)));
                });
    }

    // ---- attack ----

    /** How one village's try at an ordered attack ended. */
    private enum Outcome {
        DONE,
        NO_GO,
        LATE
    }

    private void attack(Order order, NpcTurn.View view, MapVillage target, Instant now) {
        long lordId = view.lordId();
        if (NpcOrderRules.bound(target.playerId(), lordId, view.guildMates())) {
            refuse(order, LordLetter.Kind.ORDER_REFUSED_BOUND, now);
            return;
        }
        if (!target.barbarian() && NpcTargeting.isProtected(target, now)) {
            refuse(order, LordLetter.Kind.ORDER_PROTECTED, now);
            return;
        }
        List<Long> candidates = candidates(view, target);
        if (candidates.isEmpty()) {
            refuse(order, LordLetter.Kind.ORDER_TOO_STRONG, now);
            return;
        }
        // what the lord knows of the target and its caps hold for every one of its villages
        Boolean seenAndAllowed =
                tx.execute(
                        status -> {
                            NpcTurn first = turn(view, unlocked(candidates.getFirst()), now);
                            if (warfare.usableIntel(first, target) == null) {
                                return null;
                            }
                            return NpcWarfare.capsAllow(first, target, 1);
                        });
        if (seenAndAllowed == null) {
            scoutFirst(order, view, target, candidates, now);
            return;
        }
        if (!seenAndAllowed) {
            refuse(order, LordLetter.Kind.ORDER_CAPS, now);
            return;
        }
        Instant own = null;
        if (order.landTogether()) {
            own = tx.execute(status -> ownAttackArrival(order, now));
            if (own == null) {
                refuse(order, LordLetter.Kind.ORDER_NOT_IN_TIME, now);
                return;
            }
        }
        boolean late = false;
        for (long villageId : candidates) {
            Instant ownArrival = own;
            Outcome outcome =
                    tx.execute(
                            status -> attackFrom(order, view, target, villageId, ownArrival, now));
            if (outcome == Outcome.DONE) {
                return;
            }
            late |= outcome == Outcome.LATE;
        }
        refuse(
                order,
                late ? LordLetter.Kind.ORDER_NOT_IN_TIME : LordLetter.Kind.ORDER_TOO_STRONG,
                now);
    }

    /**
     * One village's try: up to the personality's share of its available attack units, a go only if
     * the estimate (09 §6.1) is one. With "land together" it leaves now if it lands within the
     * window, or plans the march for later; the cap entry counts either way.
     */
    private Outcome attackFrom(
            Order order,
            NpcTurn.View view,
            MapVillage target,
            long villageId,
            Instant ownArrival,
            Instant now) {
        requireUnchanged(order);
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcTurn turn = lockedTurn(view, villageId, now, brain);
        if (turn == null || threatened(turn)) {
            return Outcome.NO_GO;
        }
        Intel intel = warfare.usableIntel(turn, target);
        if (intel == null) {
            return Outcome.NO_GO;
        }
        Map<UnitType, Integer> army =
                NpcOrderRules.army(
                        turn.available,
                        turn.template().attackUnits(),
                        NpcOrderRules.attackShare(view.personality(), settings));
        if (army.isEmpty()) {
            return Outcome.NO_GO;
        }
        int wall = intel.wall() == null ? 0 : intel.wall();
        BuildingType catapultTarget = wall > 0 ? BuildingType.WALL : BuildingType.BARRACKS;
        int level =
                intel.buildings() == null ? 0 : intel.buildings().getOrDefault(catapultTarget, 0);
        if (!warfare.estimateAttack(turn, target, army, intel, catapultTarget, level).go()) {
            return Outcome.NO_GO;
        }
        BuildingType sentTarget = army.containsKey(UnitType.CATAPULT) ? catapultTarget : null;
        Instant arrival = warfare.previewAttack(turn, target, army, sentTarget);
        if (arrival == null) {
            return Outcome.NO_GO;
        }
        if (ownArrival != null) {
            long travel = Duration.between(now, arrival).toSeconds();
            NpcOrderRules.Timing timing =
                    NpcOrderRules.landTogether(now, ownArrival, travel, settings);
            if (timing instanceof NpcOrderRules.Timing.TooLate) {
                return Outcome.LATE;
            }
            if (timing instanceof NpcOrderRules.Timing.Later later) {
                NpcWarfare.recordSent(turn, target);
                brain.setMemory(turn.memory.build());
                store.save(
                        order.carried(
                                        villageId,
                                        later.departAt(),
                                        later.arriveAt(),
                                        army,
                                        sentTarget)
                                .answered(
                                        NpcOrderState.PLANNED,
                                        LordLetter.Kind.ORDER_ATTACK_PLANNED,
                                        now));
                scheduler.cancel(GameEventKind.NPC_ORDER_MARCH, view.lordId());
                scheduler.schedule(GameEventKind.NPC_ORDER_MARCH, view.lordId(), later.departAt());
                letter(
                        order,
                        new LordLetter(
                                LordLetter.Kind.ORDER_ATTACK_PLANNED,
                                null,
                                ref(order),
                                army,
                                null,
                                later.arriveAt(),
                                later.departAt()));
                return Outcome.DONE;
            }
        }
        warfare.send(turn, target, army, sentTarget);
        NpcWarfare.recordSent(turn, target);
        turn.memory.farmed.put(target.id(), arrival);
        brain.setMemory(turn.memory.build());
        store.save(
                order.carried(villageId, now, arrival, army, sentTarget)
                        .answered(NpcOrderState.ACTIVE, LordLetter.Kind.ORDER_ATTACK, now));
        letter(
                order,
                new LordLetter(
                        LordLetter.Kind.ORDER_ATTACK, null, ref(order), army, null, arrival, null));
        chronicle.record(
                ChronicleKind.ORDER_ATTACK,
                now,
                ChronicleFacts.empty()
                        .withLord(chronicle.player(order.lordId()))
                        .withPlayer(chronicle.player(order.playerId()))
                        .withVillage(chronicle.village(order.targetVillageId())));
        return Outcome.DONE;
    }

    /**
     * No usable intel: the lord scouts the target first (09 §6.3) and answers once it has seen it.
     * Scouts on the way: it waits. After {@code scout-tries} scoutings, or without scouts to send,
     * it answers that it can't see the village.
     */
    private void scoutFirst(
            Order order, NpcTurn.View view, MapVillage target, List<Long> candidates, Instant now) {
        boolean underWay =
                Boolean.TRUE.equals(
                        tx.execute(
                                status ->
                                        world.ownAttackTargets(view.lordId())
                                                .contains(target.id())));
        if (underWay) {
            return;
        }
        if (order.scoutTries() >= settings.scoutTries()) {
            refuse(order, LordLetter.Kind.ORDER_UNSEEN, now);
            return;
        }
        for (long villageId : candidates) {
            boolean sent =
                    Boolean.TRUE.equals(
                            tx.execute(status -> scoutFrom(order, view, target, villageId, now)));
            if (sent) {
                return;
            }
        }
        refuse(order, LordLetter.Kind.ORDER_UNSEEN, now);
    }

    private boolean scoutFrom(
            Order order, NpcTurn.View view, MapVillage target, long villageId, Instant now) {
        requireUnchanged(order);
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcTurn turn = lockedTurn(view, villageId, now, brain);
        if (turn == null) {
            return false;
        }
        int scouts = NpcTargeting.scoutCount(turn.memory.intel.get(target.id()));
        if (turn.available.getOrDefault(UnitType.SCOUT, 0) < scouts
                || (!target.barbarian() && !NpcWarfare.capsAllow(turn, target, 2))) {
            return false;
        }
        Map<UnitType, Integer> army = Map.of(UnitType.SCOUT, scouts);
        if (warfare.previewAttack(turn, target, army, null) == null) {
            return false;
        }
        warfare.send(turn, target, army, null);
        NpcWarfare.recordSent(turn, target);
        brain.setMemory(turn.memory.build());
        store.save(order.scouting());
        return true;
    }

    private Instant ownAttackArrival(Order order, Instant now) {
        return sheet.ownAttackArrival(order.playerId(), order.targetVillageId(), now);
    }

    /**
     * The planned "land together" march leaves (event {@code NPC_ORDER_MARCH}, in the event's
     * transaction): with exactly the units the lord weighed, which its village kept for it; if any
     * of them is gone, or the command can't be sent any more, it doesn't go and says so.
     */
    void march(long lordId, Instant now) {
        Order order = store.lock(lordId).orElse(null);
        if (order == null || order.state() != NpcOrderState.PLANNED) {
            return;
        }
        if (!guilds.mates(lordId).contains(order.playerId())
                || order.sourceVillageId() == null
                || order.army() == null) {
            store.delete(lordId);
            return;
        }
        Village source = locker.lock(order.sourceVillageId()).stream().findFirst().orElse(null);
        Village target = villages.findById(order.targetVillageId()).orElse(null);
        if (source == null
                || target == null
                || source.getPlayer() == null
                || source.getPlayer().getId() != lordId) {
            refuse(order, LordLetter.Kind.ORDER_TOO_STRONG, now, false);
            return;
        }
        stateService.catchUp(source, now);
        Map<UnitType, Integer> army =
                NpcOrderRules.stillHome(
                        order.army(), unitService.homeUnits(source.getId()).toMap());
        if (army == null) {
            refuse(order, LordLetter.Kind.ORDER_TOO_STRONG, now, false);
            return;
        }
        CommandRequest request =
                new CommandRequest(
                        CommandKind.ATTACK, target.coordinates(), army, order.catapultTarget());
        CommandPreviewDto preview = commands.preview(source.getId(), lordId, request);
        if (!preview.errors().isEmpty()) {
            refuse(
                    order,
                    preview.errors().contains(ErrorCode.PROTECTED_TARGET)
                            ? LordLetter.Kind.ORDER_PROTECTED
                            : LordLetter.Kind.ORDER_TOO_STRONG,
                    now,
                    false);
            return;
        }
        MovementDto sent = commands.send(source.getId(), lordId, request);
        store.save(
                order.carried(source.getId(), now, sent.arriveAt(), army, order.catapultTarget())
                        .answered(NpcOrderState.ACTIVE, order.answer(), order.answeredAt()));
        log.info("NPC lord {} left on its planned march to {}", lordId, target.getId());
    }

    // ---- support ----

    private void support(Order order, NpcTurn.View view, MapVillage target, Instant now) {
        Long owner = target.playerId();
        if (owner == null || owner == view.lordId() || !view.guildMates().contains(owner)) {
            dropIfUnchanged(order);
            return;
        }
        double share = NpcOrderRules.supportShare(view.template().supportShare(), settings);
        for (long villageId : candidates(view, target)) {
            boolean sent =
                    Boolean.TRUE.equals(
                            tx.execute(
                                    status ->
                                            supportFrom(
                                                    order, view, target, villageId, share, now)));
            if (sent) {
                return;
            }
        }
        refuse(order, LordLetter.Kind.ORDER_NO_TROOPS, now);
    }

    private boolean supportFrom(
            Order order,
            NpcTurn.View view,
            MapVillage target,
            long villageId,
            double share,
            Instant now) {
        requireUnchanged(order);
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcTurn turn = lockedTurn(view, villageId, now, brain);
        if (turn == null || threatened(turn)) {
            return false;
        }
        Map<UnitType, Integer> units =
                NpcTargeting.shareOf(turn.available, turn.template().supportUnits(), share);
        if (units.isEmpty()) {
            return false;
        }
        CommandRequest request =
                new CommandRequest(CommandKind.SUPPORT, target.coordinates(), units, null);
        CommandPreviewDto preview = commands.preview(villageId, view.lordId(), request);
        if (!preview.errors().isEmpty() || preview.arriveAt() == null) {
            return false;
        }
        MovementDto sent = commands.send(villageId, view.lordId(), request);
        store.save(
                order.carried(villageId, now, sent.arriveAt(), units, null)
                        .answered(NpcOrderState.ACTIVE, LordLetter.Kind.ORDER_SUPPORT, now));
        letter(
                order,
                new LordLetter(
                        LordLetter.Kind.ORDER_SUPPORT,
                        null,
                        ref(order),
                        units,
                        null,
                        sent.arriveAt(),
                        null));
        return true;
    }

    // ---- resources ----

    private void send(Order order, NpcTurn.View view, MapVillage target, Instant now) {
        if (!Objects.equals(target.playerId(), order.playerId())) {
            dropIfUnchanged(order);
            return;
        }
        boolean noRoom = false;
        for (long villageId : candidates(view, target)) {
            Shipment shipment = tx.execute(status -> sendFrom(order, view, target, villageId, now));
            if (shipment == Shipment.SENT) {
                return;
            }
            noRoom |= shipment == Shipment.NO_ROOM;
        }
        refuse(
                order,
                noRoom ? LordLetter.Kind.ORDER_NO_ROOM : LordLetter.Kind.ORDER_NO_SURPLUS,
                now);
    }

    /** What one village could do about a Send order. */
    private enum Shipment {
        SENT,
        /** It holds enough above its goal, but the human's warehouse has no room for it. */
        NO_ROOM,
        NOTHING
    }

    /**
     * One village's shipment: what it holds above its own next goal (the step its economy plans, 09
     * §5.1), up to the room in the human's warehouse and what its free merchants carry; at least
     * one full merchant.
     */
    private Shipment sendFrom(
            Order order, NpcTurn.View view, MapVillage target, long villageId, Instant now) {
        requireUnchanged(order);
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        NpcTurn turn = lockedTurn(view, villageId, now, brain);
        Village receiver = villages.findById(target.id()).orElse(null);
        if (turn == null || receiver == null) {
            return Shipment.NOTHING;
        }
        Village donor = turn.village;
        Integer wallGoal =
                view.world().npcLearningEnabled()
                        ? NpcLearning.wallGoal(turn.memory.lessons, villageId, now)
                        : null;
        ResourceAmounts goal =
                economy.nextCost(
                        donor,
                        view.lordId(),
                        now,
                        view.template(),
                        turn.memory.conquest.containsKey(villageId),
                        view.lesson(),
                        wallGoal);
        ResourceAmounts surplus = NpcOrderRules.surplus(donor.floorResources(), goal);
        int free = market.freeMerchants(donor, now);
        ResourceAmounts load =
                NpcShipments.load(
                        surplus,
                        shipments.roomIn(receiver, donor.coordinates(), view.world(), now),
                        free);
        if (load.total() == 0) {
            return NpcOrderRules.wouldSend(surplus, free) ? Shipment.NO_ROOM : Shipment.NOTHING;
        }
        TransportDto transport =
                market.send(
                        villageId,
                        view.lordId(),
                        new TransportRequest(
                                receiver.coordinates(), load.wood(), load.clay(), load.iron()));
        store.save(
                order.carried(villageId, now, transport.arriveAt(), null, null)
                        .answered(NpcOrderState.ACTIVE, LordLetter.Kind.ORDER_SEND, now));
        letter(
                order,
                new LordLetter(
                        LordLetter.Kind.ORDER_SEND,
                        null,
                        ref(order),
                        null,
                        load,
                        transport.arriveAt(),
                        null));
        return Shipment.SENT;
    }

    // ---- helpers ----

    /** The lord's villages nearest the target first (ties: the lower id), without the target. */
    private List<Long> candidates(NpcTurn.View view, MapVillage target) {
        return view.villages().values().stream()
                .filter(v -> Objects.equals(v.playerId(), view.lordId()) && v.id() != target.id())
                .sorted(
                        Comparator.comparingDouble(
                                        (MapVillage v) ->
                                                v.coordinates().distanceTo(target.coordinates()))
                                .thenComparingLong(MapVillage::id))
                .limit(settings.candidates())
                .map(MapVillage::id)
                .toList();
    }

    private Village unlocked(long villageId) {
        return villages.findById(villageId).orElseThrow();
    }

    /** A turn on an unlocked village, for what the lord knows (its intel and caps). */
    private NpcTurn turn(NpcTurn.View view, Village village, Instant now) {
        NpcBrain brain = brains.findById(view.lordId()).orElseThrow();
        return new NpcTurn(
                view,
                village,
                now,
                brain.getMemory().draft(),
                Set.of(village.getId()),
                new HashSet<>(world.ownAttackTargets(view.lordId())));
    }

    /**
     * The village locked and caught up, as a turn with its available units (home minus the home
     * guard, 09 §6); null if the lord doesn't own it any more.
     */
    private NpcTurn lockedTurn(NpcTurn.View view, long villageId, Instant now, NpcBrain brain) {
        Village village = locker.lock(villageId).stream().findFirst().orElse(null);
        if (village == null
                || village.getPlayer() == null
                || village.getPlayer().getId() != view.lordId()) {
            return null;
        }
        stateService.catchUp(village, now);
        NpcTurn turn =
                new NpcTurn(
                        view,
                        village,
                        now,
                        brain.getMemory().draft(),
                        Set.of(villageId),
                        new HashSet<>(world.ownAttackTargets(view.lordId())));
        turn.available =
                NpcTargeting.available(
                        unitService.homeUnits(villageId).toMap(), view.template().homeGuard());
        return turn;
    }

    /** A foreign attack (not only scouts) is due at the village: it keeps its units (09 §6.5). */
    private boolean threatened(NpcTurn turn) {
        double maxHaste = defence.maxHaste(turn);
        return turn.view.incoming().stream()
                .anyMatch(
                        a ->
                                a.targetVillageId() == turn.villageId()
                                        && NpcDefence.isThreat(turn, a, maxHaste));
    }

    private void refuse(Order order, LordLetter.Kind kind, Instant now) {
        refuse(order, kind, now, true);
    }

    /** The lord says no (or can't go after all): the order is refused, with a letter. */
    private void refuse(Order order, LordLetter.Kind kind, Instant now, boolean ownTransaction) {
        Runnable body =
                () -> {
                    requireUnchanged(order);
                    store.save(
                            order.carried(null, null, null, null, null)
                                    .answered(NpcOrderState.REFUSED, kind, now));
                    letter(order, LordLetter.order(kind, ref(order)));
                };
        if (ownTransaction) {
            tx.executeWithoutResult(status -> body.run());
        } else {
            body.run();
        }
        log.info("NPC lord {} answered an order with {}", order.lordId(), kind);
    }

    /**
     * The human replaced or took back the order while the lord weighed it: the lord's transaction
     * is rolled back and the new order waits for its next round.
     */
    private static final class OrderChanged extends RuntimeException {
        OrderChanged() {
            super(null, null, false, false);
        }
    }

    /** Locks the order row; throws {@link OrderChanged} if it isn't the order the lord read. */
    private void requireUnchanged(Order order) {
        if (!store.lockUnchanged(order)) {
            throw new OrderChanged();
        }
    }

    /** Drops an order that can't be carried out, unless the human changed it meanwhile. */
    private void dropIfUnchanged(Order order) {
        tx.executeWithoutResult(
                status -> {
                    if (store.lockUnchanged(order)) {
                        store.delete(order.lordId());
                    }
                });
    }

    private VillageRefDto ref(Order order) {
        return villages.findById(order.targetVillageId()).map(VillageRefDto::of).orElse(null);
    }

    private void letter(Order order, LordLetter letter) {
        messages.sendLetter(order.lordId(), order.playerId(), letter);
    }
}
