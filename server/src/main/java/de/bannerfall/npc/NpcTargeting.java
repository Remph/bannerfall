package de.bannerfall.npc;

import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.Barbarians;
import de.bannerfall.rules.BattleInput;
import de.bannerfall.rules.BattleResult;
import de.bannerfall.rules.Combat;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.rules.Production;
import de.bannerfall.rules.StandardEffect;
import de.bannerfall.rules.Storage;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The pure decisions of an NPC lord's warfare (09-npc-lords §6.1–§6.4, §6.6): the outcome estimate,
 * who may be attacked, intel age, farm sizes and armies, scouts, the conquest score and the escort
 * of a noble attack. Plain values in, a decision out ({@code NpcTargetingTest}).
 */
public final class NpcTargeting {

    /**
     * A barbarian village with at most this many points can be probed without scouting: the most a
     * spawned one reaches by growth (mines 12, warehouse 5, farm 1, Town Hall 1, wall 2; 788 with
     * the points growth of 1.2 in 1.2.2 and earlier).
     */
    static final int PROBE_MAX_POINTS = 591;

    /** The wall a probe expects (the most a spawned barbarian village has). */
    static final int PROBE_WALL = 2;

    /** The loot a first probe expects. */
    static final int FIRST_PROBE_LOOT = 500;

    /** A farm carries at least this much. */
    static final int MIN_FARM_CARRY = 100;

    /**
     * An army faster than this (minutes per field) can only be scouts (09 §6.5); with banners on,
     * less the strongest Haste ({@link #onlyScouts}).
     */
    static final double SCOUTS_ONLY_MINUTES_PER_FIELD = 9.5;

    /** Scouts for a target without any scouting result yet. */
    static final int FIRST_SCOUTS = 5;

    /** After this many failed scoutings in a row the target is left alone for a while. */
    static final int SCOUT_FAILURES_TO_GIVE_UP = 2;

    /** An escort attacks with at least this times the defence of the empty village. */
    static final double ESCORT_FACTOR = 3;

    /** A conquest target is kept this long (game time, ÷ speed). */
    static final Duration CONQUEST_KEEP = Duration.ofDays(2);

    /** Intel older than this (game time, ÷ speed) is forgotten. */
    static final Duration INTEL_KEEP = Duration.ofDays(7);

    /**
     * The loyalty a lord plans per noble (09 §6.6): below the average of a real hit ({@link
     * Loyalty#MIN_HIT} to {@link Loyalty#MAX_HIT}), so a train rarely falls one noble short.
     */
    static final int PLANNED_HIT = 25;

    private NpcTargeting() {}

    /**
     * The result of {@link #estimate}.
     *
     * @param go the attacker wins the pessimistic battle with a loss ratio of at most the maximum
     * @param wallAfter the wall the battle would leave
     */
    public record Estimate(boolean go, double lossRatio, int wallAfter) {
        static final Estimate NO = new Estimate(false, 1, 0);
    }

