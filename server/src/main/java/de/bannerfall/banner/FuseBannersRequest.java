package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/banners/fuse (M32c, 02-game-rules §21.4.2), e.g. {@code {"bannerIds": [4, 7, 9],
 * "type": "WAR"}}.
 *
 * @param bannerIds three different banners of the player's stash with the same tier
 * @param type the type the new banner takes: one of the three's
 */
public record FuseBannersRequest(
        @NotNull @Size(min = 1, max = 10) List<@NotNull Long> bannerIds,
        @NotNull BannerType type) {}
