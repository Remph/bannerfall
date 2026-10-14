package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One banner of the player (02-game-rules §21.1).
 *
 * @param villageId the village it flies in; null = in the stash
 * @param equippedAt since when it flies there; null in the stash
 */
public record BannerDto(
        long id,
        BannerType type,
        int tier,
        @Schema(nullable = true) Long villageId,
        @Schema(nullable = true) Instant equippedAt) {

    static BannerDto of(Banner banner) {
        return new BannerDto(
                banner.getId(),
                banner.getType(),
                banner.getTier(),
                banner.getEquippedVillageId(),
                banner.getEquippedAt());
    }
}
