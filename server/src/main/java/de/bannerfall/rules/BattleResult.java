package de.bannerfall.rules;

import de.bannerfall.gamedata.UnitType;
import java.util.List;
import java.util.Map;

/**
 * What {@link Combat#fight} decided. Unit maps hold only the types with a count &gt; 0 (missing
 * types mean 0) and can't be changed. Loot is not in here: it needs the target's stock, see {@link
 * Loot}.
 *
 * @param scouting the scouting phase; null if the attack had no scouts
 * @param mainBattle the main battle; null if there was none (only scouts attacked)
 * @param attackerLosses attacking units that died, in the scouting phase and in the main battle
 * @param attackerSurvivors attacking units that are still alive (they return with the loot)
 * @param defenderLosses the losses of each input stack, in the order of {@link
 *     BattleInput#defenderStacks()}
 * @param wallAfter the wall level after the battle: lowered by the surviving rams (and catapults
 *     aimed at the wall) if the attacker won, else unchanged
 * @param catapultTargetLevelAfter the catapult target's level after the battle (equal to {@code
 *     wallAfter} for {@code WALL}); unchanged if the attacker lost or no catapult survived, 0
 *     without a target
 */
public record BattleResult(
        Scouting scouting,
        MainBattle mainBattle,
        Map<UnitType, Integer> attackerLosses,
        Map<UnitType, Integer> attackerSurvivors,
        List<Map<UnitType, Integer>> defenderLosses,
        int wallAfter,
        int catapultTargetLevelAfter) {

    /**
     * True if there was a main battle and the attacker won it: only then do rams, catapults, loot
     * and nobles do their work.
     */
    public boolean attackerWon() {
        return mainBattle != null && mainBattle.attackerWon();
    }

    /**
     * The scouting phase (02-game-rules §7.1).
     *
     * @param sent attacking scouts
     * @param defending all scouts in the target village (every stack); they never die here
     * @param lost attacking scouts that died in the scouting phase (the main battle may kill more)
     * @param succeeded true if the attacker had more scouts: the report may show what they saw
     */
    public record Scouting(int sent, int defending, int lost, boolean succeeded) {}

    /**
     * The main battle (02-game-rules §8).
     *
     * @param attack A: the attack strength with luck
     * @param defense D: the defence strength of all stacks with wall and base defence
     * @param effectiveWall Wb: the wall level that counted in the battle (half of the rams sent
     *     lower it for the battle only)
     * @param attackerWon true if A &gt; D (a tie goes to the defender)
     * @param attackerLossRatio share of every attacking unit type that died, 1 if the defender won
     * @param defenderLossRatio share of every defending unit type that died, in every stack, 1 if
     *     the attacker won
     */
    public record MainBattle(
            double attack,
            double defense,
            int effectiveWall,
            boolean attackerWon,
            double attackerLossRatio,
            double defenderLossRatio) {}
}
