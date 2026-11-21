package de.bannerfall.diplomacy;

import de.bannerfall.npc.TrustReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the last changes of a lord's trust (2.1 S2b, 12-solo-and-allies §5), as the diplomacy page
 * lists it: "+10 support at Wulfsburg".
 *
 * @param delta what it moved
 * @param village where it happened; null if nowhere
 */
public record TrustChangeDto(
        TrustReason reason, int delta, @Schema(nullable = true) String village) {}
