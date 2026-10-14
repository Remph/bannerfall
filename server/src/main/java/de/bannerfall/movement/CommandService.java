package de.bannerfall.movement;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.Coordinates;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerService;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.report.SupportReport;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.StackVillages;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sending troops (02-game-rules §6): preview and send a command, cancel it, withdraw or send back a
 * support. Every movement gets a MOVEMENT_ARRIVE event in the same transaction; {@link
 * MovementArriveHandler} handles the arrival.
 *
 * <p>Locks: a command and its cancel lock the units' home village only. The target is read without
 * a lock: nothing of it changes here, and its owner can only change through a conquest (M11).
 * Withdraw and send back lock both villages of the stack.
 *
 * <p>Notifications (after commit): the target's owner hears of an attack when it is sent
 * (INCOMING_ATTACK) and of a cancelled command (VILLAGE_UPDATED, his incoming list changed); a
 * withdraw or send back tells the owner of the other village (VILLAGE_UPDATED).
 */
@Service
public class CommandService {

    private final MovementRepository movements;
    private final UnitStackRepository stacks;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final VillageStateService stateService;
    private final UnitService unitService;
    private final PlayerService playerService;
    private final ReportService reportService;
    private final EventScheduler scheduler;
    private final GameData gameData;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final VillageBanners villageBanners;

    CommandService(
            MovementRepository movements,
            UnitStackRepository stacks,
            VillageRepository villages,
            VillageLocker locker,
            VillageStateService stateService,
            UnitService unitService,
            PlayerService playerService,
            ReportService reportService,
            EventScheduler scheduler,
            GameData gameData,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock,
            VillageBanners villageBanners) {
        this.movements = movements;
        this.stacks = stacks;
        this.villages = villages;
        this.locker = locker;
        this.stateService = stateService;
        this.unitService = unitService;
        this.playerService = playerService;
        this.reportService = reportService;
        this.scheduler = scheduler;
        this.gameData = gameData;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
        this.villageBanners = villageBanners;
    }

    /**
     * A command as {@link #send} would see it right now.
     *
     * @param home the live units at home (send takes the units from it)
     * @param banner what the command takes along from the source's banner (02-game-rules §21.2)
     * @param travelSeconds null if the command has no units
     * @param arriveAt null if the command has no units
     * @param errors every rule it breaks, see {@link CommandRules#check}
     */
    private record Plan(
            Village target,
            UnitCounts units,
            UnitCounts home,
            CommandBanner banner,
            double distance,
            Long travelSeconds,
            Instant arriveAt,
            List<ErrorCode> errors) {}

