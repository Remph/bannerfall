package de.bannerfall.simulator;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/simulator (M23, 02-game-rules §20): one attack against one village, e.g. {@code
 * {"attacker": {"AXEMAN": 100, "RAM": 5}, "defender": {"SPEARMAN": 80}, "wall": 3}}.
 *
 * @param attacker the attacking units, at least one in total (missing types count 0)
 * @param defender everything that defends the village, its own units and the supports together
 *     (missing types count 0; empty is fine)
 * @param wall the wall level before the battle
 * @param catapultTarget the building the catapults aim at; only used with catapults, null means
 *     HEADQUARTERS (like a real attack)
 * @param catapultTargetLevel that building's level before the battle; null means 0. Ignored for the
 *     wall (its level is {@code wall}) and without catapults
 * @param loyalty the village's loyalty before the battle, 0 to 100; null means 100. Only used to
 *     tell what the surviving nobles would do
 * @param attackerLevels the research level of each attacking unit type (M30); null or a missing
 *     type means level 1. While the world has unit levels switched off only the paladin's and the
 *     banner thief's count (Hall training, 1.4); the thief level sets the theft chance per thief
 * @param defenderLevels the same for the defending units
 * @param warTier the attacker's War banner (M32c, 02-game-rules §21.2) at the tier it works at in
 *     his village (the hall there caps it); null or 0 means none
 * @param bulwarkTier the tier of the Bulwark banner flying in the target village; null or 0 means
 *     none. It works at {@code min(bulwarkTier, bannerHall)}, like in a real battle
 * @param bannerHall the target's Banner Hall level before the battle; null means 0. Caps the
 *     Bulwark and the Oath and makes the theft harder (§21.6.2); with catapults aimed at the hall
 *     it is also the target's level. Banner inputs are ignored while the world has banners switched
 *     off
 * @param plunderTier the attacker's Plunder banner at the tier it works at in his village, like
 *     {@code warTier} (code review 1.2); null or 0 means none. A village flies one banner, so not
 *     together with {@code warTier}
 * @param oathTier the tier of the Oath banner flying in the target village, capped by the hall like
 *     the Bulwark; null or 0 means none. Not together with {@code bulwarkTier}
 * @param attackerStandard the paladin standard of the attacker's village (1.4, 02-game-rules
 *     §21.18); null means none. It only works with a paladin in the attack, at most at the
 *     attacker's paladin level ({@code attackerLevels})
 * @param attackerStandardTier its tier; null or 0 means none
 * @param defenderStandard the standard of the defending village, for all defenders (the simulator
 *     has one stack); works only with a paladin among the defenders, at most at the defender's
 *     paladin level and only with a Banner Hall ({@code bannerHall})
 * @param defenderStandardTier its tier; null or 0 means none
 * @param night the attack arrives in the night bonus (1.4, 02-game-rules §8.1): the defence counts
 *     with the world's factor; null means by day. Ignored while the world has no night bonus. The
 *     simulator doesn't know whether the target is a barbarian village (which never gets it)
 * @param morale the attacker's morale in percent (1.6, 02-game-rules §8.2), 30 to 100; null means
 *     100. Below the world's floor it counts as the floor; ignored while the world has morale off
 */
public record SimulatorRequest(
        @NotNull Map<UnitType, @NotNull @Min(0) @Max(MAX_UNITS) Integer> attacker,
        @NotNull Map<UnitType, @NotNull @Min(0) @Max(MAX_UNITS) Integer> defender,
        @NotNull @Min(0) @Max(MAX_LEVEL) Integer wall,
        @Schema(nullable = true) BuildingType catapultTarget,
        @Schema(nullable = true) @Min(0) @Max(MAX_LEVEL) Integer catapultTargetLevel,
        @Schema(nullable = true) @Min(0) @Max(100) Integer loyalty,
        @Schema(nullable = true)
                Map<UnitType, @NotNull @Min(1) @Max(MAX_UNIT_LEVEL) Integer> attackerLevels,
        @Schema(nullable = true)
                Map<UnitType, @NotNull @Min(1) @Max(MAX_UNIT_LEVEL) Integer> defenderLevels,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer warTier,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer bulwarkTier,
        @Schema(nullable = true) @Min(0) @Max(MAX_LEVEL) Integer bannerHall,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer plunderTier,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer oathTier,
        @Schema(nullable = true) BannerType attackerStandard,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer attackerStandardTier,
        @Schema(nullable = true) BannerType defenderStandard,
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer defenderStandardTier,
        @Schema(nullable = true) Boolean night,
        @Schema(nullable = true) @Min(30) @Max(100) Integer morale) {

    /** A simulation by day. */
    public SimulatorRequest(
            Map<UnitType, Integer> attacker,
            Map<UnitType, Integer> defender,
            Integer wall,
            BuildingType catapultTarget,
            Integer catapultTargetLevel,
            Integer loyalty,
            Map<UnitType, Integer> attackerLevels,
            Map<UnitType, Integer> defenderLevels,
            Integer warTier,
            Integer bulwarkTier,
            Integer bannerHall,
            Integer plunderTier,
            Integer oathTier,
            BannerType attackerStandard,
            Integer attackerStandardTier,
            BannerType defenderStandard,
            Integer defenderStandardTier) {
        this(
                attacker,
                defender,
                wall,
                catapultTarget,
                catapultTargetLevel,
                loyalty,
                attackerLevels,
                defenderLevels,
                warTier,
                bulwarkTier,
                bannerHall,
                plunderTier,
                oathTier,
                attackerStandard,
                attackerStandardTier,
                defenderStandard,
                defenderStandardTier,
                null,
                null);
    }

    /** A simulation without paladin standards. */
    public SimulatorRequest(
            Map<UnitType, Integer> attacker,
            Map<UnitType, Integer> defender,
            Integer wall,
            BuildingType catapultTarget,
            Integer catapultTargetLevel,
            Integer loyalty,
            Map<UnitType, Integer> attackerLevels,
            Map<UnitType, Integer> defenderLevels,
            Integer warTier,
            Integer bulwarkTier,
            Integer bannerHall,
            Integer plunderTier,
            Integer oathTier) {
        this(
                attacker,
                defender,
                wall,
                catapultTarget,
                catapultTargetLevel,
                loyalty,
                attackerLevels,
                defenderLevels,
                warTier,
                bulwarkTier,
                bannerHall,
                plunderTier,
                oathTier,
                null,
                null,
                null,
                null);
    }

    /** Per unit type: far above any real army, far below an overflow of the sums. */
    public static final int MAX_UNITS = 1_000_000;

    /**
     * Above every building's maximum level; the service checks the exact maximum of the building.
     */
    public static final int MAX_LEVEL = 30;

    /** Above every unit's top level; a level above the unit's maximum counts as the maximum. */
    public static final int MAX_UNIT_LEVEL = 10;

    /** Banners have tiers I to III. */
    public static final int MAX_BANNER_TIER = 3;
}
