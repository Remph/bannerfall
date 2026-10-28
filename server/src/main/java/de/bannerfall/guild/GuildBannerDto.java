package de.bannerfall.guild;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The guild banner of the player's guild ({@code GET /api/guilds/mine/banner}) and the answer of
 * every request that changes it (02-game-rules §16.4).
 *
 * @param enabled whether the world has guild banners on; off: the effects don't work and nothing
 *     can be donated, the level and the fund stay
 * @param level 0–{@code maxLevel}
 * @param price what the next level costs now (per-member price × the members now); 0 at the top
 * @param missing what the fund still lacks for it, 0 when it is full or at the top
 * @param members the guild's members now, the factor of the price
 * @param memberPrices the price per member of level 1, 2, …
 * @param lockedUntil no level can be raised before this time (a member left or was removed); null
 *     when nothing blocks
 * @param effects the effect of each level, whether it is reached or not
 * @param donors the members who have donated, the biggest first
 */
public record GuildBannerDto(
        boolean enabled,
        int level,
        int maxLevel,
        long fund,
        long price,
        long missing,
        int members,
        List<Long> memberPrices,
        @Schema(nullable = true) Instant lockedUntil,
        List<EffectDto> effects,
        List<DonorDto> donors) {

    /** What reaching {@code level} switches on, as a fraction. */
    public record EffectDto(int level, Kind kind, double bonus) {}

    /** The three effects, in the order of the levels. */
    public enum Kind {
        PRODUCTION,
        SUPPORT,
        DEFENCE
    }

    /** A member and what he has donated in all. */
    public record DonorDto(long playerId, String name, long amount) {}
}
