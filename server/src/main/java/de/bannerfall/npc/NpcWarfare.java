package de.bannerfall.npc;

import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandPreviewDto;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.npc.NpcMemory.ConquestTarget;
import de.bannerfall.npc.NpcMemory.Grudge;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcTargeting.Estimate;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.unit.UnitService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The attacks of an NPC lord (09-npc-lords §6, §7), in a village's turn after the economy: a
 * conquest train, retaliation, scouting, farms and raids. Only through {@link CommandService};
 * every command is previewed first and checked against the caps and quiet hours (§7). The decisions
 * are in {@link NpcTargeting} and {@link NpcBalance}.
 */
@Component
class NpcWarfare {

    private final CommandService commands;
    private final UnitService unitService;
    private final GameData gameData;
    private final NpcBalance balance;
    private final BannerCatalog banners;

    NpcWarfare(
            CommandService commands,
            UnitService unitService,
            GameData gameData,
            NpcBalance balance,
            BannerCatalog banners) {
        this.commands = commands;
        this.unitService = unitService;
        this.gameData = gameData;
        this.balance = balance;
        this.banners = banners;
    }

    // ---- conquest target, before the economy (§6.6) ----

    /**
     * Whether this village wants nobles (§6.6): it has an academy, the lord isn't braked, and a
     * conquest target exists within reach. Picks or keeps the target in {@code memory.conquest}.
     */
    boolean wantsNobles(NpcTurn turn) {
        if (turn.village.getBuildings().get(BuildingType.ACADEMY) < 1 || turn.view.braked()) {
            return false;
        }
        turn.available =
                NpcTargeting.available(
                        unitService.homeUnits(turn.villageId()).toMap(),
                        turn.template().homeGuard());
        return conquestTarget(turn) != null;
    }

    /** The kept target if it is still a good one, else the best one now (or null). */
    private MapVillage conquestTarget(NpcTurn turn) {
        long id = turn.villageId();
        ConquestTarget kept = turn.memory.conquest.get(id);
        if (kept != null && kept.until().isAfter(turn.now)) {
            MapVillage target = turn.view.villages().get(kept.villageId());
            if (target != null && conquestEligible(turn, target)) {
                return target;
            }
        }
        MapVillage best =
                turn.view.villages().values().stream()
                        .filter(v -> conquestEligible(turn, v))
                        .max(
                                Comparator.comparingDouble(
                                                (MapVillage v) ->
                                                        NpcTargeting.conquestScore(
                                                                v.points(), turn.distanceTo(v)))
                                        .thenComparing(MapVillage::id, Comparator.reverseOrder()))
                        .orElse(null);
        if (best == null) {
            turn.memory.conquest.remove(id);
            return null;
        }
        Duration keep = NpcTargeting.scaled(NpcTargeting.CONQUEST_KEEP, turn.speed());
        turn.memory.conquest.put(id, new ConquestTarget(best.id(), turn.now.plus(keep)));
        return best;
    }

    private boolean conquestEligible(NpcTurn turn, MapVillage target) {
        if (turn.ownOrMate(target.playerId())
                || turn.view.mateTrainTargets().contains(target.id())) {
            return false; // a guild mate's train is on the way there (09 §13.2)
        }
        int radius =
                Math.min(turn.template().conquestRadius(), turn.view.world().nobleMaxDistance());
        if (turn.distanceTo(target) > radius) {
            return false;
        }
        if (!target.barbarian()
                && !(turn.template().conquersPlayers()
                        && !NpcTargeting.isProtected(target, turn.now)
                        && !NpcBalance.spared(turn.view.difficulty(), target)
                        && allowed(turn, target))) {
            return false;
        }
        return mayBeat(turn, target);
    }

    /**
     * Whether the village could clear the target as far as the lord knows: a target it gave up
     * scouting, or whose intel shows defenders or a wall its attack units (no catapults) can't beat
     * (§6.1), is no conquest target for now, so the academy goes for the next best instead of
     * waiting for one it can't take. A target without usable intel stays one: it gets scouted.
     */
    private boolean mayBeat(NpcTurn turn, MapVillage target) {
        Intel known = turn.memory.intel.get(target.id());
        if (known != null
                && known.leftAloneUntil() != null
                && known.leftAloneUntil().isAfter(turn.now)) {
            return false;
        }
        Intel intel = usableIntel(turn, target);
        if (intel == null
                || (!intel.showedDefenders() && (intel.wall() == null || intel.wall() == 0))) {
            return true;
        }
        return estimateAttack(
                        turn,
                        NpcTargeting.only(turn.available, clearingUnits(turn)),
                        intel,
                        null,
                        0)
                .go();
    }

