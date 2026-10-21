package de.bannerfall.npc;

import de.bannerfall.gamedata.AttackType;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Lessons;
import de.bannerfall.npc.NpcMemory.Tally;
import de.bannerfall.npc.NpcMemory.WallGoal;
import de.bannerfall.report.BattleReport;
import de.bannerfall.rules.BuildRules;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Learning lords (09-npc-lords §15), the pure part: what an attack on the lord adds to its tally,
 * how the tally fades, what the human wins of the world look like, and how both lessons blend into
 * the template's mix and set a wall goal. Everything here takes plain values and is unit-tested.
 */
final class NpcLearning {

    /** Guild mates' tallies count this much (09 §15.1). */
    static final double MATE_WEIGHT = 0.5;

    /** The admin card shows this many of the largest mix changes (09 §15.5). */
    static final int MAX_CHANGES = 4;

    /** Mix shares come out per mille: {@link NpcPlanning#pickUnit} only needs their ratios. */
    static final int MIX_SCALE = 1000;

    /** Units that never count in a lesson: they are no part of the mix (09 §5.3). */
    private static final Set<UnitType> OUTSIDE_MIX =
            Set.of(UnitType.NOBLE, UnitType.PALADIN, UnitType.BANNER_THIEF);

    /** Units the wins never teach: a DEFENSIVE lord's infantry stays (09 §15.2). */
    private static final Set<UnitType> KEPT_FROM_WINS =
            Set.of(UnitType.SPEARMAN, UnitType.SWORDSMAN);

    private NpcLearning() {}

    /**
     * What one lesson set teaches a lord: the weight of each lesson (shares of the army's
     * population moved away from the template) and its target mix (population shares, sum 1).
     *
     * @param attackUnits the units the wins teach ({@link #copiedUnits}): the part of the mix the
     *     attack lesson works in (09 §15.3)
     */
    record Lesson(
            double defenceWeight,
            Map<UnitType, Double> defence,
            double attackWeight,
            Map<UnitType, Double> attack,
            Set<UnitType> attackUnits) {
        static final Lesson NONE = new Lesson(0, Map.of(), 0, Map.of(), Set.of());

        Lesson {
            defence = Map.copyOf(defence);
            attack = Map.copyOf(attack);
            attackUnits = Set.copyOf(attackUnits);
        }

        boolean active() {
            return defenceWeight > 0 || attackWeight > 0;
        }
    }

    /**
     * The human wins of the world (09 §15.2), faded to now.
     *
     * @param count the faded number of wins
     * @param population per unit type, the faded population the winning armies brought
     */
    record Wins(double count, Map<UnitType, Double> population) {
        static final Wins NONE = new Wins(0, Map.of());

        Wins {
            population = Map.copyOf(population);
        }
    }

    /** One won human attack: when it happened and the units it brought. */
    record Win(Instant at, Map<UnitType, Integer> units) {}

    /** {@code 0.5^(hours / halfLife)}: the weight of something {@code hours} old. */
    static double fade(double hours, double halfLifeHours) {
        return Math.pow(0.5, Math.max(0, hours) / halfLifeHours);
    }

    private static double hoursBetween(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / 3_600_000.0;
    }

    /** The tally's values as of {@code now} (never grown: an earlier {@code now} keeps them). */
    static Tally fadedTo(Tally tally, Instant now, double halfLifeHours) {
        if (tally.at() == null) {
            return new Tally(now, 0, 0, 0, 0, 0);
        }
        if (!now.isAfter(tally.at())) {
            return tally;
        }
        return scaled(tally, now, fade(hoursBetween(tally.at(), now), halfLifeHours));
    }

    private static Tally scaled(Tally t, Instant at, double factor) {
        return new Tally(
                at,
                t.infantry() * factor,
                t.cavalry() * factor,
                t.siege() * factor,
                t.attacks() * factor,
                t.scoutings() * factor);
    }

    /**
     * {@code base} with {@code event} added; the later of the two moments is the new one, the older
     * part fades to it.
     */
    static Tally plus(Tally base, Tally event, double halfLifeHours) {
        if (base.at() == null) {
            return event;
        }
        if (event.at() == null) {
            return base;
        }
        Tally a = base;
        Tally b = event;
        if (b.at().isBefore(a.at())) {
            a = event;
            b = base;
        }
        Tally older = fadedTo(a, b.at(), halfLifeHours);
        return new Tally(
                b.at(),
                older.infantry() + b.infantry(),
                older.cavalry() + b.cavalry(),
                older.siege() + b.siege(),
                older.attacks() + b.attacks(),
                older.scoutings() + b.scoutings());
    }

