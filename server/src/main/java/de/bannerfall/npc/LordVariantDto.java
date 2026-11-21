package de.bannerfall.npc;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A lord's variant as a player who has met it sees it on its player page (09-npc-lords §16.7). The
 * title and the line come from the web's i18n.
 *
 * @param key the variant, e.g. {@code ROBBER_KNIGHT}; null for a lord without one (spawned before
 *     2.1 or as PLAIN): the viewer has met it, but there is no title to show
 * @param since when the viewer first met the lord
 */
public record LordVariantDto(@Schema(nullable = true) String key, Instant since) {}