    /** The units of a clearing attack and an escort: the attack units without catapults. */
    private static List<UnitType> clearingUnits(NpcTurn turn) {
        return turn.template().attackUnits().stream().filter(u -> u != UnitType.CATAPULT).toList();
    }

    // ---- the attack step (§6) ----

    void attack(NpcTurn turn) {
        turn.available =
                NpcTargeting.available(
                        unitService.homeUnits(turn.villageId()).toMap(),
                        turn.template().homeGuard());
        conquest(turn);
        retaliate(turn);
        scout(turn);
        farm(turn);
    }

    /**
     * §6.6: a clearing attack (if the intel shows defenders or a wall) and one noble attack,
     * checked as a whole before anything is sent.
     */
    private void conquest(NpcTurn turn) {
        if (!turn.wantsNobles) {
            return;
        }
        ConquestTarget chosen = turn.memory.conquest.get(turn.villageId());
        MapVillage target = chosen == null ? null : turn.view.villages().get(chosen.villageId());
        if (target == null || turn.ownTargets.contains(target.id())) {
            return;
        }
        int nobles = turn.available.getOrDefault(UnitType.NOBLE, 0);
        if (nobles < NpcTargeting.noblesNeeded(target.loyalty())) {
            return;
        }
        Intel intel = usableIntel(turn, target);
        if (intel == null || !capsAllow(turn, target, 1)) {
            return; // the scouting step gets the intel
        }
        double oath = oathOf(turn, intel);
        if (nobles < noblesWanted(turn, target.loyalty(), oath)) {
            return;
        }
        List<UnitType> order = clearingUnits(turn);
        Map<UnitType, Integer> pool = NpcTargeting.only(turn.available, order);
        int wall = intel.wall() == null ? 0 : intel.wall();
        Map<UnitType, Integer> clearing = Map.of();
        Map<UnitType, Integer> escort;
        if (intel.showedDefenders() || wall > 0) {
            Estimate full = estimateAttack(turn, pool, intel, null, 0);
            if (!full.go()) {
                return;
            }
            escort = NpcTargeting.escort(gameData, pool, order, full.wallAfter());
            if (escort == null) {
                return;
            }
            clearing = NpcTargeting.minus(pool, escort);
            Estimate rest = estimateAttack(turn, clearing, intel, null, 0);
            if (!rest.go()
                    || NpcTargeting.attackOf(gameData, escort)
                            < NpcTargeting.escortAttackNeeded(rest.wallAfter())) {
                return;
            }
        } else {
            escort = NpcTargeting.escort(gameData, pool, order, 0);
            if (escort == null) {
                return;
            }
        }
        Map<UnitType, Integer> nobleAttack = new EnumMap<>(UnitType.class);
        nobleAttack.putAll(escort);
        nobleAttack.put(UnitType.NOBLE, nobles);

        // the train as a whole: both previews, and both arrivals outside the quiet hours
        List<Map<UnitType, Integer>> train = new ArrayList<>();
        if (!clearing.isEmpty()) {
            train.add(clearing);
        }
        train.add(nobleAttack);
        Instant lastArrival = null;
        for (Map<UnitType, Integer> army : train) {
            Instant arrival = previewAttack(turn, target, army, null);
            if (arrival == null) {
                return;
            }
            lastArrival = arrival;
        }
        // the loyalty grows back while the nobles walk (v1.3): count it at their arrival
        double atArrival =
                NpcTargeting.loyaltyAtArrival(
                        target.loyalty(),
                        Loyalty.regenPerHour(turn.view.world()),
                        turn.now,
                        lastArrival);
        if (nobles < noblesWanted(turn, atArrival, oath)) {
            return;
        }
        for (Map<UnitType, Integer> army : train) {
            send(turn, target, army, null);
        }
        recordSent(turn, target);
        turn.memory.farmed.put(target.id(), lastArrival);
    }