    /**
     * What a lord expects at a target besides the units (§6.1): the wall and the catapult target as
     * far as it knows them, the banners and paladin standards of both sides, the night bonus at the
     * arrival and the morale. {@link #at} is a village with nothing of that; each {@code with} adds
     * one part.
     *
     * @param catapultTarget null if the attack has no catapults
     * @param catapultTargetLevel its level as far as the lord knows (the wall for {@code WALL})
     * @param defenceBonus the Bulwark banner the lord knows of ({@link #bulwark}), 0 without
     * @param defenderStandard the paladin standard the lord expects the defenders to carry ({@link
     *     #standard}, 1.4)
     * @param attackBonus the War banner of the sending village, 0 without; only with lord banners
     *     on (09-npc-lords §14.7): the lord knows its own villages
     * @param attackerStandard the standard the army takes along, {@link StandardEffect#NONE}
     *     without a paladin in it
     * @param nightFactor the night bonus at the arrival (1.4, 02-game-rules §8.1); 1 by day and at
     *     barbarians
     * @param morale the lord's morale against the target's owner (1.6, 02-game-rules §8.2); 1 at
     *     full strength and at barbarians
     */
    public record Expected(
            int wall,
            BuildingType catapultTarget,
            int catapultTargetLevel,
            double defenceBonus,
            StandardEffect defenderStandard,
            double attackBonus,
            StandardEffect attackerStandard,
            double nightFactor,
            double morale) {

        /** A target with this wall and nothing else the lord knows of. */
        public static Expected at(int wall) {
            return new Expected(
                    wall, null, 0, 0, StandardEffect.NONE, 0, StandardEffect.NONE, 1, 1);
        }

        public Expected withCatapults(BuildingType target, int level) {
            return new Expected(
                    wall,
                    target,
                    level,
                    defenceBonus,
                    defenderStandard,
                    attackBonus,
                    attackerStandard,
                    nightFactor,
                    morale);
        }

        public Expected withDefence(double bulwark, StandardEffect standard) {
            return new Expected(
                    wall,
                    catapultTarget,
                    catapultTargetLevel,
                    bulwark,
                    standard,
                    attackBonus,
                    attackerStandard,
                    nightFactor,
                    morale);
        }

        public Expected withAttack(double war, StandardEffect standard) {
            return new Expected(
                    wall,
                    catapultTarget,
                    catapultTargetLevel,
                    defenceBonus,
                    defenderStandard,
                    war,
                    standard,
                    nightFactor,
                    morale);
        }

        public Expected withNight(double factor) {
            return new Expected(
                    wall,
                    catapultTarget,
                    catapultTargetLevel,
                    defenceBonus,
                    defenderStandard,
                    attackBonus,
                    attackerStandard,
                    factor,
                    morale);
        }

        public Expected withMorale(double factor) {
            return new Expected(
                    wall,
                    catapultTarget,
                    catapultTargetLevel,
                    defenceBonus,
                    defenderStandard,
                    attackBonus,
                    attackerStandard,
                    nightFactor,
                    factor);
        }
    }

    /**
     * §6.1: the real battle formula with luck 0 against the defenders seen × {@code margin} (each
     * count rounded up) in one stack. A go only if the attacker wins and loses at most {@code
     * maxLoss} of his units. Banner thieves among the defenders count like any unit (defence 3).
     */
    public static Estimate estimate(
            GameData data,
            Map<UnitType, Integer> attackers,
            Map<UnitType, Integer> defenders,
            Expected expected,
            double margin,
            double maxLoss) {
        if (attackers.values().stream().noneMatch(c -> c > 0)) {
            return Estimate.NO;
        }
        Map<UnitType, Integer> scaled = new EnumMap<>(UnitType.class);
        defenders.forEach(
                (type, count) -> {
                    if (count > 0) {
                        scaled.put(type, (int) Math.ceil(count * margin));
                    }
                });
        int wall = expected.wall();
        BuildingType target =
                attackers.getOrDefault(UnitType.CATAPULT, 0) > 0 ? expected.catapultTarget() : null;
        int targetLevel =
                target == null
                        ? 0
                        : target == BuildingType.WALL ? wall : expected.catapultTargetLevel();
        BattleResult result =
                Combat.fight(
                        data,
                        BattleInput.attack(attackers)
                                .defender(scaled)
                                .wall(wall)
                                .catapults(target, targetLevel)
                                .attackBonus(expected.attackBonus())
                                .defenceBonus(expected.defenceBonus())
                                .attackerStandard(expected.attackerStandard())
                                .defenderStandards(List.of(expected.defenderStandard()))
                                .nightFactor(expected.nightFactor())
                                .morale(expected.morale())
                                .build());
        if (!result.attackerWon()) {
            return new Estimate(false, 1, wall);
        }
        double loss = result.mainBattle().attackerLossRatio();
        return new Estimate(loss <= maxLoss, loss, result.wallAfter());
    }

