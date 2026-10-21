package de.bannerfall.npc;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * One NPC lord as the admin sees it (04-api *Admin*). The personality is only ever sent here, never
 * to players (02-game-rules §11b).
 *
 * @param lastThinkAt when its last round ran; null before the first
 * @param nextThinkAt when its next round is due; null for a lord that is out of the game
 * @param braked the rubber band keeps it from conquering (09 §8)
 * @param boosted the rubber band raises its production
 * @param guildId / guildName / guildTag its guild (09 §13), null for none
 * @param guildByAdmin the admin placed it (§13.7): the forming leaves it alone
 * @param paladinLevel its Hall-trained paladin level (lord banners, 09 §14.6); 1 untrained
 * @param bannerVillages its villages with a banner or standard equipped (09 §14.8), oldest first
 * @param stash its banners and standards in the stash, counted by type and tier
 * @param learning what it learned (09 §15.5); null while nothing is learned or learning is off
 */
public record NpcLordDto(
        long playerId,
        String name,
        NpcPersonality personality,
        String template,
        int points,
        int villageCount,
        @Schema(nullable = true) Instant lastThinkAt,
        @Schema(nullable = true) Instant nextThinkAt,
        boolean braked,
        boolean boosted,
        double productionFactor,
        @Schema(nullable = true) Long guildId,
        @Schema(nullable = true) String guildName,
        @Schema(nullable = true) String guildTag,
        boolean guildByAdmin,
        int paladinLevel,
        List<BannerVillage> bannerVillages,
        List<StashCount> stash,
        @Schema(nullable = true) Learning learning) {

    /**
     * A lord village with its banner and standard; each null if none is equipped.
     *
     * @param hall the village's Banner Hall level
     */
    public record BannerVillage(
            long villageId,
            String name,
            int hall,
            @Schema(nullable = true) Equipped banner,
            @Schema(nullable = true) Equipped standard) {}

    /**
     * An equipped banner or standard.
     *
     * @param effectiveTier the tier it works at now: 0 while it sleeps (no hall, or lord banners
     *     off)
     */
    public record Equipped(BannerType type, int tier, int effectiveTier) {}

    /** How many banners of one type and tier the stash holds. */
    public record StashCount(BannerType type, int tier, int count) {}

    /**
     * A learning lord's lessons (09 §15.5).
     *
     * @param defenceWeight the share of the mix the attacks on it moved (0–1)
     * @param attackWeight the share the human wins moved (0–1)
     * @param changes the largest changes against the template in its biggest village's stage, in
     *     percentage points of the army's population, largest first
     * @param walls its villages with a wall goal (siege attacks)
     */
    public record Learning(
            double defenceWeight,
            double attackWeight,
            List<MixChange> changes,
            List<WallGoal> walls) {}

    /** A unit's share in the learned mix minus its share in the template, in percentage points. */
    public record MixChange(UnitType unit, int points) {}

    /** A village's wall goal and until when it holds. */
    public record WallGoal(long villageId, String name, int level, Instant until) {}
}