    /**
     * §6.7: OFFENSIVE and BALANCED lords attack the source village of whoever attacked them, once
     * per grudge, if the estimate (with fresh intel) is a go.
     */
    private void retaliate(NpcTurn turn) {
        if (!turn.template().grudgeOpens()) {
            return;
        }
        for (Long playerId :
                new ArrayList<>(turn.memory.grudges.keySet()).stream().sorted().toList()) {
            Grudge grudge = turn.memory.grudges.get(playerId);
            if (grudge.retaliated() || !grudge.until().isAfter(turn.now)) {
                continue;
            }
            MapVillage source = turn.view.villages().get(grudge.sourceVillageId());
            if (source == null
                    || !Objects.equals(source.playerId(), playerId)
                    || NpcTargeting.isProtected(source, turn.now)) {
                retaliated(turn, playerId);
                continue;
            }
            Intel intel = usableIntel(turn, source);
            if (intel == null || turn.ownTargets.contains(source.id())) {
                continue; // the scouting step gets fresh intel first
            }
            Map<UnitType, Integer> army =
                    NpcTargeting.only(turn.available, turn.template().attackUnits());
            int wall = intel.wall() == null ? 0 : intel.wall();
            BuildingType catapultTarget = wall > 0 ? BuildingType.WALL : BuildingType.BARRACKS;
            int level =
                    intel.buildings() == null
                            ? 0
                            : intel.buildings().getOrDefault(catapultTarget, 0);
            if (army.isEmpty() || !estimateAttack(turn, army, intel, catapultTarget, level).go()) {
                // v1.3: too strong for what is at home now. The lord's village nearest the
                // attacker decides: it lets the grudge go only if its whole army (home, out
                // farming, in support; less the home guard) couldn't win either, else it gathers
                // its units for a later turn. A nearest village without any attack units has
                // nothing to decide with, so the others may still retaliate. The other villages
                // just leave it.
                if (nearestOwnVillage(turn, source)) {
                    Map<UnitType, Integer> whole =
                            NpcTargeting.only(
                                    NpcTargeting.available(
                                            unitService
                                                    .unitsOf(turn.villageId(), List.of())
                                                    .all()
                                                    .toMap(),
                                            turn.template().homeGuard()),
                                    turn.template().attackUnits());
                    if (whole.isEmpty()) {
                        continue;
                    }
                    if (estimateAttack(turn, whole, intel, catapultTarget, level).go()) {
                        turn.gathering = true;
                    } else {
                        retaliated(turn, playerId);
                    }
                }
                continue;
            }
            if (!capsAllow(turn, source, 1)) {
                continue;
            }
            BuildingType sentTarget = army.containsKey(UnitType.CATAPULT) ? catapultTarget : null;
            Instant arrival = previewAttack(turn, source, army, sentTarget);
            if (arrival == null) {
                continue;
            }
            send(turn, source, army, sentTarget);
            recordSent(turn, source);
            turn.memory.farmed.put(source.id(), arrival);
            retaliated(turn, playerId);
        }
    }

    /** Whether this village is the lord's nearest one to {@code target} (ties: the lower id). */
    private static boolean nearestOwnVillage(NpcTurn turn, MapVillage target) {
        return turn.view.villages().values().stream()
                .filter(v -> Objects.equals(v.playerId(), turn.lordId()))
                .min(
                        Comparator.comparingDouble(
                                        (MapVillage v) ->
                                                v.coordinates().distanceTo(target.coordinates()))
                                .thenComparingLong(MapVillage::id))
                .map(v -> v.id() == turn.villageId())
                .orElse(false);
    }

    private static void retaliated(NpcTurn turn, long playerId) {
        Grudge grudge = turn.memory.grudges.get(playerId);
        turn.memory.grudges.put(
                playerId,
                new Grudge(
                        grudge.until(),
                        grudge.sourceVillageId(),
                        true,
                        grudge.scouts(),
                        grudge.shared()));
    }

    /**
     * §6.3: scouts-only attacks where an attack needs intel first: the conquest target, retaliation
     * targets, then farm and raid targets nearest first. At most {@code farms-per-round} scoutings
     * per village and round.
     */
    private void scout(NpcTurn turn) {
        int limit = turn.view.general().farmsPerRound();
        int sent = 0;
        for (MapVillage target : scoutWishes(turn)) {
            if (sent >= limit) {
                return;
            }
            if (turn.ownTargets.contains(target.id())) {
                continue;
            }
            Intel intel = turn.memory.intel.get(target.id());
            if (intel != null
                    && intel.leftAloneUntil() != null
                    && intel.leftAloneUntil().isAfter(turn.now)) {
                continue;
            }
            int scouts = NpcTargeting.scoutCount(intel, knownScouts(turn, target));
            if (turn.available.getOrDefault(UnitType.SCOUT, 0) < scouts) {
                continue;
            }
            // towards a player: room for the attack that is to follow, too
            if (!target.barbarian()
                    && (NpcTargeting.isProtected(target, turn.now)
                            || !capsAllow(turn, target, 2))) {
                continue;
            }
            Map<UnitType, Integer> army = Map.of(UnitType.SCOUT, scouts);
            if (previewAttack(turn, target, army, null) == null) {
                continue;
            }
            send(turn, target, army, null);
            recordSent(turn, target);
            sent++;
        }
    }

