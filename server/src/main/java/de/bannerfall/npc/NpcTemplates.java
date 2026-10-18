package de.bannerfall.npc;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The personality templates, bound from {@code gamedata/npc/offensive.yml}, {@code balanced.yml}
 * and {@code defensive.yml} (09-npc-lords §9). The checks that need the game data are made by
 * {@link NpcTemplateValidation}.
 */
@ConfigurationProperties("npc")
public record NpcTemplates(Map<NpcPersonality, Template> templates) {

    public NpcTemplates {
        var copy = new EnumMap<NpcPersonality, Template>(NpcPersonality.class);
        if (templates != null) {
            copy.putAll(templates);
        }
        templates = Collections.unmodifiableMap(copy);
    }

    public Template of(NpcPersonality personality) {
        return templates.get(personality);
    }

    /**
     * One personality. The warfare fields (everything after {@code mix}) are used from M12c; they
     * are bound and validated now so that the files are complete.
     *
     * @param build the ordered build steps, each {@code building → target level}
     * @param villageRatio / humanPlayerRatio null = no limit (09 §6.4)
     * @param bannerFavourites all nine village banners, the lord's favourite first (lord banners,
     *     1.4, 09 §14.3)
     * @param standardFavourites all seven paladin standards, the favourite first (09 §14.3)
     * @param thieves the banner thieves each village with a Banner Hall keeps for retaliations (09
     *     §14.4); 0 = none
     * @param paladinRetaliates the village's paladin joins its retaliations (09 §14.5)
     */
    public record Template(
            List<Stage> stages,
            Map<String, Map<UnitType, Integer>> mix,
            List<UnitType> farmUnits,
            List<UnitType> attackUnits,
            List<UnitType> supportUnits,
            double homeGuard,
            double supportShare,
            double margin,
            Double villageRatio,
            Double humanPlayerRatio,
            boolean grudgeOpens,
            double grudgeTroopBonus,
            boolean conquersPlayers,
            int farmRadius,
            int conquestRadius,
            int nobles,
            List<Map<BuildingType, Integer>> build,
            List<BannerType> bannerFavourites,
            List<BannerType> standardFavourites,
            int thieves,
            boolean paladinRetaliates) {

        public Template {
            stages = stages == null ? List.of() : List.copyOf(stages);
            mix = mix == null ? Map.of() : Map.copyOf(mix);
            farmUnits = farmUnits == null ? List.of() : List.copyOf(farmUnits);
            attackUnits = attackUnits == null ? List.of() : List.copyOf(attackUnits);
            supportUnits = supportUnits == null ? List.of() : List.copyOf(supportUnits);
            build = build == null ? List.of() : List.copyOf(build);
            bannerFavourites = bannerFavourites == null ? List.of() : List.copyOf(bannerFavourites);
            standardFavourites =
                    standardFavourites == null ? List.of() : List.copyOf(standardFavourites);
        }

        /**
         * Where {@code type} stands in the lord's favourites (09 §14.3): 0 is the favourite; a type
         * the lists don't name comes last.
         */
        public int rank(BannerType type) {
            List<BannerType> list = type.isStandard() ? standardFavourites : bannerFavourites;
            int index = list.indexOf(type);
            return index < 0 ? list.size() : index;
        }

        /** The stage of a village with these points: the first whose limit is above them. */
        public Stage stageFor(int points) {
            for (Stage stage : stages) {
                if (stage.belowPoints() == null || points < stage.belowPoints()) {
                    return stage;
                }
            }
            return stages.getLast();
        }

        /**
         * The build steps for a world where the Banner Hall can be built or not (1.4, 02-game-rules
         * §21.3): without it the Banner Hall entries are left out, so no step waits on a building
         * the lord can't queue.
         */
        public List<Map<BuildingType, Integer>> buildSteps(boolean bannerHall) {
            if (bannerHall) {
                return build;
            }
            List<Map<BuildingType, Integer>> steps = new ArrayList<>();
            for (Map<BuildingType, Integer> step : build) {
                Map<BuildingType, Integer> without = new EnumMap<>(step);
                without.remove(BuildingType.BANNER_HALL);
                if (!without.isEmpty()) {
                    steps.add(without);
                }
            }
            return steps;
        }

        /** The unit mix (share of the army's population) of a stage. */
        public Map<UnitType, Integer> mixOf(Stage stage) {
            return mix.get(stage.name());
        }
    }

    /** A stage of a template: {@code belowPoints} is null for the last one. */
    public record Stage(String name, Integer belowPoints, double troopShare) {}
}
