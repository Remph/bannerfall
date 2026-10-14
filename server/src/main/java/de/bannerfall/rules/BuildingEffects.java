package de.bannerfall.rules;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.rules.BuildingEffect.Kind;
import de.bannerfall.world.WorldSettings;
import java.util.List;

/**
 * What a building does at a level, for the Town Hall preview (M24, 02-game-rules §4.4). The numbers
 * come from the same functions the game itself uses, so the preview can't drift from the rules. The
 * Harvest and Treasury banner of the village is in the numbers (02-game-rules §21.2), so the
 * preview shows what the village will really make. Pure functions.
 */
public final class BuildingEffects {

    private BuildingEffects() {}

    /**
     * The effects of {@code building} that change when it goes from {@code fromLevel} to {@code
     * fromLevel + 1}, in display order. Buildings that only unlock things (Smithy, Academy, Rally
     * Point) have none; the unlocks are listed separately.
     */
    public static List<BuildingEffect> upgrade(
            BuildingType building, int fromLevel, WorldSettings world) {
        return upgrade(building, fromLevel, world, BannerEffect.NONE);
    }

    /** {@link #upgrade(BuildingType, int, WorldSettings)} for a village that flies a banner. */
    public static List<BuildingEffect> upgrade(
            BuildingType building, int fromLevel, WorldSettings world, BannerEffect banner) {
        if (fromLevel < 0) {
            throw new IllegalArgumentException("level must not be negative: " + fromLevel);
        }
        int to = fromLevel + 1;
        return switch (building) {
            case HEADQUARTERS ->
                    List.of(
                            pair(
                                    Kind.BUILD_TIME_PERCENT,
                                    100 * Math.pow(BuildRules.HQ_FACTOR, fromLevel),
                                    100 * Math.pow(BuildRules.HQ_FACTOR, to)));
            case BARRACKS, STABLE, WORKSHOP ->
                    List.of(
                            new BuildingEffect(
                                    Kind.RECRUIT_TIME_PERCENT,
                                    fromLevel < 1 ? null : recruitPercent(fromLevel),
                                    recruitPercent(to)));
            case MARKET ->
                    List.of(pair(Kind.MERCHANTS, Merchants.total(fromLevel), Merchants.total(to)));
            case WOODCUTTER, CLAY_PIT, IRON_MINE ->
                    List.of(
                            pair(
                                    Kind.PRODUCTION_PER_HOUR,
                                    BannerRules.production(
                                            Production.perHour(fromLevel, world), banner.harvest()),
                                    BannerRules.production(
                                            Production.perHour(to, world), banner.harvest())));
            case FARM ->
                    List.of(
                            new BuildingEffect(
                                    Kind.FARM_CAPACITY,
                                    fromLevel < 1
                                            ? null
                                            : (double) Population.farmCapacity(fromLevel),
                                    Population.farmCapacity(to)));
            case WAREHOUSE ->
                    List.of(
                            new BuildingEffect(
                                    Kind.WAREHOUSE_CAPACITY,
                                    fromLevel < 1
                                            ? null
                                            : (double)
                                                    Storage.warehouseCapacity(
                                                            fromLevel, banner.treasury()),
                                    Storage.warehouseCapacity(to, banner.treasury())));
            case HIDING_PLACE ->
                    List.of(
                            pair(
                                    Kind.HIDING_CAPACITY,
                                    Storage.hidingCapacity(fromLevel, banner.treasury()),
                                    Storage.hidingCapacity(to, banner.treasury())));
            case WALL ->
                    List.of(
                            pair(Kind.WALL_DEFENSE_PERCENT, 5.0 * fromLevel, 5.0 * to),
                            pair(Kind.WALL_DEFENSE_FLAT, 50.0 * fromLevel, 50.0 * to));
            case ACADEMY, SMITHY, RALLY_POINT, BANNER_HALL -> List.of();
        };
    }

    private static double recruitPercent(int level) {
        return 100 * Math.pow(RecruitRules.LEVEL_FACTOR, level - 1);
    }

    private static BuildingEffect pair(Kind kind, double from, double to) {
        return new BuildingEffect(kind, from, to);
    }
}