    /**
     * What one attack on the lord adds to its tally (09 §15.1): scouts only count as a scouting;
     * otherwise one attack with its attack power by type (count × attack; rams and catapults as
     * siege, by population).
     */
    static Tally tallyOf(Map<UnitType, Integer> units, Instant at, GameData data) {
        double infantry = 0;
        double cavalry = 0;
        double siege = 0;
        boolean onlyScouts = true;
        boolean any = false;
        for (var entry : units.entrySet()) {
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            any = true;
            UnitType type = entry.getKey();
            if (type != UnitType.SCOUT) {
                onlyScouts = false;
            }
            UnitDef def = data.unit(type);
            if (type == UnitType.RAM || type == UnitType.CATAPULT) {
                siege += (double) count * def.pop();
            } else if (def.attackType() == AttackType.CAVALRY) {
                cavalry += (double) count * def.attack();
            } else {
                infantry += (double) count * def.attack();
            }
        }
        if (!any) {
            return new Tally(at, 0, 0, 0, 0, 0);
        }
        if (onlyScouts) {
            return new Tally(at, 0, 0, 0, 0, 1);
        }
        return new Tally(at, infantry, cavalry, siege, 1, 0);
    }

    /**
     * A DEFENSE report about an attack on the lord into its lessons (09 §15.1, §15.4): the tally,
     * and for an attack with rams or catapults the wall goal of the attacked village.
     */
    static Lessons learn(
            Lessons lessons,
            Instant at,
            BattleReport battle,
            GameData data,
            NpcBalanceSettings.Learning learning) {
        Map<UnitType, Integer> units = battle.attackerUnits();
        Tally seen = plus(lessons.seen(), tallyOf(units, at, data), learning.halfLifeHours());
        Map<Long, WallGoal> walls = lessons.walls();
        boolean siege =
                units.getOrDefault(UnitType.RAM, 0) > 0
                        || units.getOrDefault(UnitType.CATAPULT, 0) > 0;
        if (siege && battle.defenderSide() != null) {
            long villageId = battle.defender().id();
            int max = data.building(BuildingType.WALL).maxLevel();
            int goal = Math.min(max, battle.defenderSide().wallAfter() + learning.wallStep());
            Instant until = at.plus(hours(learning.wallHours()));
            WallGoal old = walls.get(villageId);
            if (old != null && old.until().isAfter(at)) {
                goal = Math.max(goal, old.level());
                until = old.until().isAfter(until) ? old.until() : until;
            }
            walls = new HashMap<>(walls);
            walls.put(villageId, new WallGoal(goal, until));
        }
        return new Lessons(seen, walls);
    }

    private static Duration hours(double hours) {
        return Duration.ofSeconds(Math.round(hours * 3600));
    }

    /** The lessons without wall goals that ran out at {@code now}. */
    static Lessons prune(Lessons lessons, Instant now) {
        if (lessons.walls().values().stream().allMatch(g -> g.until().isAfter(now))) {
            return lessons;
        }
        Map<Long, WallGoal> walls = new HashMap<>(lessons.walls());
        walls.values().removeIf(g -> !g.until().isAfter(now));
        return new Lessons(lessons.seen(), walls);
    }

    /**
     * The build steps with {@code { WALL: goal }} in front (09 §15.4) while the goal is above the
     * wall's level (queued levels included) and the wall's requirements are met; else the steps as
     * they are.
     *
     * @param levels current levels plus queued orders
     */
    static List<Map<BuildingType, Integer>> withWallGoal(
            List<Map<BuildingType, Integer>> steps,
            Integer goal,
            Map<BuildingType, Integer> levels,
            BuildingDef wall) {
        if (goal == null
                || levels.get(BuildingType.WALL) >= goal
                || !BuildRules.missingRequirements(wall.requires(), levels).isEmpty()) {
            return steps;
        }
        List<Map<BuildingType, Integer>> result = new ArrayList<>(steps.size() + 1);
        result.add(Map.of(BuildingType.WALL, goal));
        result.addAll(steps);
        return result;
    }

