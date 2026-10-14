package de.bannerfall.farm;

import de.bannerfall.banner.VillageBanners;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.Coordinates;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.player.Player;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The farm helper (02-game-rules §18): a list of targets per village, each with the units the
 * player usually sends, a one-tap "send" for a single target and "send all" for the list (M39). The
 * helper never sends by itself: every attack follows a tap and is checked and sent by {@link
 * CommandService} like any other.
 */
@Service
public class FarmService {

    /** The most targets one village's list holds. */
    public static final int MAX_TARGETS = 50;

    private final FarmTargetRepository targets;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final CommandService commandService;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;
    private final VillageBanners villageBanners;

    FarmService(
            FarmTargetRepository targets,
            VillageRepository villages,
            VillageLocker locker,
            CommandService commandService,
            GameData gameData,
            WorldService worldService,
            Clock clock,
            VillageBanners villageBanners) {
        this.targets = targets;
        this.villages = villages;
        this.locker = locker;
        this.commandService = commandService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
        this.villageBanners = villageBanners;
    }

    /**
     * The village's list, in the order the targets were added.
     *
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} if the village isn't the player's
     */
    @Transactional
    public List<FarmTargetDto> list(long villageId, long playerId) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<FarmTarget> entries = targets.findByVillageIdOrderByIdAsc(villageId);
        if (entries.isEmpty()) {
            return List.of();
        }
        // the targets and their owners in one query: the list is read after every tap
        Map<Long, Village> byId = new HashMap<>();
        for (Village target :
                villages.findAllWithPlayer(
                        entries.stream().map(FarmTarget::getTargetVillageId).toList())) {
            byId.put(target.getId(), target);
        }
        double haste = villageBanners.effectOf(source).haste(); // one lookup for the list
        return entries.stream()
                .map(
                        entry ->
                                toDto(
                                        source,
                                        entry,
                                        byId.get(entry.getTargetVillageId()),
                                        haste,
                                        now))
                .toList();
    }

    /**
     * Adds a target with its template.
     *
     * @throws ApiException 400 {@code VALIDATION} for a template that is empty, has nobles or a
     *     count outside 0 … 30 000
     * @throws NotFoundException {@code TARGET_NOT_FOUND} if there is no village at the coordinates
     * @throws GameRuleException {@code SAME_VILLAGE} / {@code OWN_VILLAGE} for the player's own
     *     villages, {@code ALREADY_IN_FARM_LIST}, {@code FARM_LIST_FULL}
     */
    @Transactional
    public FarmTargetDto add(long villageId, long playerId, FarmTargetRequest request) {
        UnitCounts template = checkedTemplate(request.units());
        Village source = locker.lockOwned(villageId, playerId);
        Coordinates at = request.target();
        Village target =
                villages.findByXAndY(at.x(), at.y())
                        .orElseThrow(() -> new NotFoundException(ErrorCode.TARGET_NOT_FOUND));
        if (target.getId().equals(source.getId())) {
            throw new GameRuleException(ErrorCode.SAME_VILLAGE, "That is this village");
        }
        Player owner = target.getPlayer();
        if (owner != null && owner.getId() == playerId) {
            throw new GameRuleException(ErrorCode.OWN_VILLAGE, "That is one of your villages");
        }
        if (targets.existsByVillageIdAndTargetVillageId(villageId, target.getId())) {
            throw new GameRuleException(
                    ErrorCode.ALREADY_IN_FARM_LIST, "That village is on the list already");
        }
        if (targets.countByVillageId(villageId) >= MAX_TARGETS) {
            throw new GameRuleException(
                    ErrorCode.FARM_LIST_FULL,
                    "A farm list has at most " + MAX_TARGETS + " targets");
        }
        Instant now = clock.instant();
        FarmTarget entry = targets.save(new FarmTarget(villageId, target.getId(), template, now));
        return toDto(source, entry, target, villageBanners.effectOf(source).haste(), now);
    }

    /**
     * Replaces the template of a target.
     *
     * @throws ApiException 400 {@code VALIDATION} as for {@link #add}
     * @throws NotFoundException {@code FARM_TARGET_NOT_FOUND}
     */
    @Transactional
    public FarmTargetDto changeUnits(
            long villageId, long targetId, long playerId, Map<UnitType, Integer> units) {
        UnitCounts template = checkedTemplate(units);
        Village source = locker.lockOwned(villageId, playerId);
        FarmTarget entry = find(villageId, targetId);
        entry.setUnits(template);
        Village target = villages.findById(entry.getTargetVillageId()).orElseThrow();
        return toDto(
                source, entry, target, villageBanners.effectOf(source).haste(), clock.instant());
    }

    /**
     * Takes a target off the list.
     *
     * @throws NotFoundException {@code FARM_TARGET_NOT_FOUND}
     */
    @Transactional
    public void remove(long villageId, long targetId, long playerId) {
        locker.lockOwned(villageId, playerId);
        targets.delete(find(villageId, targetId));
    }

    /**
     * Sends the target's template as an attack, exactly like {@code POST …/commands} would: the
     * answer is that command's, and so are its errors ({@code NOT_ENOUGH_UNITS}, {@code
     * PROTECTED_TARGET}, {@code OWN_VILLAGE} if the target has been conquered by the player since
     * ...). Nothing is sent partly.
     *
     * @throws NotFoundException {@code FARM_TARGET_NOT_FOUND}
     */
    @Transactional
    public MovementDto send(long villageId, long targetId, long playerId) {
        locker.lockOwned(villageId, playerId);
        FarmTarget entry = find(villageId, targetId);
        Village target = villages.findById(entry.getTargetVillageId()).orElseThrow();
        MovementDto movement =
                commandService.send(
                        villageId,
                        playerId,
                        new CommandRequest(
                                CommandKind.ATTACK,
                                target.coordinates(),
                                entry.getUnits().toMap(),
                                null));
        entry.sentAt(clock.instant());
        return movement;
    }

    /**
     * "Send all" (M39): works through the list from the top, sending each entry's template as
     * {@link #send} would. An entry whose target can't be hit now (beginner protection at arrival,
     * the player's own village by now) is skipped, and so is a player's village while the sender's
     * own beginner protection lasts: one tap for the whole list must not end it. The run stops at
     * the first entry the units at home don't cover, or at the end of the list. Each attack that
     * went out stays sent; the whole run holds the village's lock, so nothing else takes units from
     * it in between.
     */
    @Transactional
    public FarmSendAllDto sendAll(long villageId, long playerId) {
        Village source = locker.lockOwned(villageId, playerId);
        Instant now = clock.instant();
        List<FarmTarget> entries = targets.findByVillageIdOrderByIdAsc(villageId);
        Map<Long, Village> byId = new HashMap<>();
        for (Village target :
                villages.findAllWithPlayer(
                        entries.stream().map(FarmTarget::getTargetVillageId).toList())) {
            byId.put(target.getId(), target);
        }
        Instant ownProtection = source.getPlayer().getProtectedUntil();
        boolean senderProtected = ownProtection != null && ownProtection.isAfter(now);

        List<Long> sent = new ArrayList<>();
        List<FarmSendAllDto.Skipped> skipped = new ArrayList<>();
        for (FarmTarget entry : entries) {
            Village target = byId.get(entry.getTargetVillageId());
            Player owner = target.getPlayer();
            if (senderProtected && owner != null && owner.getId() != playerId) {
                skipped.add(
                        new FarmSendAllDto.Skipped(
                                entry.getId(), FarmSendAllDto.SkipReason.ENDS_OWN_PROTECTION));
                continue;
            }
            CommandService.SendAttempt attempt =
                    commandService.trySend(
                            villageId,
                            playerId,
                            new CommandRequest(
                                    CommandKind.ATTACK,
                                    target.coordinates(),
                                    entry.getUnits().toMap(),
                                    null));
            if (attempt.movement() != null) {
                entry.sentAt(clock.instant());
                sent.add(entry.getId());
                continue;
            }
            List<ErrorCode> errors = attempt.errors();
            // a target that can't be hit is skipped even when the units are short as well:
            // the units decide only for an entry that could be sent
            if (errors.contains(ErrorCode.OWN_VILLAGE) || errors.contains(ErrorCode.SAME_VILLAGE)) {
                skipped.add(
                        new FarmSendAllDto.Skipped(
                                entry.getId(), FarmSendAllDto.SkipReason.OWN_VILLAGE));
            } else if (errors.contains(ErrorCode.PROTECTED_TARGET)) {
                skipped.add(
                        new FarmSendAllDto.Skipped(
                                entry.getId(), FarmSendAllDto.SkipReason.PROTECTED_TARGET));
            } else if (errors.contains(ErrorCode.NOT_ENOUGH_UNITS)) {
                return new FarmSendAllDto(
                        sent, skipped, FarmSendAllDto.Stop.NOT_ENOUGH_UNITS, entry.getId());
            } else {
                // a template can't break any other rule (no nobles, never empty, ...): a bug
                throw new IllegalStateException(
                        "Farm entry " + entry.getId() + " can't be sent: " + errors);
            }
        }
        return new FarmSendAllDto(sent, skipped, FarmSendAllDto.Stop.DONE, null);
    }

    private FarmTarget find(long villageId, long targetId) {
        return targets.findByIdAndVillageId(targetId, villageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.FARM_TARGET_NOT_FOUND));
    }

    /**
     * The template as counts: 0 … 30 000 per type, at least one unit, no nobles, no paladin and no
     * banner thieves (a noble is never part of "the usual troops": it needs a train and a decision
     * each time; the paladin is one hero who should not be sent out by a one-tap button; thieves
     * are an expensive bet on one village's banner, M32b).
     */
    private static UnitCounts checkedTemplate(Map<UnitType, Integer> units) {
        UnitCounts counts = new UnitCounts();
        for (Map.Entry<UnitType, Integer> entry : units.entrySet()) {
            int count = entry.getValue();
            if (count < 0 || count > RecruitRules.MAX_COUNT) {
                throw invalid("Each count is 0 … " + RecruitRules.MAX_COUNT);
            }
            counts.set(entry.getKey(), count);
        }
        if (counts.isEmpty()) {
            throw invalid("The template needs at least one unit");
        }
        if (counts.get(UnitType.NOBLE) > 0) {
            throw invalid("Nobles can't be part of a farm template");
        }
        if (counts.get(UnitType.PALADIN) > 0) {
            throw invalid("The paladin can't be part of a farm template");
        }
        if (counts.get(UnitType.BANNER_THIEF) > 0) {
            throw invalid("Banner thieves can't be part of a farm template");
        }
        return counts;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, detail);
    }

    /**
     * @param haste the source's Haste banner (02-game-rules §21.2), so the trip shown is the one
     *     the attack will take
     */
    private FarmTargetDto toDto(
            Village source, FarmTarget entry, Village target, double haste, Instant now) {
        UnitCounts units = entry.getUnits();
        double distance = Travel.distance(source.coordinates(), target.coordinates());
        long travelSeconds =
                Travel.withHaste(
                        Travel.travelSeconds(
                                distance,
                                Travel.armyMinutesPerField(gameData, units.toMap()),
                                worldService.settings()),
                        haste);
        Player owner = target.getPlayer();
        Instant protectedUntil = owner == null ? null : owner.getProtectedUntil();
        return new FarmTargetDto(
                entry.getId(),
                VillageRefDto.of(target),
                units.toMap(),
                distance,
                travelSeconds,
                protectedUntil != null && protectedUntil.isAfter(now) ? protectedUntil : null,
                entry.getLastSentAt());
    }
}