    /**
     * What the command would look like if it were sent now, with every rule it breaks. Locks the
     * village and catches it up (recruited units count), but changes nothing else.
     *
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     */
    @Transactional
    public CommandPreviewDto preview(long villageId, long playerId, CommandRequest request) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(source, now);
        Plan plan = plan(source, playerId, request, now);
        return new CommandPreviewDto(
                VillageRefDto.of(plan.target()),
                plan.distance(),
                plan.travelSeconds(),
                plan.arriveAt(),
                plan.errors());
    }

    /**
     * Sends an attack or a support (02-game-rules §6): the units leave the home stack now and
     * arrive after the travel time of the slowest one. An attack on a player's village (an NPC lord
     * included) ends the sender's own beginner protection; an attack on a barbarian village
     * doesn't, unless it carries nobles.
     *
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     * @throws GameRuleException the first error of {@link CommandRules#check}
     */
    @Transactional
    public MovementDto send(long villageId, long playerId, CommandRequest request) {
        SendAttempt attempt = trySend(villageId, playerId, request);
        if (attempt.movement() == null) {
            ErrorCode first = attempt.errors().getFirst();
            throw new GameRuleException(first, "The command can't be sent: " + first);
        }
        return attempt.movement();
    }

    /**
     * What {@link #trySend} did: the movement it sent, or the rules the command breaks.
     *
     * @param movement null if nothing was sent
     * @param errors empty if it was sent, see {@link CommandRules#check}
     */
    public record SendAttempt(MovementDto movement, List<ErrorCode> errors) {}

    /**
     * {@link #send}, but a command that breaks a rule is answered instead of thrown. An exception
     * marks the whole transaction for rollback, so a caller that sends several commands in one
     * transaction (the farm list's "send all", M39) needs this to go on after a refused one.
     *
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     */
    @Transactional
    public SendAttempt trySend(long villageId, long playerId, CommandRequest request) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(source, now);
        Plan plan = plan(source, playerId, request, now);
        if (!plan.errors().isEmpty()) {
            return new SendAttempt(null, plan.errors());
        }

        MovementKind kind = request.kind().toMovementKind();
        UnitCounts units = plan.units();
        Movement movement =
                depart(
                        kind,
                        source,
                        plan.target(),
                        playerId,
                        units,
                        plan.home(),
                        CommandRules.catapultTarget(kind, units, request.catapultTarget()),
                        plan.banner(),
                        now,
                        plan.arriveAt());
        if (kind == MovementKind.ATTACK) {
            attacked(source, plan.target(), playerId, units.get(UnitType.NOBLE) > 0, movement, now);
        }
        return new SendAttempt(
                MovementDto.of(movement, worldService.settings().cancelWindowSeconds()), List.of());
    }

    /**
     * What the attack train would look like if it were sent now (M37, 02-game-rules §6.1), with
     * every rule each attack breaks. Locks the village and catches it up, but changes nothing else.
     *
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     */
    @Transactional
    public TrainPreviewDto previewTrain(long villageId, long playerId, TrainRequest request) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(source, now);
        TrainPlan plan = planTrain(source, playerId, request, now);
        return new TrainPreviewDto(
                VillageRefDto.of(plan.target()),
                plan.distance(),
                plan.travelSeconds(),
                plan.arriveAt(),
                plan.errors().stream().map(TrainPreviewDto.TrainAttackPreviewDto::new).toList());
    }

    /**
     * Sends an attack train (M37, 02-game-rules §6.1): all its attacks or none. They leave now, all
     * at the pace of the slowest unit in the whole train, and arrive at the same moment in the
     * order of the request (the event worker runs events of the same time in id order, and the ids
     * are handed out in this order). Each one is an ordinary ATTACK from then on: it fights on its
     * own and can be cancelled on its own. Beginner protection ends as for a single attack; the
     * target's owner hears of the train once.
     *
     * @return the new movements, in the order of the request
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the target
     * @throws GameRuleException the first error of the first attack that breaks a rule
     */
    @Transactional
    public List<MovementDto> sendTrain(long villageId, long playerId, TrainRequest request) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        stateService.catchUp(source, now);
        TrainPlan plan = planTrain(source, playerId, request, now);
        for (int i = 0; i < plan.errors().size(); i++) {
            List<ErrorCode> errors = plan.errors().get(i);
            if (!errors.isEmpty()) {
                ErrorCode first = errors.getFirst();
                throw new GameRuleException(
                        first, "Attack " + (i + 1) + " of the train can't be sent: " + first);
            }
        }

        List<Movement> sent = new ArrayList<>();
        boolean nobles = false;
        for (int i = 0; i < plan.armies().size(); i++) {
            UnitCounts units = plan.armies().get(i);
            nobles |= units.get(UnitType.NOBLE) > 0;
            sent.add(
                    depart(
                            MovementKind.ATTACK,
                            source,
                            plan.target(),
                            playerId,
                            units,
                            plan.home(),
                            CommandRules.catapultTarget(
                                    MovementKind.ATTACK,
                                    units,
                                    request.attacks().get(i).catapultTarget()),
                            plan.banner(),
                            now,
                            plan.arriveAt()));
        }
        // one notification for the whole train: they all arrive at the same moment
        attacked(source, plan.target(), playerId, nobles, sent.getFirst(), now);
        int window = worldService.settings().cancelWindowSeconds();
        return sent.stream().map(movement -> MovementDto.of(movement, window)).toList();
    }

    /**
     * Cancels an attack or support within the cancel window (02-game-rules §6): it turns around and
     * becomes a RETURN that arrives home after as long as it has been on the way. Its event moves
     * along.
     *
     * @throws NotFoundException {@code MOVEMENT_NOT_FOUND} if the player has no such movement
     *     (never had, or it has arrived)
     * @throws GameRuleException {@code CANCEL_WINDOW_OVER} if the window is over, the movement has
     *     arrived (or arrives right now) or it is a RETURN
     */
    @Transactional
    public MovementDto cancel(long movementId, long playerId) {
        long homeId =
                movements
                        .findHomeVillageIdOfOwner(movementId, playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MOVEMENT_NOT_FOUND));
        locker.lock(homeId);
        // Read after the lock: the arrival may have deleted it meanwhile.
        Movement movement =
                movements
                        .findById(movementId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MOVEMENT_NOT_FOUND));
        Instant now = clock.instant();
        int window = worldService.settings().cancelWindowSeconds();
        if (!movement.isCancellableAt(now, window)) {
            throw new GameRuleException(
                    ErrorCode.CANCEL_WINDOW_OVER, "Movement " + movementId + " can't be cancelled");
        }
        // The worker holds the event only while it handles it, so false means: it arrives right
        // now (the worker's clock is a moment ahead of ours). Too late, and the handler must not
        // find a RETURN where it expects the command: roll back.
        if (!scheduler.reschedule(
                GameEventKind.MOVEMENT_ARRIVE,
                movementId,
                movement.returnArrivalIfCancelledAt(now))) {
            throw new GameRuleException(
                    ErrorCode.CANCEL_WINDOW_OVER, "Movement " + movementId + " arrives right now");
        }
        // read before the cancel: the RETURN's target is the home village
        Village target = movement.getTargetVillage();
        movement.cancel(now, window);
        PlayerNotification.toOwnerOf(
                target, new GameNotification.VillageUpdated(target.getId()), events);
        return MovementDto.of(movement, window);
    }

    /**
     * Sends units of a support stack home (02-game-rules §6): <i>withdraw</i> by the owner of the
     * units' home village, <i>send back</i> by the owner of the village they stand in. They walk as
     * a RETURN at the speed of the slowest one; the stack is deleted when it becomes empty. The
     * owner of the other village gets a SUPPORT_RETURNED report (nobody if it is the same player).
     *
     * @throws NotFoundException {@code STACK_NOT_FOUND} if there is no such support stack, or it is
     *     in none of the player's villages and from none of them
     * @throws GameRuleException {@code EMPTY_COMMAND}, {@code NOT_ENOUGH_UNITS} (more than the
     *     stack has)
     */
    @Transactional
    public MovementDto returnStack(long stackId, long playerId, Map<UnitType, Integer> requested) {
        StackVillages ids =
                stacks.findVillagesById(stackId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.STACK_NOT_FOUND));
        locker.lock(ids.homeVillageId(), ids.locationVillageId());
        // Read after the locks: an arriving support may have changed it meanwhile.
        UnitStack stack = stacks.findById(stackId).orElse(null);
        if (stack == null
                || stack.isHome()
                || !(ownedBy(stack.getHomeVillage(), playerId)
                        || ownedBy(stack.getLocationVillage(), playerId))) {
            throw new NotFoundException(ErrorCode.STACK_NOT_FOUND);
        }
        Village home = stack.getHomeVillage();
        Village location = stack.getLocationVillage();
        Instant now = clock.instant();
        stateService.catchUp(home, now);
        stateService.catchUp(location, now);

        UnitCounts units = UnitCounts.of(requested);
        if (units.isEmpty()) {
            throw new GameRuleException(ErrorCode.EMPTY_COMMAND, "No units to send home");
        }
        if (!units.fitsInto(stack.getUnits())) {
            throw new GameRuleException(
                    ErrorCode.NOT_ENOUGH_UNITS, "Stack " + stackId + " doesn't have these units");
        }
        units.forEach((type, count) -> stack.getUnits().add(type, -count));
        if (stack.getUnits().isEmpty()) {
            stacks.delete(stack);
        }

        WorldSettings world = worldService.settings();
        // A stack that stood as a support walks home like a support: with a paladin at his speed.
        // No Haste banner (02-game-rules §21.2): that is for commands sent from a village, and the
        // trip that brought the stack here ended when it arrived.
        long seconds =
                Travel.supportTravelSeconds(
                        gameData, world, location.coordinates(), home.coordinates(), units.toMap());
        Movement movement =
                movements.save(
                        Movement.returning(
                                home,
                                location,
                                home.getPlayer().getId(),
                                units,
                                ResourceAmounts.ZERO,
                                now,
                                now.plusSeconds(seconds),
                                now));
        scheduler.schedule(GameEventKind.MOVEMENT_ARRIVE, movement.getId(), movement.getArriveAt());

        Village otherVillage = ownedBy(home, playerId) ? location : home;
        Player other = otherVillage.getPlayer();
        if (other != null && other.getId() != playerId) {
            events.publishEvent(
                    new PlayerNotification(
                            other.getId(),
                            new GameNotification.VillageUpdated(otherVillage.getId())));
            reportService.create(
                    ReportKind.SUPPORT_RETURNED,
                    now,
                    new SupportReport(
                            VillageRefDto.of(home), VillageRefDto.of(location), units.toMap()),
                    other.getId());
        }
        return MovementDto.of(movement, world.cancelWindowSeconds());
    }

    /** The source is locked and caught up. */
    private Plan plan(Village source, long playerId, CommandRequest request, Instant now) {
        WorldSettings world = worldService.settings();
        Coordinates at = request.target();
        Village target =
                villages.findByXAndY(at.x(), at.y())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.TARGET_NOT_FOUND));
        MovementKind kind = request.kind().toMovementKind();
        UnitCounts units = UnitCounts.of(request.units());
        double distance = Travel.distance(source.coordinates(), target.coordinates());
        // War, Plunder and Haste as the source's banner gives them right now (02-game-rules §21.2)
        CommandBanner banner = CommandBanner.of(kind, villageBanners.effectOf(source));

        Long travelSeconds = null;
        Instant arriveAt = null;
        if (!units.isEmpty()) {
            travelSeconds =
                    Travel.withHaste(
                            Travel.travelSeconds(
                                    distance,
                                    kind == MovementKind.SUPPORT
                                            ? Travel.supportMinutesPerField(gameData, units.toMap())
                                            : Travel.armyMinutesPerField(gameData, units.toMap()),
                                    world),
                            banner.haste());
            arriveAt = now.plusSeconds(travelSeconds);
        }
        Player owner = target.getPlayer();
        UnitCounts home = unitService.homeUnits(source.getId());
        List<ErrorCode> errors =
                CommandRules.check(
                        kind,
                        units,
                        home,
                        target.getId().equals(source.getId()),
                        ownedBy(target, playerId),
                        distance,
                        owner == null ? null : owner.getProtectedUntil(),
                        arriveAt,
                        world.nobleMaxDistance());
        return new Plan(target, units, home, banner, distance, travelSeconds, arriveAt, errors);
    }

    /**
     * The units leave the home stack and walk: the movement and its MOVEMENT_ARRIVE event.
     *
     * @param home the live home units, {@code units} are taken from it
     */
    private Movement depart(
            MovementKind kind,
            Village source,
            Village target,
            long playerId,
            UnitCounts units,
            UnitCounts home,
            BuildingType catapultTarget,
            CommandBanner banner,
            Instant now,
            Instant arriveAt) {
        units.forEach((type, count) -> home.add(type, -count));
        Movement movement =
                movements.save(
                        Movement.command(
                                kind,
                                source,
                                target,
                                playerId,
                                units,
                                catapultTarget,
                                banner,
                                now,
                                arriveAt));
        scheduler.schedule(GameEventKind.MOVEMENT_ARRIVE, movement.getId(), movement.getArriveAt());
        return movement;
    }

    /**
     * After an attack (or a train) left: the sender's protection and the target owner's
     * notification.
     *
     * @param nobles the attack, or any attack of the train, carries nobles
     * @param movement the attack whose arrival the target's owner hears of
     */
    private void attacked(
            Village source,
            Village target,
            long playerId,
            boolean nobles,
            Movement movement,
            Instant now) {
        // Decided by the owner at send time. Barbarians are free to hit, or protected players
        // couldn't farm them without giving up the protection they are there to use for it.
        // Nobles still end it: a conquered barbarian village would otherwise belong to a
        // protected player while attacks sent at it as a barbarian village are on the way
        // (PROTECTED_TARGET is checked at send time only).
        if (target.getPlayer() != null || nobles) {
            playerService.endProtection(playerId, now);
        }
        VillageRefDto sourceRef = VillageRefDto.of(source);
        PlayerNotification.toOwnerOf(
                target,
                new GameNotification.IncomingAttack(
                        target.getId(), sourceRef, sourceRef.playerName(), movement.getArriveAt()),
                events);
    }

    /**
     * An attack train as {@link #sendTrain} would see it right now.
     *
     * @param armies the units of each attack, in the request's order
     * @param home the live units at home (send takes the units from it)
     * @param travelSeconds of the whole train; null if no attack has units
     * @param arriveAt of every attack; null like {@code travelSeconds}
     * @param errors per attack, see {@link CommandRules#check}
     */
    private record TrainPlan(
            Village target,
            List<UnitCounts> armies,
            UnitCounts home,
            CommandBanner banner,
            double distance,
            Long travelSeconds,
            Instant arriveAt,
            List<List<ErrorCode>> errors) {}

    /** The source is locked and caught up. */
    private TrainPlan planTrain(Village source, long playerId, TrainRequest request, Instant now) {
        WorldSettings world = worldService.settings();
        Coordinates at = request.target();
        Village target =
                villages.findByXAndY(at.x(), at.y())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.TARGET_NOT_FOUND));
        double distance = Travel.distance(source.coordinates(), target.coordinates());
        CommandBanner banner =
                CommandBanner.of(MovementKind.ATTACK, villageBanners.effectOf(source));
        List<UnitCounts> armies =
                request.attacks().stream().map(attack -> UnitCounts.of(attack.units())).toList();

        // the whole train walks at the pace of its slowest unit, so it arrives together
        UnitCounts all = new UnitCounts();
        for (UnitCounts army : armies) {
            all = all.plus(army);
        }
        Long travelSeconds = null;
        Instant arriveAt = null;
        if (!all.isEmpty()) {
            travelSeconds =
                    Travel.withHaste(
                            Travel.travelSeconds(
                                    distance,
                                    Travel.armyMinutesPerField(gameData, all.toMap()),
                                    world),
                            banner.haste());
            arriveAt = now.plusSeconds(travelSeconds);
        }

        Player owner = target.getPlayer();
        UnitCounts home = unitService.homeUnits(source.getId());
        // each attack is checked against what the ones before it leave at home
        UnitCounts left = home.copy();
        List<List<ErrorCode>> errors = new ArrayList<>();
        for (UnitCounts army : armies) {
            errors.add(
                    CommandRules.check(
                            MovementKind.ATTACK,
                            army,
                            left,
                            target.getId().equals(source.getId()),
                            ownedBy(target, playerId),
                            distance,
                            owner == null ? null : owner.getProtectedUntil(),
                            arriveAt,
                            world.nobleMaxDistance()));
            if (army.fitsInto(left)) {
                left = left.minus(army);
            }
        }
        return new TrainPlan(
                target, armies, home, banner, distance, travelSeconds, arriveAt, errors);
    }

    /** False for barbarian villages. Reading the id doesn't load the player. */
    private static boolean ownedBy(Village village, long playerId) {
        Player owner = village.getPlayer();
        return owner != null && owner.getId() == playerId;
    }
}
