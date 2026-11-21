package de.bannerfall.victory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A side that is the court of a solo world (2.1 S2b, 12-solo-and-allies §6): the lord guild with
 * the most points. The client writes its name from {@code key} ("The Raven Court" / "Der Rabenhof",
 * plus the numeral), or "the court of" and the guild's name without one.
 *
 * @param key the court's key ({@code RAVEN}, {@code BEAR}, …); null for a guild named by the host
 * @param numeral "II", "III", … for a guild of a later round of names; null otherwise
 */
public record CourtRefDto(
        @Schema(nullable = true) String key, @Schema(nullable = true) String numeral) {}
