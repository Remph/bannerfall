package de.bannerfall.farm;

import de.bannerfall.banner.BannerBattleService;
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
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.movement.ReturnWatchService;
import de.bannerfall.note.VillageNoteService;
import de.bannerfall.player.Player;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.Loot;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final FarmTemplateRepository templates;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final CommandService commandService;
    private final GameData gameData;
    private final WorldService worldService;
    private final Clock clock;
    private final VillageBanners villageBanners;
    private final BannerBattleService bannerBattles;
    private final MovementRepository movements;
    private final VillageNoteService villageNotes;
    private final ReturnWatchService returnWatches;

    FarmService(
            FarmTargetRepository targets,
            FarmTemplateRepository templates,
            VillageRepository villages,
            VillageLocker locker,
            CommandService commandService,
            GameData gameData,
            WorldService worldService,
            Clock clock,
            VillageBanners villageBanners,
            BannerBattleService bannerBattles,
            MovementRepository movements,
            VillageNoteService villageNotes,
            ReturnWatchService returnWatches) {
        this.targets = targets;
        this.templates = templates;
        this.villages = villages;
        this.locker = locker;
        this.commandService = commandService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.clock = clock;
        this.villageBanners = villageBanners;
        this.bannerBattles = bannerBattles;
        this.movements = movements;
        this.villageNotes = villageNotes;
        this.returnWatches = returnWatches;
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
        BannerEffect banner = villageBanners.effectOf(source); // one lookup for the list
        BannerChances chances = new BannerChances(playerId, byId.values(), now); // and for all
        Map<Long, FarmTemplateLetter> letters = letters(playerId);
        Map<Long, String> notes = villageNotes.notesOn(playerId, byId.keySet());
        return entries.stream()
                .map(
                        entry ->
                                toDto(
                                        source,
                                        entry,
                                        byId.get(entry.getTargetVillageId()),
                                        banner,
                                        chances,
                                        letters,
                                        notes,
                                        now))
                .toList();
    }

    /**
     * Adds a target with what it is sent: one of the player's templates or its own units (1.4).
     *
     * @throws ApiException 400 {@code VALIDATION} for both or neither of units and template, own
     *     units that are empty, have nobles or a count outside 0 … 30 000
     * @throws NotFoundException {@code FARM_TEMPLATE_NOT_FOUND} for a template the player hasn't
     *     made, {@code TARGET_NOT_FOUND} if there is no village at the coordinates
     * @throws GameRuleException {@code SAME_VILLAGE} / {@code OWN_VILLAGE} for the player's own
     *     villages, {@code ALREADY_IN_FARM_LIST}, {@code FARM_LIST_FULL}
     */
    @Transactional
    public FarmTargetDto add(long villageId, long playerId, FarmTargetRequest request) {
        checkChoice(request.units(), request.template());
        Village source = locker.lockOwned(villageId, playerId);
        // after the lock: a template change holds it too, so the copy can't be outdated
        Troops troops = troopsOf(playerId, request.units(), request.template());
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
        FarmTarget entry =
                targets.save(
                        new FarmTarget(
                                villageId,
                                target.getId(),
                                troops.units(),
                                troops.templateId(),
                                now));
        return toDto(
                source,
                entry,
                target,
                villageBanners.effectOf(source),
                new BannerChances(playerId, List.of(target), now),
                letters(playerId),
                villageNotes.notesOn(playerId, List.of(target.getId())),
                now);
    }

    /**
     * Copies the village's list to another village of the player (1.6.1): the entries that the
     * other list lacks are appended in the same order with the same template or own units, until
     * the other list is full. Nothing of the other list changes, and the source stays as it is.
     *
     * @throws GameRuleException {@code SAME_VILLAGE} when both are the same village
     * @throws NotFoundException {@code VILLAGE_NOT_FOUND} if one isn't the player's
     */
    @Transactional
    public FarmCopyDto copy(long villageId, long playerId, long toVillageId) {
        if (villageId == toVillageId) {
            throw new GameRuleException(ErrorCode.SAME_VILLAGE, "That is this village");
        }
        // both are locked in ascending id order (01-architecture §4.5)
        locker.lockOwned(Math.min(villageId, toVillageId), playerId);
        locker.lockOwned(Math.max(villageId, toVillageId), playerId);
        List<FarmTarget> entries = targets.findByVillageIdOrderByIdAsc(villageId);
        Set<Long> there = new HashSet<>();
        for (FarmTarget entry : targets.findByVillageIdOrderByIdAsc(toVillageId)) {
            there.add(entry.getTargetVillageId());
        }
        Map<Long, Village> byId = new HashMap<>();
        for (Village target :
                villages.findAllWithPlayer(
                        entries.stream().map(FarmTarget::getTargetVillageId).toList())) {
            byId.put(target.getId(), target);
        }
        Instant now = clock.instant();
        int copied = 0;
        int alreadyThere = 0;
        int ownVillage = 0;
        int listFull = 0;
        for (FarmTarget entry : entries) {
            Village target = byId.get(entry.getTargetVillageId());
            Player owner = target == null ? null : target.getPlayer();
            if (entry.getTargetVillageId() == toVillageId
                    || owner != null && owner.getId() == playerId) {
                ownVillage++;
            } else if (there.contains(entry.getTargetVillageId())) {
                alreadyThere++;
            } else if (there.size() >= MAX_TARGETS) {
                listFull++;
            } else {
                targets.save(
                        new FarmTarget(
                                toVillageId,
                                entry.getTargetVillageId(),
                                entry.getUnits().copy(),
                                entry.getTemplateId(),
                                now));
                there.add(entry.getTargetVillageId());
                copied++;
            }
        }
        return new FarmCopyDto(copied, alreadyThere, ownVillage, listFull);
    }

    /**
     * Changes what a target is sent: another template or own units.
     *
     * @throws ApiException 400 {@code VALIDATION} as for {@link #add}
     * @throws NotFoundException {@code FARM_TARGET_NOT_FOUND}, {@code FARM_TEMPLATE_NOT_FOUND}
     */
    @Transactional
    public FarmTargetDto changeUnits(
            long villageId, long targetId, long playerId, FarmUnitsRequest request) {
        checkChoice(request.units(), request.template());
        Village source = locker.lockOwned(villageId, playerId);
        FarmTarget entry = find(villageId, targetId);
        Troops troops = troopsOf(playerId, request.units(), request.template());
        entry.setUnits(troops.units(), troops.templateId());
        Village target = villages.findById(entry.getTargetVillageId()).orElseThrow();
        Instant now = clock.instant();
        return toDto(
                source,
                entry,
                target,
                villageBanners.effectOf(source),
                new BannerChances(playerId, List.of(target), now),
                letters(playerId),
                villageNotes.notesOn(playerId, List.of(target.getId())),
                now);
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

    /** The player's templates (1.4), A to E as far as he has made them. */
    @Transactional(readOnly = true)
    public List<FarmTemplateDto> templates(long playerId) {
        return templates.findByPlayerIdOrderByLetterAsc(playerId).stream()
                .map(FarmTemplateDto::of)
                .toList();
    }

    /**
     * Makes or changes one of the player's templates (1.4). Every entry that uses it, in any of his
     * villages, sends the new units from now on: the change holds the locks of all his villages, so
     * no list is read or sent halfway.
     *
     * @throws ApiException 400 {@code VALIDATION} as for own units in {@link #add}
     */
    @Transactional
    public FarmTemplateDto saveTemplate(
            long playerId, FarmTemplateLetter letter, Map<UnitType, Integer> units) {
        UnitCounts counts = checkedTemplate(units);
        lockAllVillages(playerId);
        FarmTemplate template = templates.findByPlayerIdAndLetter(playerId, letter).orElse(null);
        if (template == null) {
            template = templates.save(new FarmTemplate(playerId, letter, counts));
        } else {
            template.setUnits(counts);
            for (FarmTarget entry : targets.findByTemplateId(template.getId())) {
                entry.followTemplate(counts);
            }
        }
        return FarmTemplateDto.of(template);
    }

    /**
     * Deletes one of the player's templates (1.4). Its entries keep the units they have as their
     * own.
     *
     * @throws NotFoundException {@code FARM_TEMPLATE_NOT_FOUND}
     */
    @Transactional
    public void deleteTemplate(long playerId, FarmTemplateLetter letter) {
        lockAllVillages(playerId);
        FarmTemplate template =
                templates
                        .findByPlayerIdAndLetter(playerId, letter)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.FARM_TEMPLATE_NOT_FOUND));
        for (FarmTarget entry : targets.findByTemplateId(template.getId())) {
            entry.setUnits(entry.getUnits(), null);
        }
        templates.delete(template);
    }

    private void lockAllVillages(long playerId) {
        locker.lock(villages.findIdsByPlayerId(playerId).toArray(Long[]::new));
    }

    /** The letters of the player's templates by id, for the entries' {@code template}. */
    private Map<Long, FarmTemplateLetter> letters(long playerId) {
        Map<Long, FarmTemplateLetter> letters = new HashMap<>();
        for (FarmTemplate template : templates.findByPlayerIdOrderByLetterAsc(playerId)) {
            letters.put(template.getId(), template.getLetter());
        }
        return letters;
    }

    /** What an entry sends: a copy of the units, and the template they come from (or null). */
    private record Troops(UnitCounts units, Long templateId) {}

    /** Exactly one of own units and a template (1.4); own units must be a valid template. */
    private static void checkChoice(Map<UnitType, Integer> units, FarmTemplateLetter letter) {
        if ((units == null) == (letter == null)) {
            throw invalid("Either units or a template");
        }
        if (units != null) {
            checkedTemplate(units);
        }
    }

    private Troops troopsOf(
            long playerId, Map<UnitType, Integer> units, FarmTemplateLetter letter) {
        if (letter == null) {
            return new Troops(checkedTemplate(units), null);
        }
        FarmTemplate template =
                templates
                        .findByPlayerIdAndLetter(playerId, letter)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.FARM_TEMPLATE_NOT_FOUND));
        return new Troops(template.getUnits().copy(), template.getId());
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
     * own beginner protection lasts: one tap for the whole list must not end it. An entry the units
     * at home don't cover is skipped too (1.3; before, the run stopped there), so a later entry
     * with other units still goes out; the run always ends at the end of the list. Each attack that
     * went out stays sent; the whole run holds the village's lock, so nothing else takes units from
     * it in between.
     *
     * <p>With {@code bannerChanceOnly} (1.2.3) the run sends only the entries with a banner chance
     * ({@link FarmTargetDto#bannerChance()}): the others are neither sent nor skipped. Those are
     * entries whose roll is used, whose pair already has an attack on the way (sent earlier or by
     * this run), or whose template can't win one.
     *
     * <p>With {@code notifyReturn} (1.6) the player is told once when every attack of the run is
     * home ({@link ReturnWatchService}).
     *
     * @throws GameRuleException {@code BANNERS_DISABLED} for {@code bannerChanceOnly} in a world
     *     without banners or drops
     */
    @Transactional
    public FarmSendAllDto sendAll(
            long villageId, long playerId, boolean bannerChanceOnly, boolean notifyReturn) {
        Village source = locker.lockOwned(villageId, playerId);
        if (bannerChanceOnly) {
            if (!bannerDrops(worldService.settings())) {
                throw new GameRuleException(
                        ErrorCode.BANNERS_DISABLED, "This world has no banner drops");
            }
        }
        Instant now = clock.instant();
        List<FarmTarget> entries = targets.findByVillageIdOrderByIdAsc(villageId);
        Map<Long, Village> byId = new HashMap<>();
        for (Village target :
                villages.findAllWithPlayer(
                        entries.stream().map(FarmTarget::getTargetVillageId).toList())) {
            byId.put(target.getId(), target);
        }
        BannerChances chances =
                bannerChanceOnly ? new BannerChances(playerId, byId.values(), now) : null;
        Instant ownProtection = source.getPlayer().getProtectedUntil();
        boolean senderProtected = ownProtection != null && ownProtection.isAfter(now);

        List<Long> sent = new ArrayList<>();
        List<Long> attacks = new ArrayList<>();
        List<FarmSendAllDto.Skipped> skipped = new ArrayList<>();
        for (FarmTarget entry : entries) {
            Village target = byId.get(entry.getTargetVillageId());
            if (chances != null && !chances.has(entry, target)) {
                continue;
            }
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
                attacks.add(attempt.movement().id());
                if (chances != null) {
                    chances.claim(target);
                }
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
                skipped.add(
                        new FarmSendAllDto.Skipped(
                                entry.getId(), FarmSendAllDto.SkipReason.NOT_ENOUGH_UNITS));
            } else {
                // a template can't break any other rule (no nobles, never empty, ...): a bug
                throw new IllegalStateException(
                        "Farm entry " + entry.getId() + " can't be sent: " + errors);
            }
        }
        if (notifyReturn) {
            returnWatches.watchFarmRun(playerId, source, attacks, now);
        }
        return new FarmSendAllDto(sent, skipped);
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

    /** Whether banners can drop in the world at all (02-game-rules §21.7, §21.9). */
    private static boolean bannerDrops(WorldSettings world) {
        return world.bannersEnabled() && world.bannerDropFactor() > 0;
    }

    /**
     * The player's banner chances against the targets of one answer (02-game-rules §21.7), per pair
     * as in {@link BannerBattleService.Loser} (per player for a player's or NPC lord's village, per
     * barbarian village): when a used roll comes back, and the pairs that are taken already. A pair
     * is taken by one of the player's attacks on the way that can win a roll (it uses the roll when
     * it lands, so a second attack has no chance, 1.2.3) and by an entry this answer gave the
     * chance to ({@link #claim}), so only the first entry of a pair has it.
     */
    private final class BannerChances {

        private final Map<String, Instant> rolls;
        private final Set<String> taken = new HashSet<>();

        BannerChances(long playerId, Collection<Village> targets, Instant now) {
            rolls =
                    bannerBattles.nextRolls(
                            playerId, targets.stream().map(BannerChances::key).toList(), now);
            for (Movement attack : movements.findAttacksUnderWay(playerId)) {
                BannerBattleService.Loser loser =
                        BannerBattleService.Loser.defenderOf(attack.getTargetVillage());
                if (bannerBattles.attackCanRoll(loser, attack.getUnits().toMap())) {
                    taken.add(loser.key());
                }
            }
        }

        private static String key(Village target) {
            return BannerBattleService.Loser.defenderOf(target).key();
        }

        /** When the used roll against the target comes back; null if it isn't used. */
        Instant nextRollAt(Village target) {
            return rolls.get(key(target));
        }

        /**
         * Whether a won attack with the entry's template can roll a banner (02-game-rules §21.7):
         * the roll is free, no attack on the way or earlier entry has the pair, and the template
         * can win one at all ({@link BannerBattleService#attackCanRoll}). Assumes banners drop.
         */
        boolean has(FarmTarget entry, Village target) {
            BannerBattleService.Loser loser = BannerBattleService.Loser.defenderOf(target);
            return !rolls.containsKey(loser.key())
                    && !taken.contains(loser.key())
                    && bannerBattles.attackCanRoll(loser, entry.getUnits().toMap());
        }

        /** The target's pair has its attack now: later entries of the pair have no chance. */
        void claim(Village target) {
            taken.add(key(target));
        }
    }

    /**
     * @param banner the source's banner (02-game-rules §21.2): Haste, so the trip shown is the one
     *     the attack will take, and Plunder for what the units can carry
     * @param chances the banner chances of this answer; the entry claims its pair if it has one
     * @param letters the player's templates by id
     * @param notes the player's village notes by village id (1.4)
     */
    private FarmTargetDto toDto(
            Village source,
            FarmTarget entry,
            Village target,
            BannerEffect banner,
            BannerChances chances,
            Map<Long, FarmTemplateLetter> letters,
            Map<Long, String> notes,
            Instant now) {
        UnitCounts units = entry.getUnits();
        double distance = Travel.distance(source.coordinates(), target.coordinates());
        long travelSeconds =
                Travel.withHaste(
                        Travel.travelSeconds(
                                distance,
                                Travel.armyMinutesPerField(gameData, units.toMap()),
                                worldService.settings()),
                        banner.haste());
        Player owner = target.getPlayer();
        Instant protectedUntil = owner == null ? null : owner.getProtectedUntil();
        boolean bannerChance = bannerDrops(worldService.settings()) && chances.has(entry, target);
        if (bannerChance) {
            chances.claim(target);
        }
        return new FarmTargetDto(
                entry.getId(),
                VillageRefDto.of(target),
                units.toMap(),
                entry.getTemplateId() == null ? null : letters.get(entry.getTemplateId()),
                distance,
                travelSeconds,
                protectedUntil != null && protectedUntil.isAfter(now) ? protectedUntil : null,
                entry.getLastSentAt(),
                chances.nextRollAt(target),
                bannerChance,
                notes.get(target.getId()),
                morale(source.getPlayer(), owner),
                Loot.capacity(gameData, units.toMap(), banner.plunder()),
                FarmRaidDto.of(entry));
    }

    /**
     * The morale an attack on {@code owner}'s village would have with today's points (1.6,
     * 02-game-rules §8.2); null at full strength and at barbarians.
     */
    private Double morale(Player attacker, Player owner) {
        if (attacker == null || owner == null) {
            return null;
        }
        double morale =
                worldService.settings().morale().factor(attacker.getPoints(), owner.getPoints());
        return morale < 1 ? morale : null;
    }
}