    /**
     * The Bulwark the lord expects in a village (02-game-rules §21.11): the banner his last
     * successful scouting saw there, at the tier the scouted Banner Hall lets it work at. No banner
     * seen (or never scouted): none. The lord assumes it works even where it might sleep (another
     * lord's village), the safe side.
     */
    public static double bulwark(Intel intel, BannerCatalog catalog) {
        if (intel == null
                || intel.banner() == null
                || intel.banner().type() != BannerType.BULWARK) {
            return 0;
        }
        int hall =
                intel.buildings() == null
                        ? 0
                        : intel.buildings().getOrDefault(BuildingType.BANNER_HALL, 0);
        return catalog.flying(BannerType.BULWARK, intel.banner().tier(), hall).bonus();
    }

    /**
     * The paladin standard the lord expects the defenders to carry (1.4, 02-game-rules §21.18): the
     * one his last successful scouting saw equipped there, if a paladin was among the defenders he
     * saw and the scouted Banner Hall lets it work. The lord can't know the owner's paladin level,
     * so he takes the standard's own tier, the safe side. None seen: none.
     */
    public static StandardEffect standard(Intel intel, BannerCatalog catalog) {
        if (intel == null
                || intel.standard() == null
                || intel.units() == null
                || intel.units().getOrDefault(UnitType.PALADIN, 0) == 0) {
            return StandardEffect.NONE;
        }
        int hall =
                intel.buildings() == null
                        ? 0
                        : intel.buildings().getOrDefault(BuildingType.BANNER_HALL, 0);
        BattleReport.ScoutedBanner seen = intel.standard();
        return catalog.standard(
                seen.type(), seen.tier(), BannerRules.standardTier(seen.tier(), seen.tier(), hall));
    }

    // ---- who may be attacked (§6.4) ----

    /**
     * Whether a lord with this template may attack {@code target} from a village with {@code
     * attackerVillagePoints} (§6.4). Barbarian villages always; a grudge against the owner lifts
     * the limits where the template says so.
     */
    public static boolean targetAllowed(
            NpcTemplates.Template template,
            int attackerVillagePoints,
            int lordPoints,
            MapVillage target,
            boolean grudge) {
        if (target.barbarian()) {
            return true;
        }
        if (grudge && template.grudgeOpens()) {
            return true;
        }
        if (template.villageRatio() != null
                && !(target.points() < template.villageRatio() * attackerVillagePoints)) {
            return false;
        }
        return !(target.human()
                && template.humanPlayerRatio() != null
                && !(target.ownerPoints() < template.humanPlayerRatio() * lordPoints));
    }

    /** Whether the target's owner is under beginner protection at {@code now}. */
    public static boolean isProtected(MapVillage target, Instant now) {
        return target.ownerProtectedUntil() != null && target.ownerProtectedUntil().isAfter(now);
    }

    // ---- intel (§4) ----

    /** Game time scaled by the world speed: {@code duration ÷ speed}, at least one second. */
    public static Duration scaled(Duration duration, double speed) {
        return Duration.ofSeconds(Math.max(1, Math.round(duration.toSeconds() / speed)));
    }

    /** Fresh: not older than {@code intel-max-age-hours ÷ speed}. */
    public static boolean isFresh(Intel intel, Instant now, double maxAgeHours, double speed) {
        long maxAgeSeconds = Math.round(maxAgeHours * 3600 / speed);
        return intel != null && !intel.at().plusSeconds(maxAgeSeconds).isBefore(now);
    }

    /**
     * Intel good enough to plan an attack on (§4): about a player village it must be fresh and have
     * seen the units; about a barbarian village it stays good until it is pruned, unless it showed
     * units or a lost battle (then it must be fresh too).
     *
     * @return null if there is none that is good enough
     */
    public static Intel usable(
            Intel intel, boolean barbarian, Instant now, double maxAgeHours, double speed) {
        if (intel == null || intel.units() == null) {
            return null;
        }
        boolean fresh = isFresh(intel, now, maxAgeHours, speed);
        if (barbarian
                && !intel.showedDefenders()
                && intel.outcome() != BattleOutcome.DEFENDER_WON) {
            return intel;
        }
        return fresh ? intel : null;
    }

