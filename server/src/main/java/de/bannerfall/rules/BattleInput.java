package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.List;
import java.util.Map;

/**
 * Everything {@link Combat#fight} needs to know about one attack (02-game-rules §7–§9.2), taken
 * from the arriving movement and the target village after its catch-up to the arrival time. Unit
 * maps are copied: missing types count 0, zero counts are dropped, negative counts are rejected.
 *
 * @param attacker the arriving units, at least one
 * @param defenderStacks every stack standing in the target village – the owner's own units and each
 *     support – in any order; {@link BattleResult#defenderLosses()} keeps that order. Empty if
 *     nobody is there.
 * @param wallLevel the target's wall level before the battle
 * @param catapultTarget the building the catapults aim at; required if the attack has catapults,
 *     null if there is no target
 * @param catapultTargetLevel the level of that building before the battle: the wall level for
 *     {@code WALL}, 0 without a target
 * @param luck in [−1, 1], drawn by the caller with {@link Combat#drawLuck}
 * @param attackerLevels the research levels of the attacking units (M30); {@link UnitLevels#NONE}
 *     with unit levels off
 * @param defenderLevels the levels of each defending stack, one entry per entry of {@code
 *     defenderStacks}: a support stack fights with its owner's levels, not the village owner's
 * @param attackBonus the War banner of the attack's source village (02-game-rules §21.2), as a
 *     fraction; 0 without
 * @param defenceBonus the Bulwark banner of the target village at battle time, as a fraction; 0
 *     without
 */
public record BattleInput(
        Map<UnitType, Integer> attacker,
        List<Map<UnitType, Integer>> defenderStacks,
        int wallLevel,
        BuildingType catapultTarget,
        int catapultTargetLevel,
        double luck,
        UnitLevels attackerLevels,
        List<UnitLevels> defenderLevels,
        double attackBonus,
        double defenceBonus) {

    /** A battle without banners. */
    public BattleInput(
            Map<UnitType, Integer> attacker,
            List<Map<UnitType, Integer>> defenderStacks,
            int wallLevel,
            BuildingType catapultTarget,
            int catapultTargetLevel,
            double luck,
            UnitLevels attackerLevels,
            List<UnitLevels> defenderLevels) {
        this(
                attacker,
                defenderStacks,
                wallLevel,
                catapultTarget,
                catapultTargetLevel,
                luck,
                attackerLevels,
                defenderLevels,
                0,
                0);
    }

    /** A battle without unit levels (every unit at level 1) and without banners. */
    public BattleInput(
            Map<UnitType, Integer> attacker,
            List<Map<UnitType, Integer>> defenderStacks,
            int wallLevel,
            BuildingType catapultTarget,
            int catapultTargetLevel,
            double luck) {
        this(
                attacker,
                defenderStacks,
                wallLevel,
                catapultTarget,
                catapultTargetLevel,
                luck,
                UnitLevels.NONE,
                defenderStacks.stream().map(stack -> UnitLevels.NONE).toList());
    }

    public BattleInput {
        attacker = Combat.copyUnits(attacker);
        defenderStacks = defenderStacks.stream().map(Combat::copyUnits).toList();
        if (attacker.isEmpty()) {
            throw new IllegalArgumentException("an attack needs at least one unit");
        }
        if (wallLevel < 0 || catapultTargetLevel < 0) {
            throw new IllegalArgumentException(
                    "levels must not be negative: wall "
                            + wallLevel
                            + ", catapult target "
                            + catapultTargetLevel);
        }
        if (catapultTarget == null
                && (attacker.containsKey(UnitType.CATAPULT) || catapultTargetLevel != 0)) {
            throw new IllegalArgumentException(
                    "catapults need a target, and without one its level must be 0");
        }
        if (catapultTarget == BuildingType.WALL && catapultTargetLevel != wallLevel) {
            throw new IllegalArgumentException(
                    "catapults aimed at the wall: target level "
                            + catapultTargetLevel
                            + " must be the wall level "
                            + wallLevel);
        }
        if (!(luck >= -1 && luck <= 1)) {
            throw new IllegalArgumentException("luck must be in [-1, 1]: " + luck);
        }
        if (defenderLevels.size() != defenderStacks.size()) {
            throw new IllegalArgumentException(
                    "one set of levels per defending stack: "
                            + defenderLevels.size()
                            + " for "
                            + defenderStacks.size());
        }
        defenderLevels = List.copyOf(defenderLevels);
        if (!(attackBonus >= 0 && attackBonus < 1 && defenceBonus >= 0 && defenceBonus < 1)) {
            throw new IllegalArgumentException(
                    "banner bonuses must be in [0, 1): " + attackBonus + ", " + defenceBonus);
        }
    }
}
