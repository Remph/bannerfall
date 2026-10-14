package de.bannerfall.npc;

import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Checks the NPC templates against the game data (09-npc-lords §9) and stops the server at startup
 * if something is wrong, like {@code GameData} does for the numbers. The static {@link #problems}
 * is what the tests call with broken templates.
 */
@Component
class NpcTemplateValidation {

    NpcTemplateValidation(NpcTemplates templates, GameData gameData) {
        List<String> problems = problems(templates, gameData);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid NPC templates:\n" + String.join("\n", problems));
        }
    }

    /**
     * All problems, one text each (empty = valid). Checked: a template for every personality; every
     * step non-empty with levels within 1..max; the requirements of every step's buildings met by
     * the steps before it (from the start levels); every building at its max level after the last
     * step; stages with ascending {@code below-points}, only the last without one, and shares in
     * 0..1; a mix for every stage with positive shares of units that can be recruited by the end of
     * the build list (and no nobles, they are never in the mix); no unit in a unit list twice.
     */
    static List<String> problems(NpcTemplates templates, GameData data) {
        List<String> problems = new ArrayList<>();
        for (NpcPersonality personality : NpcPersonality.values()) {
            NpcTemplates.Template template = templates.of(personality);
            if (template == null) {
                problems.add(personality + ": template missing");
                continue;
            }
            checkBuild(personality.name(), template, data, problems);
            checkStages(personality.name(), template, data, problems);
        }
        return problems;
    }

    private static void checkBuild(
            String name, NpcTemplates.Template template, GameData data, List<String> problems) {
        Map<BuildingType, Integer> levels = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            levels.put(type, data.building(type).startLevel());
        }
        if (template.build().isEmpty()) {
            problems.add(name + ".build: no steps");
        }
        int index = 0;
        for (Map<BuildingType, Integer> step : template.build()) {
            String where = name + ".build[" + index + "]";
            index++;
            if (step == null || step.isEmpty()) {
                problems.add(where + ": empty step");
                continue;
            }
            Map<BuildingType, Integer> before = new EnumMap<>(levels);
            for (var entry : step.entrySet()) {
                BuildingDef def = data.building(entry.getKey());
                int target = entry.getValue() == null ? 0 : entry.getValue();
                if (target < 1 || target > def.maxLevel()) {
                    problems.add(
                            where
                                    + "."
                                    + entry.getKey()
                                    + ": level must be in 1.."
                                    + def.maxLevel());
                    continue;
                }
                if (!BuildRules.missingRequirements(def.requires(), before).isEmpty()) {
                    problems.add(
                            where
                                    + "."
                                    + entry.getKey()
                                    + ": requirements "
                                    + def.requires()
                                    + " are not met by the steps before it");
                }
                levels.merge(entry.getKey(), target, Math::max);
            }
        }
        for (BuildingType type : BuildingType.values()) {
            // NPC lords have no banners (02-game-rules §21.1): they never build the banner hall
            boolean notForLords = type == BuildingType.BANNER_HALL;
            if (!notForLords && levels.get(type) != data.building(type).maxLevel()) {
                problems.add(
                        name
                                + ".build: "
                                + type
                                + " ends at "
                                + levels.get(type)
                                + ", not at its max level");
            }
        }
    }

    private static void checkStages(
            String name, NpcTemplates.Template template, GameData data, List<String> problems) {
        List<NpcTemplates.Stage> stages = template.stages();
        if (stages.isEmpty()) {
            problems.add(name + ".stages: none");
        }
        Integer previous = null;
        for (int i = 0; i < stages.size(); i++) {
            NpcTemplates.Stage stage = stages.get(i);
            String where = name + ".stages[" + i + "]";
            boolean last = i == stages.size() - 1;
            if (stage.belowPoints() == null != last) {
                problems.add(where + ": only the last stage has no below-points");
            } else if (stage.belowPoints() != null) {
                if (stage.belowPoints() <= 0
                        || (previous != null && stage.belowPoints() <= previous)) {
                    problems.add(where + ": below-points must ascend");
                }
                previous = stage.belowPoints();
            }
            if (!(stage.troopShare() > 0 && stage.troopShare() < 1)) {
                problems.add(where + ": troop-share must be in 0..1");
            }
            Map<UnitType, Integer> mix = template.mixOf(stage);
            if (mix == null || mix.isEmpty()) {
                problems.add(name + ".mix." + stage.name() + ": missing");
                continue;
            }
            for (var entry : mix.entrySet()) {
                String unit = name + ".mix." + stage.name() + "." + entry.getKey();
                if (entry.getKey() == UnitType.NOBLE) {
                    problems.add(unit + ": nobles are never in the mix");
                } else if (entry.getKey() == UnitType.PALADIN) {
                    problems.add(unit + ": paladins are never in the mix");
                } else if (entry.getKey() == UnitType.BANNER_THIEF) {
                    // lords don't use banners (02-game-rules §21.11)
                    problems.add(unit + ": banner thieves are never in the mix");
                } else if (entry.getValue() == null || entry.getValue() <= 0) {
                    problems.add(unit + ": share must be > 0");
                } else {
                    // At the end of the build list every building is at its max level.
                    Map<BuildingType, Integer> maxed = new EnumMap<>(BuildingType.class);
                    for (BuildingType type : BuildingType.values()) {
                        maxed.put(type, data.building(type).maxLevel());
                    }
                    if (!BuildRules.missingRequirements(data.unit(entry.getKey()).requires(), maxed)
                            .isEmpty()) {
                        problems.add(unit + ": can't be recruited");
                    }
                }
            }
        }
        for (String key : template.mix().keySet()) {
            if (stages.stream().noneMatch(s -> s.name().equals(key))) {
                problems.add(name + ".mix." + key + ": no stage of that name");
            }
        }
        checkRatio(name + ".home-guard", template.homeGuard(), problems);
        checkRatio(name + ".support-share", template.supportShare(), problems);
        checkRatio(name + ".grudge-troop-bonus", template.grudgeTroopBonus(), problems);
        if (!(template.margin() >= 1)) {
            problems.add(name + ".margin must be >= 1");
        }
        if (template.farmRadius() <= 0 || template.conquestRadius() <= 0 || template.nobles() < 0) {
            problems.add(name + ": radii must be > 0 and nobles >= 0");
        }
        for (var list :
                List.of(template.farmUnits(), template.attackUnits(), template.supportUnits())) {
            if (list.stream().distinct().count() != list.size()) {
                problems.add(name + ": a unit appears twice in a unit list");
            }
            if (list.contains(UnitType.BANNER_THIEF)) {
                problems.add(name + ": banner thieves are never in a unit list");
            }
        }
    }

    private static void checkRatio(String name, double value, List<String> problems) {
        if (!(value >= 0 && value <= 1)) {
            problems.add(name + " must be in 0..1");
        }
    }
}