    /**
     * The wall a barbarian village may have by {@code now}, {@code seen} at {@code at}: with walls
     * on (M29, 02-game-rules §11) one more level per growth round since, up to the cap. An upper
     * bound (a razed wall may not be rebuilt): the lord rather sends too much than loses the army.
     * Barbarian intel stays usable for days (§4), long enough for several rounds.
     */
    public static int expectedBarbarianWall(
            int seen, Instant at, Instant now, WorldSettings world) {
        if (!world.barbarianWallsEnabled() || seen >= world.barbarianWallMaxLevel()) {
            return seen;
        }
        long elapsed = Math.max(0, Duration.between(at, now).toSeconds());
        long interval = Barbarians.growthInterval(world).toSeconds();
        long rounds = (elapsed + interval - 1) / interval;
        return (int) Math.min(world.barbarianWallMaxLevel(), seen + rounds);
    }

    // ---- scouting (§6.3) ----

    /**
     * Scouts for the next scouting: {@code max(3, 2 × seen + 1)} (5 if none were ever seen),
     * doubled for each failure in a row.
     */
    public static int scoutCount(Intel intel) {
        return scoutCount(intel, 0);
    }

    /**
     * Scouts for the next scouting when the lord also knows that at least {@code knownScouts} stand
     * there (an attack from there brought them home): those count like scouts seen, so the lord
     * never sends a handful against an army of scouts it knows about (§6.3).
     */
    public static int scoutCount(Intel intel, int knownScouts) {
        boolean unseen = intel == null || intel.units() == null;
        int seen = Math.max(unseen ? 0 : intel.scoutsSeen(), knownScouts);
        int base = Math.max(unseen ? FIRST_SCOUTS : 3, 2 * seen + 1);
        int failures = intel == null ? 0 : Math.min(intel.scoutFailures(), 10);
        return base << failures;
    }

    // ---- farming (§6.2) ----

    /**
     * The loot to expect at a farm target (§6.2). With scouted resources: those plus the production
     * since (mines from the intel, else level 3), each capped at the warehouse (intel, else level
     * 5), minus the hiding place (intel, else 0). Without: the last loot × 1.5 if it filled the
     * carry capacity, else the last loot. No intel: a first probe expects 500.
     */
    public static int expectedLoot(Intel intel, Instant now, double speed) {
        if (intel == null) {
            return FIRST_PROBE_LOOT;
        }
        if (intel.resources() == null) {
            int last = intel.loot().total();
            return intel.lootFull() ? (int) Math.round(last * 1.5) : last;
        }
        Map<BuildingType, Integer> b = intel.buildings() == null ? Map.of() : intel.buildings();
        double hours = Math.max(0, Duration.between(intel.at(), now).toSeconds() / 3600.0);
        int capacity = Storage.warehouseCapacity(b.getOrDefault(BuildingType.WAREHOUSE, 5));
        int hiding = Storage.hidingCapacity(b.getOrDefault(BuildingType.HIDING_PLACE, 0));
        ResourceAmounts r = intel.resources();
        return lootable(
                        r.wood(),
                        b.getOrDefault(BuildingType.WOODCUTTER, 3),
                        hours,
                        speed,
                        capacity,
                        hiding)
                + lootable(
                        r.clay(),
                        b.getOrDefault(BuildingType.CLAY_PIT, 3),
                        hours,
                        speed,
                        capacity,
                        hiding)
                + lootable(
                        r.iron(),
                        b.getOrDefault(BuildingType.IRON_MINE, 3),
                        hours,
                        speed,
                        capacity,
                        hiding);
    }

    private static int lootable(
            int stock, int mine, double hours, double speed, int capacity, int hiding) {
        double now = Math.min(capacity, stock + Production.perHour(mine, speed, 1.0) * hours);
        return (int) Math.max(0, Math.floor(now) - hiding);
    }