    /** The village's wall goal at {@code now}, or null if it has none (or it ran out). */
    static Integer wallGoal(Lessons lessons, long villageId, Instant now) {
        WallGoal goal = lessons.walls().get(villageId);
        return goal == null || !goal.until().isAfter(now) ? null : goal.level();
    }

    /**
     * The human wins of the world (09 §15.2), each faded by its age at {@code now}: scouts-only
     * attacks don't count, nobles, the paladin and banner thieves are left out of the population.
     */
    static Wins winsOf(Collection<Win> wins, Instant now, double halfLifeHours, GameData data) {
        double count = 0;
        Map<UnitType, Double> population = new EnumMap<>(UnitType.class);
        for (Win win : wins) {
            boolean onlyScouts =
                    win.units().entrySet().stream()
                            .filter(e -> e.getValue() > 0)
                            .allMatch(e -> e.getKey() == UnitType.SCOUT);
            if (onlyScouts) {
                continue;
            }
            double weight = fade(hoursBetween(win.at(), now), halfLifeHours);
            count += weight;
            win.units()
                    .forEach(
                            (type, n) -> {
                                if (n > 0 && !OUTSIDE_MIX.contains(type)) {
                                    population.merge(
                                            type, weight * n * data.unit(type).pop(), Double::sum);
                                }
                            });
        }
        return new Wins(count, population);
    }

    /**
     * Both lessons of a lord at {@code now} (09 §15.1–§15.3).
     *
     * @param own the lord's own tally
     * @param mates the tallies of its guild mates (half weight)
     * @param wins the human wins of the world
     * @param attackUnits the lord's {@code attack-units}: the wins teach proportions among these
     *     (spearmen and swordsmen left out) and scouts only
     */
    static Lesson lesson(
            Tally own,
            List<Tally> mates,
            Wins wins,
            Set<UnitType> attackUnits,
            Instant now,
            NpcBalanceSettings.Learning learning) {
        double h = learning.halfLifeHours();
        Tally seen = fadedTo(own, now, h);
        for (Tally mate : mates) {
            Tally faded = fadedTo(mate, now, h);
            seen =
                    new Tally(
                            now,
                            seen.infantry() + MATE_WEIGHT * faded.infantry(),
                            seen.cavalry() + MATE_WEIGHT * faded.cavalry(),
                            seen.siege() + MATE_WEIGHT * faded.siege(),
                            seen.attacks() + MATE_WEIGHT * faded.attacks(),
                            seen.scoutings() + MATE_WEIGHT * faded.scoutings());
        }
        Map<UnitType, Double> defence = defenceMix(seen, learning.scoutShare());
        Set<UnitType> copied = copiedUnits(attackUnits);
        Map<UnitType, Double> attack = attackMix(wins, copied);
        double e1 = defence.isEmpty() ? 0 : Math.min(1, seen.attacks() / learning.evidence());
        double e2 = attack.isEmpty() ? 0 : Math.min(1, wins.count() / learning.evidence());
        if (e1 + e2 <= 0) {
            return Lesson.NONE;
        }
        double weight = learning.maxShift() * Math.max(e1, e2);
        return new Lesson(
                weight * e1 / (e1 + e2),
                e1 > 0 ? defence : Map.of(),
                weight * e2 / (e1 + e2),
                e2 > 0 ? attack : Map.of(),
                copied);
    }

    /**
     * The units the wins teach a lord (09 §15.2): its attack units and scouts, without spearmen and
     * swordsmen, so a DEFENSIVE lord (which attacks with them) keeps its infantry.
     */
    static Set<UnitType> copiedUnits(Set<UnitType> attackUnits) {
        Set<UnitType> result = EnumSet.of(UnitType.SCOUT);
        result.addAll(attackUnits);
        result.removeAll(KEPT_FROM_WINS);
        return result;
    }

    /**
     * The defence lesson's target (09 §15.1): spearmen for the cavalry share of the attack power
     * seen, swordsmen for the rest, and scouts for the share of scoutings. Empty if no attack was
     * seen.
     */
    static Map<UnitType, Double> defenceMix(Tally seen, double scoutShare) {
        double power = seen.infantry() + seen.cavalry();
        if (seen.attacks() <= 0 || power <= 0) {
            return Map.of();
        }
        double scouts = scoutShare * seen.scoutings() / (seen.scoutings() + seen.attacks());
        double cavalry = seen.cavalry() / power;
        Map<UnitType, Double> mix = new EnumMap<>(UnitType.class);
        put(mix, UnitType.SPEARMAN, cavalry * (1 - scouts));
        put(mix, UnitType.SWORDSMAN, (1 - cavalry) * (1 - scouts));
        put(mix, UnitType.SCOUT, scouts);
        return mix;
    }

