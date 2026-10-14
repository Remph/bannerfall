package de.bannerfall.simulator;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * The answer of POST /api/simulator: the same battle with the luck at its worst for the attacker,
 * at none and at its best (the world's {@code luckRange}); one scenario only if the world has no
 * luck. Unit maps hold only the types with a count above 0.
 *
 * @param scenarios ordered from the worst luck to the best
 * @param attackBonus the War banner the battles were fought with, as a fraction (M32c); 0 without
 * @param defenceBonus the Bulwark banner, as a fraction, after the hall's cap; 0 without
 * @param plunderBonus the Plunder banner the loot capacity was counted with, as a fraction (code
 *     review 1.2); 0 without
 * @param oathBonus the Oath banner the loyalty outlook was counted with, as a fraction, after the
 *     hall's cap; 0 without
 */
public record SimulationDto(
        List<Scenario> scenarios,
        double attackBonus,
        double defenceBonus,
        double plunderBonus,
        double oathBonus) {

    /**
     * One battle.
     *
     * @param luck the luck used, e.g. {@code -0.15}
     * @param outcome who wins, or SCOUTS_ONLY if only scouts attack
     * @param attack the attack strength with luck; null for SCOUTS_ONLY
     * @param defense the defence strength with the wall; null for SCOUTS_ONLY
     * @param effectiveWall the wall level that counted in the battle (half the rams lower it); null
     *     for SCOUTS_ONLY
     * @param attackerLosses the attackers that die (scouting phase and main battle)
     * @param attackerSurvivors the attackers that come back
     * @param defenderLosses the defenders that die
     * @param defenderSurvivors the defenders that are left
     * @param wallBefore the wall level before the battle
     * @param wallAfter the wall level after it
     * @param catapultTarget what the catapults aim at; null without catapults
     * @param catapultTargetLevelBefore that building's level before; null without catapults
     * @param catapultTargetLevelAfter and after the battle; null without catapults
     * @param scouting null if the attack has no scouts
     * @param loyalty null unless the attacker wins with surviving nobles
     * @param lootCapacity what the survivors can carry home, with the Plunder banner (the stock
     *     decides what they take)
     * @param theft what the banner thieves could do (M32c); null without thieves in the attack or
     *     with banners switched off
     */
    public record Scenario(
            double luck,
            BattleOutcome outcome,
            @Schema(nullable = true) Double attack,
            @Schema(nullable = true) Double defense,
            @Schema(nullable = true) Integer effectiveWall,
            Map<UnitType, Integer> attackerLosses,
            Map<UnitType, Integer> attackerSurvivors,
            Map<UnitType, Integer> defenderLosses,
            Map<UnitType, Integer> defenderSurvivors,
            int wallBefore,
            int wallAfter,
            @Schema(nullable = true) BuildingType catapultTarget,
            @Schema(nullable = true) Integer catapultTargetLevelBefore,
            @Schema(nullable = true) Integer catapultTargetLevelAfter,
            @Schema(nullable = true) Scouting scouting,
            @Schema(nullable = true) LoyaltyOutlook loyalty,
            int lootCapacity,
            @Schema(nullable = true) Theft theft) {}

    /**
     * The banner thieves of the attack (02-game-rules §21.6.2): the chance that the survivors take
     * the banner, if one flies in the village (the simulator doesn't know whether one does). 0 if
     * the battle is lost or no thief survives; a battle that conquers the village takes the banner
     * anyway (§21.6.1).
     *
     * @param sent thieves in the attack
     * @param survived thieves alive after the battle
     * @param chance the chance of the roll, with the hall level after the battle
     */
    public record Theft(int sent, int survived, double chance) {}

    /**
     * The scouting phase (02-game-rules §7.1).
     *
     * @param sent the attacking scouts
     * @param defending the scouts in the village
     * @param lost the attacking scouts that die in the scouting phase
     * @param succeeded the attacker has more scouts, so the report would show what they saw
     */
    public record Scouting(int sent, int defending, int lost, boolean succeeded) {}

    /**
     * What the surviving nobles do (02-game-rules §10): every noble takes 20 to 35, less the
     * village's Oath banner ({@code round(hit × (1 − oath))}, §21.2), so the result is a range.
     *
     * @param nobles the surviving nobles
     * @param before the loyalty before
     * @param highest the loyalty after, if every noble hits 20 (less the Oath; never below 0)
     * @param lowest the loyalty after, if every noble hits 35 (less the Oath; never below 0)
     * @param conquered NO if even the lowest hits leave the village standing, YES if even the
     *     weakest hits conquer it, POSSIBLE in between
     */
    public record LoyaltyOutlook(
            int nobles, int before, int highest, int lowest, Conquest conquered) {}

    /** Whether the village falls. */
    public enum Conquest {
        NO,
        POSSIBLE,
        YES
    }
}