    /**
     * The army of a farm or raid (§6.2): units from {@code available} in {@code order} until they
     * carry {@code carryNeeded}, then more (each type doubled in turn) until {@code go} accepts it.
     * Units that carry nothing are not taken.
     *
     * @return null if {@code go} can't be met with what is available
     */
    public static Map<UnitType, Integer> farmArmy(
            GameData data,
            Map<UnitType, Integer> available,
            List<UnitType> order,
            int carryNeeded,
            Predicate<Map<UnitType, Integer>> go) {
        Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
        int carry = 0;
        for (UnitType type : order) {
            int perUnit = data.unit(type).carry();
            int have = available.getOrDefault(type, 0);
            if (carry >= carryNeeded || perUnit == 0 || have == 0) {
                continue;
            }
            int take = Math.min(have, (carryNeeded - carry + perUnit - 1) / perUnit);
            army.put(type, take);
            carry += take * perUnit;
        }
        while (true) {
            if (!army.isEmpty() && go.test(army)) {
                return army;
            }
            boolean grown = false;
            for (UnitType type : order) {
                int have = available.getOrDefault(type, 0);
                int now = army.getOrDefault(type, 0);
                if (data.unit(type).carry() == 0 || now >= have) {
                    continue;
                }
                army.put(type, Math.min(have, Math.max(now * 2, now + 1)));
                grown = true;
                break;
            }
            if (!grown) {
                return null;
            }
        }
    }

    // ---- conquest (§6.6) ----

    /** {@code points / (1 + distance / 10)}: the best conquest target has the highest. */
    public static double conquestScore(int points, double distance) {
        return points / (1 + distance / 10);
    }

    /** Nobles a train needs: {@code ceil(loyalty / 25)}, at least 1. */
    public static int noblesNeeded(int loyalty) {
        return noblesNeeded(loyalty, 0);
    }

    /**
     * Nobles a train needs against a village with an Oath banner (v1.3, 02-game-rules §21.2): each
     * noble is counted as {@code 25 × (1 − oath)}, so {@code ceil(loyalty / (25 × (1 − oath)))}, at
     * least 1. {@code oath} 0 is the plain rule.
     */
    public static int noblesNeeded(double loyalty, double oath) {
        double perNoble = PLANNED_HIT * (1 - oath);
        // a hair below the next whole number counts as that number (100 / 25 is 4, not 5)
        return Math.max(1, (int) Math.ceil(loyalty / perNoble - 1e-9));
    }

    /**
     * The loyalty the nobles find when they arrive (v1.3): it grows back on the way, {@code
     * min(100, loyalty + perHour × hours until arrival)}. Counting today's loyalty sent too few
     * nobles on long walks (20 fields at speed 3 take about 12 hours, 35 points of loyalty).
     */
    public static double loyaltyAtArrival(
            double loyalty, double perHour, Instant now, Instant arrival) {
        return Loyalty.regen(loyalty, perHour, now, arrival);
    }

    /**
     * The Oath the lord expects in the target (v1.3, like {@link #bulwark}): the banner its last
     * successful scouting saw, at the tier the scouted Banner Hall lets it work; 0 without.
     */
    public static double oath(Intel intel, BannerCatalog catalog) {
        if (intel == null || intel.banner() == null || intel.banner().type() != BannerType.OATH) {
            return 0;
        }
        int hall =
                intel.buildings() == null
                        ? 0
                        : intel.buildings().getOrDefault(BuildingType.BANNER_HALL, 0);
        int tier = BannerRules.effectiveTier(intel.banner().tier(), hall);
        return tier == 0 ? 0 : catalog.effect(BannerType.OATH, tier);
    }

    /** The attack an escort needs: 3 × the defence of the empty village with this wall. */
    public static double escortAttackNeeded(int wall) {
        return ESCORT_FACTOR * Combat.defenseStrength(0, wall);
    }

    /** The attack strength of an army (no luck): {@code Σ count × attack}. */
    public static double attackOf(GameData data, Map<UnitType, Integer> army) {
        double sum = 0;
        for (var entry : army.entrySet()) {
            sum += (double) entry.getValue() * data.unit(entry.getKey()).attack();
        }
        return sum;
    }

