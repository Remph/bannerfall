package de.bannerfall.movement;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * One attack of a {@link TrainRequest}.
 *
 * @param units missing types send none
 * @param catapultTarget only used by an attack with catapults; null means HEADQUARTERS
 */
public record TrainAttack(
        @NotNull Map<UnitType, @NotNull @Min(0) Integer> units,
        @Schema(nullable = true) BuildingType catapultTarget) {}
