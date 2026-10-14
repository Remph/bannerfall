package de.bannerfall.simulator;

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
 *     type means level 1. Ignored while the world has unit levels switched off
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
        @Schema(nullable = true) @Min(0) @Max(MAX_BANNER_TIER) Integer oathTier) {

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
