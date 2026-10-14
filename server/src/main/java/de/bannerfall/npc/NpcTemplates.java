package de.bannerfall.npc;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
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
            List<Map<BuildingType, Integer>> build) {

        public Template {
            stages = stages == null ? List.of() : List.copyOf(stages);
            mix = mix == null ? Map.of() : Map.copyOf(mix);
            farmUnits = farmUnits == null ? List.of() : List.copyOf(farmUnits);
            attackUnits = attackUnits == null ? List.of() : List.copyOf(attackUnits);
            supportUnits = supportUnits == null ? List.of() : List.copyOf(supportUnits);
            build = build == null ? List.of() : List.copyOf(build);
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

        /** The unit mix (share of the army's population) of a stage. */
        public Map<UnitType, Integer> mixOf(Stage stage) {
            return mix.get(stage.name());
        }
    }

    /** A stage of a template: {@code belowPoints} is null for the last one. */
    public record Stage(String name, Integer belowPoints, double troopShare) {}
}