    /**
     * The escort of a noble attack (§6.6): units from {@code pool} in {@code order} until their
     * attack reaches {@link #escortAttackNeeded}.
     *
     * @return null if the pool isn't strong enough
     */
    public static Map<UnitType, Integer> escort(
            GameData data, Map<UnitType, Integer> pool, List<UnitType> order, int wall) {
        double needed = escortAttackNeeded(wall);
        Map<UnitType, Integer> escort = new EnumMap<>(UnitType.class);
        double attack = 0;
        for (UnitType type : order) {
            int perUnit = data.unit(type).attack();
            int have = pool.getOrDefault(type, 0);
            if (attack >= needed || perUnit == 0 || have == 0) {
                continue;
            }
            int take = Math.min(have, (int) Math.ceil((needed - attack) / perUnit));
            escort.put(type, take);
            attack += (double) take * perUnit;
        }
        return attack >= needed ? escort : null;
    }

    // ---- defence (§6.5) ----

    /**
     * Whether an incoming attack can only be scouts: its army walks faster than {@code 9.5 × (1 −
     * maxHaste)} minutes per field ({@code (arriveAt − departAt) / distance}, scaled back by the
     * speeds). A Haste banner lets light cavalry (10 min/field) walk under 9.5, so the threshold
     * drops by the strongest Haste there is (code review 1.2, D5; 8.075 with 15 %): hasted cavalry
     * counts as a threat, and unhasted scouts (9 min/field) count as one too, the safe side.
     *
     * @param maxHaste the strongest Haste banner, as a fraction; 0 while banners are off
     */
    public static boolean onlyScouts(
            Instant departAt,
            Instant arriveAt,
            double distance,
            double speed,
            double unitSpeed,
            double maxHaste) {
        if (distance <= 0) {
            return false;
        }
        double seconds = Duration.between(departAt, arriveAt).toMillis() / 1000.0;
        double minutesPerField = seconds * speed * unitSpeed / 60 / distance;
        return minutesPerField < SCOUTS_ONLY_MINUTES_PER_FIELD * (1 - maxHaste);
    }

    /** {@code share} of each count, rounded down, only the types in {@code types}. */
    public static Map<UnitType, Integer> shareOf(
            Map<UnitType, Integer> units, List<UnitType> types, double share) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        for (UnitType type : types) {
            int count = (int) Math.floor(units.getOrDefault(type, 0) * share);
            if (count > 0) {
                result.put(type, count);
            }
        }
        return result;
    }

    /**
     * The units a village may send (§6): at home minus {@code homeGuard} of each type (rounded up);
     * scouts and nobles are exempt.
     */
    public static Map<UnitType, Integer> available(Map<UnitType, Integer> home, double homeGuard) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        home.forEach(
                (type, count) -> {
                    int guard =
                            type == UnitType.SCOUT || type == UnitType.NOBLE
                                    ? 0
                                    : (int) Math.ceil(count * homeGuard);
                    if (count - guard > 0) {
                        result.put(type, count - guard);
                    }
                });
        return result;
    }

    /** The units of {@code types} in {@code units} (counts &gt; 0), in a new map. */
    public static Map<UnitType, Integer> only(Map<UnitType, Integer> units, List<UnitType> types) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        for (UnitType type : types) {
            int count = units.getOrDefault(type, 0);
            if (count > 0) {
                result.put(type, count);
            }
        }
        return result;
    }

    /** {@code a + b} per type, in a new map. */
    public static Map<UnitType, Integer> plus(Map<UnitType, Integer> a, Map<UnitType, Integer> b) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        result.putAll(a);
        b.forEach((type, count) -> result.merge(type, count, Integer::sum));
        return result;
    }

    /** {@code a − b} per type, dropping what reaches 0. */
    public static Map<UnitType, Integer> minus(Map<UnitType, Integer> a, Map<UnitType, Integer> b) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        a.forEach(
                (type, count) -> {
                    int left = count - b.getOrDefault(type, 0);
                    if (left > 0) {
                        result.put(type, left);
                    }
                });
        return result;
    }
}
