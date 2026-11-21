package de.bannerfall.diplomacy;

import de.bannerfall.npc.NpcOrderKind;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code POST /api/diplomacy/orders} (2.1 S3): e.g. {@code {"kind": "ATTACK", "villageId": 812,
 * "lordIds": [14, 22], "landTogether": true}}. Each lord's new order replaces its old one.
 *
 * @param landTogether only for an attack: the lords time their march to land within a minute after
 *     the human's own attack
 */
public record OrderRequest(
        @NotNull NpcOrderKind kind,
        long villageId,
        @NotEmpty @Size(max = 50) List<@NotNull Long> lordIds,
        boolean landTogether) {}