    /**
     * The attack lesson's target (09 §15.2): the population shares of the winning armies among the
     * given units ({@link #copiedUnits}) and scouts. Empty without wins or without any of those
     * units in them.
     */
    static Map<UnitType, Double> attackMix(Wins wins, Set<UnitType> attackUnits) {
        double total = 0;
        Map<UnitType, Double> mix = new EnumMap<>(UnitType.class);
        for (var entry : wins.population().entrySet()) {
            UnitType type = entry.getKey();
            if (attackUnits.contains(type) || type == UnitType.SCOUT) {
                mix.put(type, entry.getValue());
                total += entry.getValue();
            }
        }
        if (total <= 0) {
            return Map.of();
        }
        double sum = total;
        mix.replaceAll((type, value) -> value / sum);
        return mix;
    }

    /**
     * The changes of the learned mix against the template (09 §15.5), in whole percentage points of
     * the army's population: the largest {@link #MAX_CHANGES} of at least one point, largest first
     * (ties in enum order).
     */
    static List<NpcLordDto.MixChange> changes(
            Map<UnitType, Integer> template, Map<UnitType, Integer> learned) {
        double templateSum = template.values().stream().mapToInt(Integer::intValue).sum();
        double learnedSum = learned.values().stream().mapToInt(Integer::intValue).sum();
        List<NpcLordDto.MixChange> changes = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            double before = templateSum == 0 ? 0 : template.getOrDefault(type, 0) / templateSum;
            double after = learnedSum == 0 ? 0 : learned.getOrDefault(type, 0) / learnedSum;
            int points = (int) Math.round((after - before) * 100);
            if (points != 0) {
                changes.add(new NpcLordDto.MixChange(type, points));
            }
        }
        changes.sort(
                java.util.Comparator.comparingInt((NpcLordDto.MixChange c) -> -Math.abs(c.points()))
                        .thenComparing(NpcLordDto.MixChange::unit));
        return List.copyOf(changes.subList(0, Math.min(MAX_CHANGES, changes.size())));
    }

    private static void put(Map<UnitType, Double> mix, UnitType type, double share) {
        if (share > 0) {
            mix.put(type, share);
        }
    }

    /**
     * The learned mix (09 §15.3): {@code (1 − L) × template + L1 × defence + L2 × attack'}, the
     * template's shares normalized first, per mille. {@code attack'} is the template with its
     * attack part (the units of {@link #copiedUnits}, together the share {@code a}) replaced by
     * {@code a ×} the attack lesson's mix: the wins only change proportions among the units the
     * lord sends. Without an active lesson: the template.
     */
    static Map<UnitType, Integer> learnedMix(Map<UnitType, Integer> template, Lesson lesson) {
        if (!lesson.active()) {
            return template;
        }
        double templateSum = template.values().stream().mapToInt(Integer::intValue).sum();
        double rest = 1 - lesson.defenceWeight() - lesson.attackWeight();
        Map<UnitType, Double> shares = new EnumMap<>(UnitType.class);
        if (templateSum > 0) {
            template.forEach(
                    (type, share) -> shares.merge(type, rest * share / templateSum, Double::sum));
        }
        lesson.defence()
                .forEach(
                        (type, share) ->
                                shares.merge(type, lesson.defenceWeight() * share, Double::sum));
        double attackPart = 0;
        if (templateSum > 0) {
            for (var entry : template.entrySet()) {
                double share = entry.getValue() / templateSum;
                if (lesson.attackUnits().contains(entry.getKey())) {
                    attackPart += share;
                } else {
                    shares.merge(entry.getKey(), lesson.attackWeight() * share, Double::sum);
                }
            }
        }
        double part = attackPart;
        lesson.attack()
                .forEach(
                        (type, share) ->
                                shares.merge(
                                        type, lesson.attackWeight() * part * share, Double::sum));
        Map<UnitType, Integer> mix = new EnumMap<>(UnitType.class);
        shares.forEach(
                (type, share) -> {
                    int perMille = (int) Math.round(share * MIX_SCALE);
                    if (perMille > 0) {
                        mix.put(type, perMille);
                    }
                });
        return mix;
    }
}
