package de.bannerfall.statistics;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BattleResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * What one battle adds to the statistics of the players in it (02-game-rules §24). Pure: the battle
 * code passes in what it knows and writes the result with {@link StatisticsService#add}.
 *
 * <ul>
 *   <li>Kills and losses count in every battle, scouts included.
 *   <li>Won and lost attacks and defences need a main battle: scouts alone count as scouting.
 *   <li>The defenders are the village's owner and every other player whose stack there had units
 *       (never the attacker himself); each counts the defence once. The attackers they killed are
 *       shared out by the population each brought, the rest of the division to the biggest stack
 *       (the owner's on a tie); in an empty village the owner gets them all.
 *   <li>An attack on a village of the attacker's own counts for nothing.
 * </ul>
 */
public final class BattleStats {

    private BattleStats() {}

    /**
     * @param attackerId the attacking player (never null: barbarians don't attack)
     * @param ownerId the target's owner before the battle, null for barbarians
     * @param defenderStacks the units of each defending stack before the battle
     * @param stackOwners the player of each stack (null for barbarians)
     * @param loot the resources the attack took
     * @param pop the population of one unit of a type
     * @return what to add, per player and stat; only values above 0
     */
    public static Map<Long, Map<Stat, Long>> of(
            long attackerId,
            Long ownerId,
            List<Map<UnitType, Integer>> defenderStacks,
            List<Long> stackOwners,
            BattleResult result,
            long loot,
            ToIntFunction<UnitType> pop) {
        Map<Long, Map<Stat, Long>> deltas = new LinkedHashMap<>();
        if (ownerId != null && ownerId == attackerId) {
            return deltas;
        }
        long attackerLost = population(result.attackerLosses(), pop);
        add(deltas, attackerId, Stat.UNITS_LOST, attackerLost);
        long killed = 0;
        for (int i = 0; i < result.defenderLosses().size(); i++) {
            Long owner = stackOwners.get(i);
            long lost = population(result.defenderLosses().get(i), pop);
            add(deltas, owner, Stat.UNITS_LOST, lost);
            if (!Objects.equals(owner, attackerId)) {
                killed += lost;
            }
        }
        add(deltas, attackerId, Stat.KILLED_ATTACKING, killed);

        List<Long> defenders = new ArrayList<>();
        List<Long> brought = new ArrayList<>();
        if (ownerId != null) {
            defenders.add(ownerId);
            brought.add(0L);
        }
        for (int i = 0; i < stackOwners.size(); i++) {
            Long owner = stackOwners.get(i);
            long units = population(defenderStacks.get(i), pop);
            if (owner == null || owner == attackerId || (units == 0 && !owner.equals(ownerId))) {
                continue;
            }
            int index = defenders.indexOf(owner);
            if (index < 0) {
                defenders.add(owner);
                brought.add(units);
            } else {
                brought.set(index, brought.get(index) + units);
            }
        }
        shareKills(deltas, defenders, brought, attackerLost);

        if (result.mainBattle() != null) {
            boolean won = result.attackerWon();
            add(deltas, attackerId, won ? Stat.ATTACKS_WON : Stat.ATTACKS_LOST, 1);
            for (Long defender : defenders) {
                add(deltas, defender, won ? Stat.DEFENCES_LOST : Stat.DEFENCES_WON, 1);
            }
        }
        add(deltas, attackerId, Stat.LOOT_TAKEN, loot);
        add(deltas, ownerId, Stat.LOOT_LOST, loot);
        return deltas;
    }

    /** The attackers' dead, shared by the population each defender brought. */
    private static void shareKills(
            Map<Long, Map<Stat, Long>> deltas,
            List<Long> defenders,
            List<Long> brought,
            long killed) {
        if (defenders.isEmpty() || killed == 0) {
            return;
        }
        long total = brought.stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            // nobody stood there: the wall (or nothing) did it, the owner gets them
            add(deltas, defenders.get(0), Stat.KILLED_DEFENDING, killed);
            return;
        }
        int biggest = 0;
        long given = 0;
        for (int i = 0; i < defenders.size(); i++) {
            long share = killed * brought.get(i) / total;
            add(deltas, defenders.get(i), Stat.KILLED_DEFENDING, share);
            given += share;
            if (brought.get(i) > brought.get(biggest)) {
                biggest = i;
            }
        }
        add(deltas, defenders.get(biggest), Stat.KILLED_DEFENDING, killed - given);
    }

    private static long population(Map<UnitType, Integer> units, ToIntFunction<UnitType> pop) {
        long total = 0;
        for (var entry : units.entrySet()) {
            total += (long) entry.getValue() * pop.applyAsInt(entry.getKey());
        }
        return total;
    }

    private static void add(Map<Long, Map<Stat, Long>> deltas, Long player, Stat stat, long by) {
        if (player == null || by <= 0) {
            return;
        }
        deltas.computeIfAbsent(player, id -> new EnumMap<>(Stat.class)).merge(stat, by, Long::sum);
    }
}
