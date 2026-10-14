package de.bannerfall.movement;

import de.bannerfall.common.Coordinates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/villages/{id}/commands/train and .../train/preview (M37, 02-game-rules §6.1): several
 * attacks from one village at one target, sent together, e.g. {@code {"target": {"x": 48, "y": 52},
 * "attacks": [{"units": {"AXEMAN": 300, "RAM": 20}}, {"units": {"AXEMAN": 50, "NOBLE": 1}}]}}. They
 * arrive in this order.
 */
public record TrainRequest(
        @NotNull Coordinates target,
        @NotNull @Size(min = 1, max = MAX_ATTACKS) List<@NotNull @Valid TrainAttack> attacks) {

    /** The most attacks a train holds (02-game-rules §6.1). */
    public static final int MAX_ATTACKS = 5;
}
