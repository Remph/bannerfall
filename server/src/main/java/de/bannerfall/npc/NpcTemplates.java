package de.bannerfall.npc;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The personality templates, bound from {@code gamedata/npc/offensive.yml}, {@code balanced.yml}
 * and {@code defensive.yml} (09-npc-lords §9), and the nine lord variants from {@code variants.yml}
 * (2.1, 09 §16): each an overlay on its personality's template, resolved here into a full {@link
 * Template}. The checks that need the game data are made by {@link NpcTemplateValidation}.
 */
@ConfigurationProperties("npc")
public final class NpcTemplates {

    private static final Logger log = LoggerFactory.getLogger(NpcTemplates.class);

    /** The variant key that means "the personality's own template" (09 §16.8). */
    public static final String PLAIN = "PLAIN";

    private final Map<NpcPersonality, Template> templates;
    private final Map<String, Variant> variants;
    private final Map<String, Template> resolved;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /** Only the three personalities, no variants (tests). */
    public NpcTemplates(Map<NpcPersonality, Template> templates) {
        this(templates, null);
    }

    @ConstructorBinding
    public NpcTemplates(Map<NpcPersonality, Template> templates, Map<String, Variant> variants) {
        var copy = new EnumMap<NpcPersonality, Template>(NpcPersonality.class);
        if (templates != null) {
            copy.putAll(templates);
        }
        this.templates = Collections.unmodifiableMap(copy);
        // sorted by key: the draw (09 §16.6) goes through them in a fixed order
        this.variants =
                Collections.unmodifiableMap(variants == null ? Map.of() : new TreeMap<>(variants));
        Map<String, Template> byKey = new HashMap<>();
        this.variants.forEach(
                (key, variant) -> {
                    Template base = variant.base() == null ? null : copy.get(variant.base());
                    if (base != null) {
                        byKey.put(key, variant.applyTo(base));
                    }
                });
        this.resolved = Collections.unmodifiableMap(byKey);
    }

    public Map<NpcPersonality, Template> templates() {
        return templates;
    }

    /** The overlays as bound, by key (sorted). */
    public Map<String, Variant> variants() {
        return variants;
    }

    public Template of(NpcPersonality personality) {
        return templates.get(personality);
    }

    /**
     * The resolved template of a variant (09 §16.5); null for an unknown key or a variant whose
     * base is missing.
     */
    public Template ofVariant(String key) {
        return resolved.get(key);
    }

    /**
     * The template a lord plays with (09 §16.5): its variant's, or its personality's for a lord
     * spawned before the variants (whose {@code template} is the personality's name). An unknown
     * key falls back to the personality's template with one WARN per key.
     */
    public Template of(NpcBrain brain) {
        String key = brain.getTemplate();
        if (key == null || key.equals(brain.getPersonality().name())) {
            return of(brain.getPersonality());
        }
        Template variant = resolved.get(key);
        if (variant != null && variants.get(key).base() == brain.getPersonality()) {
            return variant;
        }
        if (warned.add(key)) {
            log.warn(
                    "NPC lord {} has the unknown template {}, it plays as {}",
                    brain.getPlayerId(),
                    key,
                    brain.getPersonality());
        }
        return of(brain.getPersonality());
    }

