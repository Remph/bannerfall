package de.bannerfall.report;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * A battle (02-game-rules §7–§9) as one recipient sees it (kinds ATTACK, DEFENSE, SCOUT). What the
 * attacker may not see is null in his copy (02-game-rules §14): {@link #defenderSide} and the
 * scouted {@link #scouting} data need at least one attacking unit that survived, and his {@link
 * DefenderSide#stacks} are empty.
 *
 * @param attacker the attacking village (snapshot)
 * @param defender the attacked village (snapshot)
 * @param luck the luck of the main battle, e.g. {@code -0.12}; null without one (scouts only)
 * @param attackerUnits the units that attacked
 * @param attackerLosses the attacking units that died
 * @param attackerLevels the research levels (above 1) of the attacking unit types (M30); empty with
 *     unit levels off, without research, and in reports from before M30
 * @param catapultTarget the building the catapults aimed at; null without catapults
 * @param defenderSide the defending units, the wall and the catapult damage; null if the attacker
 *     may not see it
 * @param scouting null if the attack had no scouts (or the attacker may not see anything)
 * @param loot what the attackers took home (all 0 unless they won)
 * @param loyalty what surviving nobles did to the village's loyalty; null if none survived (and in
 *     reports from before M11)
 * @param banners what banners did in and after the battle (M32b, 02-game-rules §21.9); null if
 *     nothing (and in reports from before M32b)
 */
public record BattleReport(
        VillageRefDto attacker,
        VillageRefDto defender,
        BattleOutcome outcome,
        @Schema(nullable = true) Double luck,
        Map<UnitType, Integer> attackerUnits,
        Map<UnitType, Integer> attackerLosses,
        Map<UnitType, Integer> attackerLevels,
        @Schema(nullable = true) BuildingType catapultTarget,
        @Schema(nullable = true) DefenderSide defenderSide,
        @Schema(nullable = true) ScoutingReport scouting,
        ResourceAmounts loot,
        @Schema(nullable = true) LoyaltyReport loyalty,
        @Schema(nullable = true) BannerReport banners)
        implements ReportPayload {

    /** A battle report without banners. */
    public BattleReport(
            VillageRefDto attacker,
            VillageRefDto defender,
            BattleOutcome outcome,
            Double luck,
            Map<UnitType, Integer> attackerUnits,
            Map<UnitType, Integer> attackerLosses,
            Map<UnitType, Integer> attackerLevels,
            BuildingType catapultTarget,
            DefenderSide defenderSide,
            ScoutingReport scouting,
            ResourceAmounts loot,
            LoyaltyReport loyalty) {
        this(
                attacker,
                defender,
                outcome,
                luck,
                attackerUnits,
                attackerLosses,
                attackerLevels,
                catapultTarget,
                defenderSide,
                scouting,
                loot,
                loyalty,
                null);
    }

    /** A battle report without unit levels (every unit at level 1) and without banners. */
    public BattleReport(
            VillageRefDto attacker,
            VillageRefDto defender,
            BattleOutcome outcome,
            Double luck,
            Map<UnitType, Integer> attackerUnits,
            Map<UnitType, Integer> attackerLosses,
            BuildingType catapultTarget,
            DefenderSide defenderSide,
            ScoutingReport scouting,
            ResourceAmounts loot,
            LoyaltyReport loyalty) {
        this(
                attacker,
                defender,
                outcome,
                luck,
                attackerUnits,
                attackerLosses,
                Map.of(),
                catapultTarget,
                defenderSide,
                scouting,
                loot,
                loyalty);
    }

    public BattleReport {
        attackerUnits = ReportMaps.units(attackerUnits);
        attackerLosses = ReportMaps.units(attackerLosses);
        attackerLevels = ReportMaps.units(attackerLevels);
        loot = loot == null ? ResourceAmounts.ZERO : loot;
    }

    @Override
    public ReportSummary summary() {
        return new ReportSummary(attacker, defender, outcome);
    }

    /**
     * The defending side.
     *
     * @param units all defending units in the village before the battle (every stack)
     * @param losses all defending units that died
     * @param stacks the same per stack: the village's own units first, then each support; empty in
     *     the attacker's copy (he sees the totals, not who supports the village)
     * @param wallBefore the wall level before the battle
     * @param wallAfter the wall level after it (rams and catapults)
     * @param catapultLevelBefore the catapult target's level before the battle; null without
     *     catapults
     * @param catapultLevelAfter its level after the battle; null without catapults
     * @param levels the highest research level above 1 per unit type among the defenders, only in
     *     the attacker's copy and only when his scouts were upgraded to level 2 or more and
     *     scouting succeeded (M31 follow-up, 02-game-rules §5.4); empty otherwise. The defenders'
     *     copy has the levels per stack instead
     */
    public record DefenderSide(
            Map<UnitType, Integer> units,
            Map<UnitType, Integer> losses,
            List<DefenderStack> stacks,
            int wallBefore,
            int wallAfter,
            @Schema(nullable = true) Integer catapultLevelBefore,
            @Schema(nullable = true) Integer catapultLevelAfter,
            Map<UnitType, Integer> levels) {

        public DefenderSide {
            units = ReportMaps.units(units);
            losses = ReportMaps.units(losses);
            stacks = stacks == null ? List.of() : List.copyOf(stacks);
            levels = ReportMaps.units(levels);
        }

        /** Without the scouted levels. */
        public DefenderSide(
                Map<UnitType, Integer> units,
                Map<UnitType, Integer> losses,
                List<DefenderStack> stacks,
                int wallBefore,
                int wallAfter,
                Integer catapultLevelBefore,
                Integer catapultLevelAfter) {
            this(
                    units,
                    losses,
                    stacks,
                    wallBefore,
                    wallAfter,
                    catapultLevelBefore,
                    catapultLevelAfter,
                    Map.of());
        }
    }

    /**
     * The units of one home village that stood in the attacked village.
     *
     * @param home the village they belong to (snapshot)
     * @param levels the research levels (above 1) of these unit types, those of their owner (M30);
     *     empty with unit levels off and in reports from before M30
     */
    public record DefenderStack(
            VillageRefDto home,
            Map<UnitType, Integer> units,
            Map<UnitType, Integer> losses,
            Map<UnitType, Integer> levels) {

        public DefenderStack(
                VillageRefDto home, Map<UnitType, Integer> units, Map<UnitType, Integer> losses) {
            this(home, units, losses, Map.of());
        }

        public DefenderStack {
            units = ReportMaps.units(units);
            losses = ReportMaps.units(losses);
            levels = ReportMaps.units(levels);
        }
    }

    /**
     * The loyalty before and after the surviving nobles hit it (02-game-rules §10).
     *
     * @param before rounded down
     * @param after rounded down, never below 0; 0 when the village was conquered (it then starts at
     *     25 for its new owner)
     * @param conquered the village changed owner
     */
    public record LoyaltyReport(int before, int after, boolean conquered) {}

    /**
     * The scouting phase (02-game-rules §7.1).
     *
     * @param succeeded the attacking scouts outnumbered the defending ones
     * @param resources the stock before the loot (rounded down); only in the attacker's copy, and
     *     only if scouting succeeded
     * @param buildings every building's level before the battle; like {@code resources}
     * @param banner the banner flying in the village (M32, 02-game-rules §21.7: besides its owner
     *     only a successful scout sees it); like {@code resources}, and null if none flies there
     */
    public record ScoutingReport(
            boolean succeeded,
            @Schema(nullable = true) ResourceAmounts resources,
            @Schema(nullable = true) Map<BuildingType, Integer> buildings,
            @Schema(nullable = true) ScoutedBanner banner) {

        public ScoutingReport {
            buildings = buildings == null ? null : ReportMaps.buildings(buildings);
        }
    }

    /** A banner as a scout saw it: its type and tier (the village's hall level is in the list). */
    public record ScoutedBanner(BannerType type, int tier) {}

    /**
     * The banner block (M32b, 02-game-rules §21.9). Each part is null when it says nothing.
     *
     * @param attackBonus the War banner the attack took along, as a fraction (both copies: the
     *     defenders see everything about the attackers)
     * @param defenceBonus the Bulwark banner of the village, as a fraction (the defenders' copy;
     *     the attacker's copy only when his scouting succeeded and someone came back)
     * @param found the banner the recipient's side found: the attacker's in his copy, the village
     *     owner's in the defenders' copy (02-game-rules §21.7)
     * @param theft what the attacker's banner thieves did (his copy only)
     * @param stolen the village's banner that thieves took (the defenders' copy only)
     */
    public record BannerReport(
            @Schema(nullable = true) Double attackBonus,
            @Schema(nullable = true) Double defenceBonus,
            @Schema(nullable = true) ScoutedBanner found,
            @Schema(nullable = true) TheftReport theft,
            @Schema(nullable = true) ScoutedBanner stolen) {

        /** Null instead of a block that says nothing. */
        public static BannerReport orNull(
                Double attackBonus,
                Double defenceBonus,
                ScoutedBanner found,
                TheftReport theft,
                ScoutedBanner stolen) {
            if (attackBonus == null
                    && defenceBonus == null
                    && found == null
                    && theft == null
                    && stolen == null) {
                return null;
            }
            return new BannerReport(attackBonus, defenceBonus, found, theft, stolen);
        }
    }

    /**
     * The attack's banner thieves (02-game-rules §21.6.2). The chance is shown so the rule can be
     * learned; the roll itself is not.
     *
     * @param sent thieves in the attack
     * @param survived thieves that survived the battle
     * @param chance the chance of the roll, null without one
     * @param result what happened
     * @param banner the village's banner, if the thieves got to see it (STOLEN, FAILED)
     */
    public record TheftReport(
            int sent,
            int survived,
            @Schema(nullable = true) Double chance,
            TheftResult result,
            @Schema(nullable = true) ScoutedBanner banner) {}
}
