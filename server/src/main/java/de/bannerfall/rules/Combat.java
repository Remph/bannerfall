package de.bannerfall.rules;

import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.AttackType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitStats;
import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One battle (02-game-rules §7–§9.2): scouting phase, main battle, losses per stack, and after a
 * won battle the damage of the rams and catapults. Pure: luck is drawn by the caller ({@link
 * #drawLuck}), loot is {@link Loot} (it needs the target's stock), loyalty and conquest are M11.
 */
public final class Combat {

    /** Every village defends itself with this much even without units (02-game-rules §8). */
    private static final double BASE_DEFENSE = 20;

    private Combat() {}

    /**
     * Fights one battle in the order of 02-game-rules §7: scouting phase (if scouts came along),
     * main battle (if the attackers have any attack strength), and after a won main battle the
     * surviving rams and catapults. Paladin standards (§21.18) raise the strength of the units they
     * cover and make rams and catapults count for more, in the battle and after it.
     */
    public static BattleResult fight(GameData data, BattleInput input) {
        Map<UnitType, Integer> attacker = input.attacker();

        int scoutsSent = attacker.getOrDefault(UnitType.SCOUT, 0);
        BattleResult.Scouting scouting =
                scoutsSent > 0
                        ? scout(scoutsSent, count(input.defenderStacks(), UnitType.SCOUT))
                        : null;

        BattleResult.MainBattle battle = mainBattle(data, input);

        // The scouts that survived the scouting phase share the attacker's loss ratio.
        var attackerLosses = new EnumMap<UnitType, Integer>(UnitType.class);
        var survivors = new EnumMap<UnitType, Integer>(UnitType.class);
        for (var entry : attacker.entrySet()) {
            UnitType type = entry.getKey();
            int lostScouting = type == UnitType.SCOUT && scouting != null ? scouting.lost() : 0;
            int alive = entry.getValue() - lostScouting;
            int lostBattle = battle == null ? 0 : losses(alive, battle.attackerLossRatio());
            putIfPositive(attackerLosses, type, lostScouting + lostBattle);
            putIfPositive(survivors, type, alive - lostBattle);
        }

        List<Map<UnitType, Integer>> defenderLosses = new ArrayList<>();
        for (Map<UnitType, Integer> stack : input.defenderStacks()) {
            defenderLosses.add(
                    battle == null ? Map.of() : losses(stack, battle.defenderLossRatio()));
        }

        int wallAfter = input.wallLevel();
        int targetLevelAfter = input.catapultTargetLevel();
        if (battle != null && battle.attackerWon()) {
            // 02-game-rules §9.1/§9.2: the surviving rams first, then the surviving catapults; a
            // Ram or Siege Standard makes them count for more (§21.18)
            BuildingType target = input.catapultTarget();
            StandardEffect standard = input.attackerStandard();
            wallAfter =
                    Siege.wallAfterRams(
                            wallAfter,
                            BannerRules.boosted(
                                    survivors.getOrDefault(UnitType.RAM, 0), standard.rams()));
            if (target == BuildingType.WALL) {
                targetLevelAfter = wallAfter;
            }
            int cats =
                    BannerRules.boosted(
                            survivors.getOrDefault(UnitType.CATAPULT, 0), standard.catapults());
            if (cats > 0) {
                targetLevelAfter =
                        Siege.levelAfterCatapults(
                                targetLevelAfter, cats, Siege.catapultMinLevel(target));
                if (target == BuildingType.WALL) {
                    wallAfter = targetLevelAfter;
                }
            }
        }

        return new BattleResult(
                scouting,
                battle,
                Collections.unmodifiableMap(attackerLosses),
                Collections.unmodifiableMap(survivors),
                Collections.unmodifiableList(defenderLosses),
                wallAfter,
                targetLevelAfter);
    }

    /**
     * Luck for one battle, uniform in {@code [−luckRange, +luckRange)} with one draw.
     *
     * @param luckRange the world setting, in [0, 1]
     */
    public static double drawLuck(RandomSource random, double luckRange) {
        if (!(luckRange >= 0 && luckRange <= 1)) {
            throw new IllegalArgumentException("luckRange must be in [0, 1]: " + luckRange);
        }
        return random.nextDouble(-luckRange, luckRange);
    }

    /**
     * D: {@code (unitDefense + 20 + 50 × Wb) × (1 + 0.05 × Wb)}.
     *
     * @param unitDefense the defence of all defending units against this attack's mix of infantry
     *     and cavalry
     * @param effectiveWall Wb, the wall level during the battle
     */
    public static double defenseStrength(double unitDefense, int effectiveWall) {
        return (unitDefense + BASE_DEFENSE + 50.0 * effectiveWall) * (1 + 0.05 * effectiveWall);
    }

    /**
     * The share of the winner's units that die: {@code (weaker / stronger)^1.5}. 0 if the weaker
     * side has no strength at all.
     */
    public static double lossRatio(double weaker, double stronger) {
        if (!(stronger > 0 && weaker >= 0 && weaker <= stronger)) {
            throw new IllegalArgumentException(
                    "need 0 <= weaker <= stronger, stronger > 0: " + weaker + ", " + stronger);
        }
        return Math.pow(weaker / stronger, 1.5);
    }

    /**
     * 02-game-rules §7.1: more scouts than the defender → they lose {@code round(Sa ×
     * (Sd/Sa)^1.5)}, else all of them die.
     */
    private static BattleResult.Scouting scout(int sent, int defending) {
        if (sent > defending) {
            int lost = losses(sent, lossRatio(defending, sent));
            return new BattleResult.Scouting(sent, defending, lost, true);
        }
        return new BattleResult.Scouting(sent, defending, sent, false);
    }

    /** 02-game-rules §8; null if the attackers have no attack strength (only scouts). */
    private static BattleResult.MainBattle mainBattle(GameData data, BattleInput input) {
        double infantry = 0;
        double cavalry = 0;
        StandardEffect attackStandard = input.attackerStandard();
        for (var entry : input.attacker().entrySet()) {
            UnitDef unit = data.unit(entry.getKey());
            double strength =
                    (double) entry.getValue()
                            * unit.stats(input.attackerLevels().get(entry.getKey())).attack();
            // 02-game-rules §21.18: the attack's paladin standard for the units it covers
            double standard = attackStandard.attack(entry.getKey());
            if (standard > 0) {
                strength *= 1 + standard;
            }
            if (unit.attackType() == AttackType.CAVALRY) {
                cavalry += strength;
            } else {
                infantry += strength;
            }
        }
        if (infantry + cavalry == 0) {
            return null;
        }

        double total = infantry + cavalry;
        // 02-game-rules §21.2: the War banner on top of luck, A × (1 + luck) × (1 + b)
        double attack = total * (1 + input.luck()) * (1 + input.attackBonus());

        int rams =
                BannerRules.boosted(
                        input.attacker().getOrDefault(UnitType.RAM, 0), attackStandard.rams());
        int effectiveWall = Siege.wallAfterRams(input.wallLevel(), rams / 2);
        // count × (defInf × shareInf + defCav × shareCav), summed over whole numbers and divided
        // once at the end: the shares (e.g. 0.4) aren't exact doubles, this way D is exact
        // whenever the rulebook's value is, so ties and x.5 losses come out as calculated by hand.
        double weighted = 0;
        for (int i = 0; i < input.defenderStacks().size(); i++) {
            UnitLevels levels = input.defenderLevels().get(i);
            StandardEffect standard = input.defenderStandards().get(i);
            for (var entry : input.defenderStacks().get(i).entrySet()) {
                UnitStats stats = data.unit(entry.getKey()).stats(levels.get(entry.getKey()));
                double defence =
                        entry.getValue()
                                * (stats.defenseInfantry() * infantry
                                        + stats.defenseCavalry() * cavalry);
                // the stack's own paladin standard for the units it covers (§21.18)
                double bonus = standard.defence(entry.getKey());
                weighted += bonus > 0 ? defence * (1 + bonus) : defence;
            }
        }
        // the Bulwark banner after the wall term, D × (1 + b), then the night bonus (§8.1)
        double defense =
                defenseStrength(weighted / total, effectiveWall)
                        * (1 + input.defenceBonus())
                        * input.nightFactor();

        if (attack > defense) {
            return new BattleResult.MainBattle(
                    attack, defense, effectiveWall, true, lossRatio(defense, attack), 1);
        }
        return new BattleResult.MainBattle(
                attack, defense, effectiveWall, false, 1, lossRatio(attack, defense));
    }

    /** Losses of every type of one stack: {@code round(count × ratio)}, rounded per stack. */
    private static Map<UnitType, Integer> losses(Map<UnitType, Integer> stack, double ratio) {
        var lost = new EnumMap<UnitType, Integer>(UnitType.class);
        stack.forEach((type, count) -> putIfPositive(lost, type, losses(count, ratio)));
        return Collections.unmodifiableMap(lost);
    }

    private static int losses(int count, double ratio) {
        return (int) Math.round(count * ratio);
    }

    private static int count(List<Map<UnitType, Integer>> stacks, UnitType type) {
        int sum = 0;
        for (Map<UnitType, Integer> stack : stacks) {
            sum = Math.addExact(sum, stack.getOrDefault(type, 0));
        }
        return sum;
    }

    private static void putIfPositive(Map<UnitType, Integer> map, UnitType type, int count) {
        if (count > 0) {
            map.put(type, count);
        }
    }

    /**
     * An unchangeable copy of a unit map without the zero counts (used by {@link BattleInput}).
     *
     * @throws IllegalArgumentException for a negative count
     */
    static Map<UnitType, Integer> copyUnits(Map<UnitType, Integer> units) {
        var copy = new EnumMap<UnitType, Integer>(UnitType.class);
        units.forEach(
                (type, count) -> {
                    if (count < 0) {
                        throw new IllegalArgumentException(
                                "negative count for " + type + ": " + count);
                    }
                    putIfPositive(copy, type, count);
                });
        return Collections.unmodifiableMap(copy);
    }
}
