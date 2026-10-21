package de.bannerfall.movement;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * POST /api/villages/{id}/commands and .../commands/preview: e.g. {@code {"kind": "ATTACK",
 * "target": {"x": 48, "y": 52}, "units": {"AXEMAN": 100, "CATAPULT": 10}, "catapultTarget":
 * "FARM"}}.
 *
 * @param units missing types send none
 * @param catapultTarget only used by an attack with catapults; null means HEADQUARTERS
 * @param notifyReturn an attack only: tell the owner when the units are home (1.6); null means
 *     false
 */
public record CommandRequest(
        @NotNull CommandKind kind,
        @NotNull Coordinates target,
        @NotNull Map<UnitType, @NotNull @Min(0) Integer> units,
        @Schema(nullable = true) BuildingType catapultTarget,
        @Schema(nullable = true) Boolean notifyReturn) {

    /** A command without the return push. */
    public CommandRequest(
            CommandKind kind,
            Coordinates target,
            Map<UnitType, Integer> units,
            BuildingType catapultTarget) {
        this(kind, target, units, catapultTarget, null);
    }

    /** Whether the owner wants to hear when the units are home. */
    public boolean wantsReturnPush() {
        return Boolean.TRUE.equals(notifyReturn);
    }
}
