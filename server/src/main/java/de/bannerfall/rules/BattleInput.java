package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.List;
import java.util.Map;

/**
 * Everything {@link Combat#fight} needs to know about one attack (02-game-rules §7–§9.2), taken
 * from the arriving movement and the target village after its catch-up to the arrival time. Unit
 * maps are copied: missing types count 0, zero counts are dropped, negative counts are rejected.
 * Built with {@link #attack}, so a new modifier is one more builder method with a neutral default
 * instead of one more constructor (the battle, the simulator and the NPC estimate all build it).
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
 * @param attackerStandard the paladin standard the attack took along (1.4, 02-game-rules §21.18);
 *     {@link StandardEffect#NONE} without
 * @param defenderStandards the standard each defending stack carries at battle time, one entry per
 *     entry of {@code defenderStacks}
 * @param nightFactor the night bonus (1.4, 02-game-rules §8.1): multiplies the defence, 1 by day,
 *     in a barbarian village and in a world without it
 * @param morale the attacker's morale (1.6, 02-game-rules §8.2, {@link Morale}): multiplies the
 *     attack, in (0, 1]; 1 against a bigger player, in a barbarian village and in a world without
 *     it
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
        double defenceBonus,
        StandardEffect attackerStandard,
        List<StandardEffect> defenderStandards,
        double nightFactor,
        double morale) {

    /**
     * A builder for the battle of {@code attacker}: everything left out is the plain battle (nobody
     * defending, wall 0, no catapult target, luck 0, no unit levels, banners, standards, night
     * bonus or morale), so a caller names only what its battle has.
     */
    public static Builder attack(Map<UnitType, Integer> attacker) {
        return new Builder(attacker);
    }

    /**
     * Collects the parts of a {@link BattleInput}; {@link #build} checks them like the constructor.
     * The levels and standards of the defending stacks default to none for each stack.
     */
    public static final class Builder {
        private final Map<UnitType, Integer> attacker;
        private List<Map<UnitType, Integer>> defenderStacks = List.of();
        private int wallLevel;
        private BuildingType catapultTarget;
        private int catapultTargetLevel;
        private double luck;
        private UnitLevels attackerLevels = UnitLevels.NONE;
        private List<UnitLevels> defenderLevels;
        private double attackBonus;
        private double defenceBonus;
        private StandardEffect attackerStandard = StandardEffect.NONE;
        private List<StandardEffect> defenderStandards;
        private double nightFactor = 1;
        private double morale = 1;

        private Builder(Map<UnitType, Integer> attacker) {
            this.attacker = attacker;
        }

        /** Every stack standing in the target village, see {@link BattleInput#defenderStacks}. */
        public Builder defenders(List<Map<UnitType, Integer>> stacks) {
            this.defenderStacks = stacks;
            return this;
        }

        /** One stack standing in the target village (the simulator's and the NPC estimate's). */
        public Builder defender(Map<UnitType, Integer> stack) {
            return defenders(List.of(stack));
        }

        public Builder wall(int level) {
            this.wallLevel = level;
            return this;
        }

        /** The catapults' target and its level before the battle (the wall level for a wall). */
        public Builder catapults(BuildingType target, int level) {
            this.catapultTarget = target;
            this.catapultTargetLevel = level;
            return this;
        }

        public Builder luck(double luck) {
            this.luck = luck;
            return this;
        }

        public Builder attackerLevels(UnitLevels levels) {
            this.attackerLevels = levels;
            return this;
        }

        /** One entry per defending stack. */
        public Builder defenderLevels(List<UnitLevels> levels) {
            this.defenderLevels = levels;
            return this;
        }

        /** The War banner of the attack, as a fraction. */
        public Builder attackBonus(double bonus) {
            this.attackBonus = bonus;
            return this;
        }

        /** The Bulwark banner of the target, as a fraction. */
        public Builder defenceBonus(double bonus) {
            this.defenceBonus = bonus;
            return this;
        }

        public Builder attackerStandard(StandardEffect standard) {
            this.attackerStandard = standard;
            return this;
        }

        /** One entry per defending stack. */
        public Builder defenderStandards(List<StandardEffect> standards) {
            this.defenderStandards = standards;
            return this;
        }

        public Builder nightFactor(double factor) {
            this.nightFactor = factor;
            return this;
        }

        /** The attacker's morale, see {@link BattleInput#morale}. */
        public Builder morale(double factor) {
            this.morale = factor;
            return this;
        }

        /**
         * @throws IllegalArgumentException like the constructor
         */
        public BattleInput build() {
            return new BattleInput(
                    attacker,
                    defenderStacks,
                    wallLevel,
                    catapultTarget,
                    catapultTargetLevel,
                    luck,
                    attackerLevels,
                    defenderLevels != null
                            ? defenderLevels
                            : defenderStacks.stream().map(stack -> UnitLevels.NONE).toList(),
                    attackBonus,
                    defenceBonus,
                    attackerStandard,
                    defenderStandards != null
                            ? defenderStandards
                            : defenderStacks.stream().map(stack -> StandardEffect.NONE).toList(),
                    nightFactor,
                    morale);
        }
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
        if (attackerStandard == null) {
            throw new IllegalArgumentException("attackerStandard: NONE, not null");
        }
        if (defenderStandards.size() != defenderStacks.size()) {
            throw new IllegalArgumentException(
                    "one standard per defending stack: "
                            + defenderStandards.size()
                            + " for "
                            + defenderStacks.size());
        }
        defenderStandards = List.copyOf(defenderStandards);
        if (!(nightFactor >= 1)) {
            throw new IllegalArgumentException("nightFactor must be at least 1: " + nightFactor);
        }
        if (!(morale > 0 && morale <= 1)) {
            throw new IllegalArgumentException("morale must be in (0, 1]: " + morale);
        }
    }
}