    /** The variant keys of a personality, sorted (09 §16.6). */
    public List<String> variantsOf(NpcPersonality personality) {
        return variants.entrySet().stream()
                .filter(e -> e.getValue().base() == personality)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The variant key of a lord's template (09 §16.7), or null for a lord that plays its
     * personality's template (spawned before the variants, or as {@code PLAIN}).
     */
    public String variantKey(NpcBrain brain) {
        String key = brain.getTemplate();
        return key != null && variants.containsKey(key) ? key : null;
    }

    /**
     * One lord variant of {@code variants.yml} (09 §16.5): its {@code base} personality and only
     * the fields it changes; every null field keeps the base's value. A {@code mix} stage given
     * replaces that stage, a list given (stages, units, build, favourites) replaces the whole list.
     */
    public record Variant(
            NpcPersonality base,
            List<Stage> stages,
            Map<String, Map<UnitType, Integer>> mix,
            List<UnitType> farmUnits,
            List<UnitType> attackUnits,
            List<UnitType> supportUnits,
            Double homeGuard,
            Double supportShare,
            Double margin,
            Double villageRatio,
            Double humanPlayerRatio,
            Boolean grudgeOpens,
            Double grudgeTroopBonus,
            Boolean conquersPlayers,
            Integer farmRadius,
            Integer conquestRadius,
            Integer nobles,
            List<Map<BuildingType, Integer>> build,
            List<BannerType> bannerFavourites,
            List<BannerType> standardFavourites,
            Integer thieves,
            Boolean paladinRetaliates,
            Boolean raidFirst,
            Boolean humansOnlyOnGrudge,
            Double grudgeFactor,
            Integer retaliations,
            Double supportHorizonFactor,
            Double shipFrom,
            Double targetMinRatio,
            Boolean prefersFalling,
            Boolean spareLastVillage) {

        /** The base's template with this variant's fields put over it. */
        Template applyTo(Template t) {
            Map<String, Map<UnitType, Integer>> mixes = new LinkedHashMap<>(t.mix());
            if (mix != null) {
                mixes.putAll(mix);
            }
            return new Template(
                    or(stages, t.stages()),
                    mixes,
                    or(farmUnits, t.farmUnits()),
                    or(attackUnits, t.attackUnits()),
                    or(supportUnits, t.supportUnits()),
                    or(homeGuard, t.homeGuard()),
                    or(supportShare, t.supportShare()),
                    or(margin, t.margin()),
                    or(villageRatio, t.villageRatio()),
                    or(humanPlayerRatio, t.humanPlayerRatio()),
                    or(grudgeOpens, t.grudgeOpens()),
                    or(grudgeTroopBonus, t.grudgeTroopBonus()),
                    or(conquersPlayers, t.conquersPlayers()),
                    or(farmRadius, t.farmRadius()),
                    or(conquestRadius, t.conquestRadius()),
                    or(nobles, t.nobles()),
                    or(build, t.build()),
                    or(bannerFavourites, t.bannerFavourites()),
                    or(standardFavourites, t.standardFavourites()),
                    or(thieves, t.thieves()),
                    or(paladinRetaliates, t.paladinRetaliates()),
                    or(raidFirst, t.raidFirst()),
                    or(humansOnlyOnGrudge, t.humansOnlyOnGrudge()),
                    or(grudgeFactor, t.grudgeFactor()),
                    or(retaliations, t.retaliations()),
                    or(supportHorizonFactor, t.supportHorizonFactor()),
                    or(shipFrom, t.shipFrom()),
                    or(targetMinRatio, t.targetMinRatio()),
                    or(prefersFalling, t.prefersFalling()),
                    or(spareLastVillage, t.spareLastVillage()));
        }

        private static <T> T or(T value, T fallback) {
            return value != null ? value : fallback;
        }
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
     * @param raidFirst raids (player villages with fresh intel and no defenders) come before the
     *     barbarians in the farm order (2.1, 09 §16.4)
     * @param humansOnlyOnGrudge a human's villages are targets only while a grudge against him is
     *     active (09 §16.4)
     * @param grudgeFactor multiplies {@code grudge-hours} for the lord's own and shared grudges
     * @param retaliations retaliations per grudge (09 §6.7, §16.4)
     * @param supportHorizonFactor multiplies {@code support-horizon-hours} for the help step
     * @param shipFrom the warehouse share from which a village ships to its siblings (09 §5.5)
     * @param targetMinRatio a player is a target only with at least this share of the lord's
     *     points; null = no minimum. Set, it replaces {@code villageRatio} and {@code
     *     humanPlayerRatio} and a grudge doesn't lift it (09 §16.4)
     * @param prefersFalling players whose points fell at least 3 % since the last daily snapshot
     *     come first in the target order (09 §16.4)
     * @param spareLastVillage true = spare a human's last village whatever the difficulty says;
     *     null = the difficulty decides (09 §16.4)
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
            boolean paladinRetaliates,
            @DefaultValue("false") boolean raidFirst,
            @DefaultValue("false") boolean humansOnlyOnGrudge,
            @DefaultValue("1.0") double grudgeFactor,
            @DefaultValue("1") int retaliations,
            @DefaultValue("1.0") double supportHorizonFactor,
            @DefaultValue("0.9") double shipFrom,
            Double targetMinRatio,
            @DefaultValue("false") boolean prefersFalling,
            Boolean spareLastVillage) {

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
