package de.bannerfall.gamedata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All building and unit numbers, bound from {@code gamedata/buildings.yml} and {@code
 * gamedata/units.yml} (imported in application.yml). The constructor validates the data, so an
 * invalid file stops the application at startup. Tests load the same files without Spring through
 * {@code TestGameData.load()}.
 */
@ConfigurationProperties("gamedata")
public record GameData(Map<BuildingType, BuildingDef> buildings, Map<UnitType, UnitDef> units) {

    /**
     * The buildings that recruit units (02-game-rules §5.1), iterated in building order. The Banner
     * Hall trains the banner thief (M32b, §21.5).
     */
    public static final Set<BuildingType> RECRUITING_BUILDINGS =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            BuildingType.BARRACKS,
                            BuildingType.STABLE,
                            BuildingType.WORKSHOP,
                            BuildingType.ACADEMY,
                            BuildingType.BANNER_HALL));

    public GameData {
        buildings = immutableEnumMap(BuildingType.class, buildings);
        units = immutableEnumMap(UnitType.class, units);
        validate(buildings, units);
    }

    /** The definition of a building. Never null for a validated GameData. */
    public BuildingDef building(BuildingType type) {
        return buildings.get(type);
    }

    /** The definition of a unit. Never null for a validated GameData. */
    public UnitDef unit(UnitType type) {
        return units.get(type);
    }

    /**
     * Checks the game data and throws if anything is wrong, so an invalid yml stops the application
     * at startup instead of causing strange behaviour later. Called from the constructor: every
     * {@code GameData} instance is valid.
     *
     * <p>All problems are collected first and reported together in one {@link
     * IllegalStateException}. Its message starts with {@code "Invalid game data:"} followed by one
     * problem per line, each naming the entry, e.g. {@code buildings.WALL: missing} or {@code
     * units.AXEMAN: baseSeconds must be > 0}.
     *
     * <p>What is checked:
     *
     * <ul>
     *   <li>Every {@link BuildingType} and every {@link UnitType} has a definition (the binder does
     *       not check that, and a value can be null).
     *   <li>Buildings: max level at least 1, start level within 0..max level, cost factor at least
     *       1, population base and points base not negative, base seconds and every base cost
     *       positive.
     *   <li>Units: recruiting building is a {@code BARRACKS}, {@code STABLE}, {@code WORKSHOP}, or
     *       {@code ACADEMY} and among the unit's requirements (so a unit is never available without
     *       its building); population, base seconds, speed and every cost positive; attack, defence
     *       and carry not negative; attack type set.
     *   <li>Unit levels (M30): stats never below the level before, cost and time positive,
     *       requirements reachable.
     *   <li>Requirements (buildings and units): every required level is between 1 and the max level
     *       of the required building, and a building does not require itself.
     * </ul>
     *
     * <p>Tests: {@code GameDataValidationTest}.
     */
    private static void validate(
            Map<BuildingType, BuildingDef> buildings, Map<UnitType, UnitDef> units) {
        List<String> problems = new ArrayList<>();
        String prefixBuildings = "buildings.";
        String prefixUnits = "units.";

        checkMissingEntries(prefixBuildings, buildings, BuildingType.values(), problems);
        checkMissingEntries(prefixUnits, units, UnitType.values(), problems);

        for (var entry : buildings.entrySet()) {
            BuildingType type = entry.getKey();
            BuildingDef def = entry.getValue();
            if (def == null) {
                continue;
            }

            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.maxLevel() >= 1,
                    "maxLevel must be >= 1");
            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.startLevel() >= 0 && def.startLevel() <= def.maxLevel(),
                    "startLevel must be in 0..maxLevel");
            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.costFactor() >= 1,
                    "costFactor must be >= 1");
            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.popBase() >= 0,
                    "popBase must be >= 0");
            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.baseSeconds() > 0,
                    "baseSeconds must be > 0");
            check(
                    problems,
                    prefixBuildings,
                    type.name(),
                    def.pointsBase() >= 0,
                    "pointsBase must be >= 0");

            if (def.baseCost() == null) {
                problems.add(prefixBuildings + type.name() + ": baseCost missing");
            } else {
                for (ResourceType resource : ResourceType.values()) {
                    check(
                            problems,
                            prefixBuildings,
                            type.name(),
                            def.baseCost().get(resource) > 0,
                            "baseCost." + resource.name().toLowerCase() + " must be > 0");
                }
            }

            check(
                    problems,
                    prefixBuildings,
                    type.name() + ".requires",
                    !def.requires().containsKey(type),
                    "building must not require itself");
            checkRequirements(problems, prefixBuildings, type.name(), def.requires(), buildings);
        }

        for (var entry : units.entrySet()) {
            UnitType type = entry.getKey();
            UnitDef def = entry.getValue();
            if (def == null) {
                continue;
            }

            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.building() != null && RECRUITING_BUILDINGS.contains(def.building()),
                    "building must be one of " + RECRUITING_BUILDINGS);
            check(problems, prefixUnits, type.name(), def.pop() > 0, "pop must be > 0");
            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.baseSeconds() > 0,
                    "baseSeconds must be > 0");
            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.minutesPerField() > 0,
                    "minutesPerField must be > 0");
            check(problems, prefixUnits, type.name(), def.attack() >= 0, "attack must be >= 0");
            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.defenseInfantry() >= 0,
                    "defenseInfantry must be >= 0");
            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.defenseCavalry() >= 0,
                    "defenseCavalry must be >= 0");
            check(problems, prefixUnits, type.name(), def.carry() >= 0, "carry must be >= 0");
            check(
                    problems,
                    prefixUnits,
                    type.name(),
                    def.attackType() != null,
                    "attackType missing");

            if (def.cost() == null) {
                problems.add(prefixUnits + type.name() + ": cost missing");
            } else {
                for (ResourceType resource : ResourceType.values()) {
                    check(
                            problems,
                            prefixUnits,
                            type.name(),
                            def.cost().get(resource) > 0,
                            "cost." + resource.name().toLowerCase() + " must be > 0");
                }
            }

            checkRequirements(problems, prefixUnits, type.name(), def.requires(), buildings);
            check(
                    problems,
                    prefixUnits,
                    type.name() + ".requires",
                    def.building() == null || def.requires().containsKey(def.building()),
                    "must require its building " + def.building());
            checkLevels(problems, prefixUnits, type, def, buildings);
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid game data:\n" + String.join("\n", problems));
        }
    }

    /**
     * The research steps of a unit (M30): every level keeps or raises the stats of the one before
     * it (so a level never makes a unit worse), costs and times are positive, the requirements are
     * reachable. The entry names the level it speaks of, e.g. {@code units.SPEARMAN.levels[2]}.
     */
    private static void checkLevels(
            List<String> problems,
            String prefix,
            UnitType type,
            UnitDef def,
            Map<BuildingType, BuildingDef> buildings) {
        UnitStats before = new UnitStats(def.attack(), def.defenseInfantry(), def.defenseCavalry());
        for (int i = 0; i < def.levels().size(); i++) {
            UnitLevelDef level = def.levels().get(i);
            String name = type.name() + ".levels[" + (i + 2) + "]";
            check(
                    problems,
                    prefix,
                    name,
                    level.attack() >= before.attack()
                            && level.defenseInfantry() >= before.defenseInfantry()
                            && level.defenseCavalry() >= before.defenseCavalry(),
                    "attack and defence must not be below the level before");
            check(problems, prefix, name, level.baseSeconds() > 0, "baseSeconds must be > 0");
            if (level.cost() == null) {
                problems.add(prefix + name + ": cost missing");
            } else {
                for (ResourceType resource : ResourceType.values()) {
                    check(
                            problems,
                            prefix,
                            name,
                            level.cost().get(resource) > 0,
                            "cost." + resource.name().toLowerCase() + " must be > 0");
                }
            }
            checkRequirements(problems, prefix, name, level.requires(), buildings);
            before = level.stats();
        }
    }

    private static <E extends Enum<E>, V> void checkMissingEntries(
            String prefix, Map<E, V> map, E[] enumValues, List<String> problems) {
        Arrays.stream(enumValues)
                .filter(e -> Objects.isNull(map.get(e)))
                .map(e -> prefix + e + ": missing")
                .forEach(problems::add);
    }

    /**
     * Every required level must be between 1 and the max level of the required building. A required
     * building without definition is skipped: it is already reported as missing.
     */
    private static void checkRequirements(
            List<String> problems,
            String prefix,
            String name,
            Map<BuildingType, Integer> requires,
            Map<BuildingType, BuildingDef> buildings) {
        for (var entry : requires.entrySet()) {
            BuildingType requiredType = entry.getKey();
            BuildingDef requiredDef = buildings.get(requiredType);
            Integer level = entry.getValue();
            if (requiredDef == null) {
                continue;
            }
            check(
                    problems,
                    prefix,
                    name + ".requires." + requiredType.name(),
                    level != null && level >= 1 && level <= requiredDef.maxLevel(),
                    "level must be between 1 and the maxLevel of " + requiredType);
        }
    }

    private static void check(
            List<String> problems, String prefix, String name, boolean ok, String description) {
        if (!ok) {
            problems.add(prefix + name + ": " + description);
        }
    }

    private static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(
            Class<K> keyType, Map<K, V> source) {
        var copy = new EnumMap<K, V>(keyType);
        if (source != null) {
            copy.putAll(source);
        }
        return Collections.unmodifiableMap(copy);
    }
}