    /**
     * The scouts the lord knows stand in {@code target}: those an attack from there on the lord
     * brought home, while that grudge is active (0 if none).
     */
    private static int knownScouts(NpcTurn turn, MapVillage target) {
        if (!turn.grudgeAgainst(target.playerId())) {
            return 0;
        }
        Grudge grudge = turn.memory.grudges.get(target.playerId());
        return grudge.sourceVillageId() == target.id() ? grudge.scouts() : 0;
    }

    /** The villages an attack of this turn would need intel about, most important first. */
    private List<MapVillage> scoutWishes(NpcTurn turn) {
        Map<Long, MapVillage> wishes = new LinkedHashMap<>();
        NpcTurn.View view = turn.view;
        ConquestTarget conquest = turn.memory.conquest.get(turn.villageId());
        if (turn.wantsNobles && conquest != null) {
            MapVillage target = view.villages().get(conquest.villageId());
            if (target != null && usableIntel(turn, target) == null) {
                wishes.put(target.id(), target);
            }
        }
        if (turn.template().grudgeOpens()) {
            turn.memory.grudges.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            e -> {
                                Grudge g = e.getValue();
                                MapVillage source = view.villages().get(g.sourceVillageId());
                                if (!g.retaliated()
                                        && g.until().isAfter(turn.now)
                                        && source != null
                                        && Objects.equals(source.playerId(), e.getKey())
                                        && usableIntel(turn, source) == null) {
                                    wishes.putIfAbsent(source.id(), source);
                                }
                            });
        }
        if (!farmsThisRound(turn)) {
            // no scouting for farms and raids that this round won't send (§6.2, v1.3)
            return new ArrayList<>(wishes.values());
        }
        for (MapVillage target : farmCandidates(turn)) {
            Intel intel = turn.memory.intel.get(target.id());
            if (target.barbarian()) {
                if (usableIntel(turn, target) == null) {
                    wishes.putIfAbsent(target.id(), target);
                }
            } else if (!NpcTargeting.isProtected(target, turn.now)
                    && allowed(turn, target)
                    && (intel == null
                            || intel.units() == null
                            || (!isFresh(turn, intel) && !intel.showedDefenders()))) {
                // A player village seen defended isn't scouted again for raids until that intel
                // is forgotten: every scouting counts against the caps.
                wishes.putIfAbsent(target.id(), target);
            }
        }
        return new ArrayList<>(wishes.values());
    }

    /**
     * Whether the lord farms this round (§6.2, v1.3): not while its farm budget is spent and, with
     * {@code farm-rest}, not in the build quiet hours.
     */
    private static boolean farmsThisRound(NpcTurn turn) {
        Difficulty difficulty = turn.view.difficulty();
        return !turn.view.farmBudgetSpent()
                && !(difficulty.farmRest()
                        && NpcPlanning.inQuietWindow(
                                turn.now,
                                difficulty.buildQuietFromTime(),
                                difficulty.buildQuietToTime(),
                                turn.view.general().zone()));
    }

    /**
     * §6.2: farms (barbarian villages) and raids (player villages whose fresh intel shows no
     * defenders), nearest first, at most {@code farms-per-round}; none while the lord's farm budget
     * is spent or, with {@code farm-rest}, in the build quiet hours (v1.3).
     */
    private void farm(NpcTurn turn) {
        if (!farmsThisRound(turn)) {
            return;
        }
        NpcBalanceSettings.Balance general = turn.view.general();
        Duration cooldown =
                NpcTargeting.scaled(
                        Duration.ofSeconds(Math.round(general.farmCooldownMinutes() * 60)),
                        turn.speed());
        int sent = 0;
        for (MapVillage target : farmCandidates(turn)) {
            if (sent >= general.farmsPerRound()) {
                return;
            }
            Instant last = turn.memory.farmed.get(target.id());
            if (turn.ownTargets.contains(target.id())
                    || (last != null && last.plus(cooldown).isAfter(turn.now))) {
                continue;
            }
            Intel intel = turn.memory.intel.get(target.id());
            int wall;
            int expected;
            if (target.barbarian()) {
                if (intel == null) {
                    // first contact without scouts: a probe of a small barbarian village
                    if (turn.available.getOrDefault(UnitType.SCOUT, 0) > 0
                            || target.points() > NpcTargeting.PROBE_MAX_POINTS) {
                        continue;
                    }
                    wall = NpcTargeting.PROBE_WALL;
                } else {
                    Intel usable = usableIntel(turn, target);
                    if (usable == null || usable.showedDefenders()) {
                        continue;
                    }
                    wall = usable.wall() == null ? NpcTargeting.PROBE_WALL : usable.wall();
                }
            } else {
                if (NpcTargeting.isProtected(target, turn.now)
                        || !allowed(turn, target)
                        || intel == null
                        || intel.units() == null
                        || intel.showedDefenders()
                        || !isFresh(turn, intel)
                        || !capsAllow(turn, target, 1)) {
                    continue;
                }
                wall = intel.wall() == null ? 0 : intel.wall();
            }
            expected = NpcTargeting.expectedLoot(intel, turn.now, turn.speed());
            int carry = Math.max(expected, NpcTargeting.MIN_FARM_CARRY);
            int targetWall = wall;
            double bulwark = bulwarkOf(turn, intel);
            Map<UnitType, Integer> army =
                    NpcTargeting.farmArmy(
                            gameData,
                            NpcTargeting.only(turn.available, farmUnits(turn)),
                            farmUnits(turn),
                            carry,
                            a ->
                                    NpcTargeting.estimate(
                                                    gameData,
                                                    a,
                                                    Map.of(),
                                                    targetWall,
                                                    null,
                                                    0,
                                                    general.farmMargin(),
                                                    general.farmMaxLoss(),
                                                    bulwark)
                                            .go());
            if (army == null) {
                continue;
            }
            Instant arrival = previewAttack(turn, target, army, null);
            if (arrival == null) {
                continue;
            }
            send(turn, target, army, null);
            recordSent(turn, target);
            turn.memory.farmed.put(target.id(), arrival);
            sent++;
        }
    }

    /**
     * The units this village farms with (§6.2): the template's {@code farm-units}, without the
     * attack units while it gathers them for a retaliation (v1.3) and without the support units
     * while an attack is on its way here (v1.3: they defend instead of walking away).
     */
    private static List<UnitType> farmUnits(NpcTurn turn) {
        return turn.template().farmUnits().stream()
                .filter(u -> !(turn.gathering && turn.template().attackUnits().contains(u)))
                .filter(u -> !(turn.threatened && turn.template().supportUnits().contains(u)))
                .toList();
    }

    /**
     * Villages within {@code farm-radius} that are neither the lord's nor a guild mate's (§13.2),
     * nearest first; for OFFENSIVE the grudge holders' villages come first (§6.7).
     */
    private List<MapVillage> farmCandidates(NpcTurn turn) {
        int radius = turn.template().farmRadius();
        boolean grudgeFirst = turn.view.personality() == NpcPersonality.OFFENSIVE;
        return turn.view.villages().values().stream()
                .filter(v -> !turn.ownOrMate(v.playerId()))
                .filter(v -> turn.distanceTo(v) <= radius)
                .sorted(
                        Comparator.comparing(
                                        (MapVillage v) ->
                                                !(grudgeFirst && turn.grudgeAgainst(v.playerId())))
                                .thenComparingDouble(turn::distanceTo)
                                .thenComparingLong(MapVillage::id))
                .toList();
    }

    // ---- helpers ----

    private boolean allowed(NpcTurn turn, MapVillage target) {
        return NpcTargeting.targetAllowed(
                turn.template(),
                turn.village.getPoints(),
                turn.view.lordPoints(),
                target,
                turn.grudgeAgainst(target.playerId()));
    }

    /** Usable intel (§4); a barbarian wall the lord saw counts as grown since (M29). */
    private Intel usableIntel(NpcTurn turn, MapVillage target) {
        Intel intel =
                NpcTargeting.usable(
                        turn.memory.intel.get(target.id()),
                        target.barbarian(),
                        turn.now,
                        turn.view.general().intelMaxAgeHours(),
                        turn.speed());
        if (intel == null || !target.barbarian() || intel.wall() == null) {
            return intel;
        }
        int wall =
                NpcTargeting.expectedBarbarianWall(
                        intel.wall(), intel.at(), turn.now, turn.view.world());
        return wall == intel.wall() ? intel : intel.withWall(wall);
    }

    private static boolean isFresh(NpcTurn turn, Intel intel) {
        return NpcTargeting.isFresh(
                intel, turn.now, turn.view.general().intelMaxAgeHours(), turn.speed());
    }

    private Estimate estimateAttack(
            NpcTurn turn,
            Map<UnitType, Integer> army,
            Intel intel,
            BuildingType catapultTarget,
            int catapultTargetLevel) {
        return NpcTargeting.estimate(
                gameData,
                army,
                intel.units() == null ? Map.of() : intel.units(),
                intel.wall() == null ? 0 : intel.wall(),
                catapultTarget,
                catapultTargetLevel,
                turn.template().margin(),
                turn.view.general().attackMaxLoss(),
                bulwarkOf(turn, intel));
    }

    /**
     * The nobles a train waits for (v1.3): enough for the loyalty with the scouted Oath, but never
     * more than the template keeps. An Oath village at full loyalty can need one more than that;
     * the lord then sends all it has and the next train finishes the village, as before.
     */
    private static int noblesWanted(NpcTurn turn, double loyalty, double oath) {
        return Math.min(
                NpcTargeting.noblesNeeded(loyalty, oath), Math.max(1, turn.template().nobles()));
    }

    /** The Oath the lord expects (v1.3, 02-game-rules §21.2); none while banners are off. */
    private double oathOf(NpcTurn turn, Intel intel) {
        return turn.view.world().bannersEnabled() ? NpcTargeting.oath(intel, banners) : 0;
    }

    /** The Bulwark the lord expects (02-game-rules §21.11); none while banners are off. */
    private double bulwarkOf(NpcTurn turn, Intel intel) {
        return turn.view.world().bannersEnabled() ? NpcTargeting.bulwark(intel, banners) : 0;
    }

    /**
     * §7: whether {@code commands} more commands towards the target's owner stay within the caps.
     * Barbarian villages are never limited.
     */
    private static boolean capsAllow(NpcTurn turn, MapVillage target, int commands) {
        if (target.barbarian()) {
            return true;
        }
        long playerId = target.playerId();
        int own = NpcBalance.sentTo(turn.memory.attacksSent, playerId, turn.now);
        int all = turn.view.otherLordsToHuman().getOrDefault(playerId, 0) + own;
        return NpcBalance.capsAllow(turn.view.difficulty(), own, target.human(), all, commands);
    }

    /**
     * The arrival of the attack if it may be sent now: {@code preview} finds no error and, towards
     * a human, it doesn't arrive in the quiet hours; else null.
     */
    private Instant previewAttack(
            NpcTurn turn,
            MapVillage target,
            Map<UnitType, Integer> army,
            BuildingType catapultTarget) {
        CommandPreviewDto preview =
                commands.preview(
                        turn.villageId(), turn.lordId(), request(target, army, catapultTarget));
        if (!preview.errors().isEmpty() || preview.arriveAt() == null) {
            return null;
        }
        if (target.human() && balance.quietArrival(turn.view.difficulty(), preview.arriveAt())) {
            return null;
        }
        return preview.arriveAt();
    }

    private void send(
            NpcTurn turn,
            MapVillage target,
            Map<UnitType, Integer> army,
            BuildingType catapultTarget) {
        commands.send(turn.villageId(), turn.lordId(), request(target, army, catapultTarget));
        turn.available = NpcTargeting.minus(turn.available, army);
        turn.ownTargets.add(target.id());
    }

    /** A command towards a player village counts against the caps (a train once). */
    private static void recordSent(NpcTurn turn, MapVillage target) {
        if (!target.barbarian()) {
            turn.memory.attacksSent.add(new NpcMemory.SentAttack(target.playerId(), turn.now));
        }
    }

    private static CommandRequest request(
            MapVillage target, Map<UnitType, Integer> army, BuildingType catapultTarget) {
        return new CommandRequest(CommandKind.ATTACK, target.coordinates(), army, catapultTarget);
    }
}
