package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/banners}: everything the Banner Hall page shows (02-game-rules §21, 04-api).
 *
 * @param enabled whether banners work in this world; off = no effects, no equipping
 * @param hallMaxLevel the highest level of the Banner Hall (the highest tier that can work)
 * @param effects per type the effect of tier I, II and III as a fraction (0.05 = 5 %), the paladin
 *     standards' too
 * @param banners all banners of the player, stash and equipped, oldest first; paladin standards
 *     among them (1.4)
 * @param villages the player's villages with their hall and slots
 * @param paladinsEnabled whether the world has paladins: only they carry standards (1.4,
 *     02-game-rules §21.18)
 * @param paladinLevel the player's paladin level (Hall training): a standard works at most at this
 *     tier
 * @param standardTheftEnabled the admin setting: the defenders' thieves can take the standard of an
 *     attack they beat
 */
public record BannersDto(
        boolean enabled,
        int hallMaxLevel,
        Map<BannerType, List<Double>> effects,
        List<BannerDto> banners,
        List<BannerVillageDto> villages,
        boolean paladinsEnabled,
        int paladinLevel,
        boolean standardTheftEnabled) {

    /**
     * A village of the player as the Banner Hall page needs it.
     *
     * @param hallLevel the village's Banner Hall; 0 = a banner can't be equipped
     * @param bannerId the banner flying here; null = the slot is empty
     * @param effectiveTier the tier that banner works at ({@code min(tier, hallLevel)}), 0 =
     *     dormant or no banner
     * @param locked an attack is on its way: equipping, swapping and unequipping are refused
     * @param standardId the paladin standard equipped here (1.4); null = the slot is empty
     * @param standardEffectiveTier the tier it works at ({@code min(tier, paladinLevel)}, 0 without
     *     a hall), 0 = asleep or no standard
     * @param standardLocked the standard slot is locked: an attack is on its way to the village, or
     *     a standard is equipped and an attack with a paladin from the village is under way (an
     *     empty slot can still be filled then)
     */
    public record BannerVillageDto(
            long id,
            String name,
            int x,
            int y,
            int hallLevel,
            @Schema(nullable = true) Long bannerId,
            int effectiveTier,
            boolean locked,
            @Schema(nullable = true) Long standardId,
            int standardEffectiveTier,
            boolean standardLocked) {}
}
