package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.StandardEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All banner numbers (02-game-rules §21), bound from {@code gamedata/banners.yml}. The constructor
 * validates them, so a broken file stops the application at startup. M32a uses {@link #effects};
 * the drop, theft and fusing numbers are read by the battle and fusing milestones.
 *
 * @param effects per banner type the effect of tier I, II and III as a fraction
 * @param drops the drop rules of 02 §21.7
 * @param theft the theft rules of 02 §21.6.2
 * @param fusing the fusing rule of 02 §21.4.2
 */
@ConfigurationProperties("banners")
public record BannerCatalog(
        Map<BannerType, List<Double>> effects, Drops drops, Theft theft, Fusing fusing) {

    /** Tiers a banner has. */
    public static final int TIERS = 3;

    /**
     * @param playerChance chance per eligible win against a player or an NPC lord
     * @param barbarianChance chance per eligible win against a barbarian village
     * @param tierOdds percent for tier I, II and III, adding up to 100
     * @param minLoserPop population the losing side fought with at least
     * @param pairHours real hours between two rolls of the same winner and loser
     * @param maxPerDay drops per player in 24 real hours
     */
    public record Drops(
            double playerChance,
            double barbarianChance,
            List<Integer> tierOdds,
            int minLoserPop,
            int pairHours,
            int maxPerDay) {}

    /**
     * @param chancePerThief chance one surviving thief steals the banner, per thief level (Hall
     *     training, 1.4): the first entry is level 1
     * @param maxChance the chance never goes above this
     * @param hallReductionPerLevel the target's Banner Hall makes it harder by this per level
     */
    public record Theft(
            List<Double> chancePerThief, double maxChance, double hallReductionPerLevel) {

        public Theft {
            chancePerThief = chancePerThief == null ? List.of() : List.copyOf(chancePerThief);
        }

        /**
         * The chance per surviving thief at a thief level; a level past the list counts as its last
         * entry (so a changed banners.yml never breaks a trained level).
         *
         * @param thiefLevel 1 or more
         */
        public double perThief(int thiefLevel) {
            if (thiefLevel < 1) {
                throw new IllegalArgumentException("thief level must be at least 1: " + thiefLevel);
            }
            return chancePerThief.get(Math.min(thiefLevel, chancePerThief.size()) - 1);
        }

        /**
         * The chance of {@code survivors} thieves of one level to steal a village's banner
         * (02-game-rules §21.6.2): {@link BannerRules#theftChance} with this catalog's numbers and
         * the Banner Hall's reduction. The real theft and the simulator both ask here.
         */
        public double chance(int survivors, int hallLevel, int thiefLevel) {
            return BannerRules.theftChance(
                    survivors, hallLevel, perThief(thiefLevel), maxChance, hallReductionPerLevel);
        }

        /**
         * The chance of the defenders' surviving thieves to take an attack's paladin standard
         * (§21.18): like {@link #chance}, without a hall's reduction.
         */
        public double standardChance(int survivors, int thiefLevel) {
            return BannerRules.theftChance(survivors, 0, perThief(thiefLevel), maxChance, 0);
        }
    }

    /**
     * @param bannersNeeded banners of one tier that make one of the next
     */
    public record Fusing(int bannersNeeded) {}

    public BannerCatalog {
        Map<BannerType, List<Double>> copy = new EnumMap<>(BannerType.class);
        if (effects != null) {
            effects.forEach((type, values) -> copy.put(type, List.copyOf(values)));
        }
        effects = Collections.unmodifiableMap(copy);
        validate(effects, drops, theft, fusing);
    }

    /** The effect of a banner of {@code type} at {@code tier} (1–3), as a fraction. */
    public double effect(BannerType type, int tier) {
        if (tier < 1 || tier > TIERS) {
            throw new IllegalArgumentException("tier must be 1.." + TIERS + ": " + tier);
        }
        return effects.get(type).get(tier - 1);
    }

    /**
     * A banner of {@code tier} flying in a village whose Banner Hall has {@code hallLevel}
     * (02-game-rules §21.3): the tier it works at ({@link BannerRules#effectiveTier}) and its
     * effect, 0 while it sleeps. The battle, the simulator and the NPC estimate all take a
     * village's banner from here.
     */
    public BannerEffect flying(BannerType type, int tier, int hallLevel) {
        int effective = BannerRules.effectiveTier(tier, hallLevel);
        return new BannerEffect(
                type, tier, effective, effective == 0 ? 0 : effect(type, effective));
    }

    /**
     * A paladin standard of {@code tier} that works at {@code effectiveTier} ({@link
     * BannerRules#standardTier}, §21.18) and its effect, 0 while it sleeps.
     */
    public StandardEffect standard(BannerType type, int tier, int effectiveTier) {
        return new StandardEffect(
                type, tier, effectiveTier, effectiveTier == 0 ? 0 : effect(type, effectiveTier));
    }

    private static void validate(
            Map<BannerType, List<Double>> effects, Drops drops, Theft theft, Fusing fusing) {
        List<String> problems = new ArrayList<>();
        for (BannerType type : BannerType.values()) {
            List<Double> values = effects.get(type);
            if (values == null || values.size() != TIERS) {
                problems.add("effects." + type + ": needs exactly " + TIERS + " tiers");
                continue;
            }
            double previous = 0;
            for (double value : values) {
                // a tier is worth more than the one before, and below 100 % (a time must stay > 0)
                if (value <= previous || value >= 1) {
                    problems.add("effects." + type + ": tiers must ascend, above 0 and below 1");
                    break;
                }
                previous = value;
            }
        }
        if (drops == null) {
            problems.add("drops: missing");
        } else {
            check(
                    problems,
                    drops.playerChance() > 0 && drops.playerChance() <= 1,
                    "drops.player-chance must be in (0, 1]");
            check(
                    problems,
                    drops.barbarianChance() > 0 && drops.barbarianChance() <= 1,
                    "drops.barbarian-chance must be in (0, 1]");
            check(
                    problems,
                    drops.tierOdds() != null
                            && drops.tierOdds().size() == TIERS
                            && drops.tierOdds().stream().allMatch(odds -> odds >= 0)
                            && drops.tierOdds().stream().mapToInt(Integer::intValue).sum() == 100,
                    "drops.tier-odds must be three percentages adding up to 100");
            check(problems, drops.minLoserPop() >= 0, "drops.min-loser-pop must be >= 0");
            check(problems, drops.pairHours() >= 0, "drops.pair-hours must be >= 0");
            check(problems, drops.maxPerDay() >= 1, "drops.max-per-day must be >= 1");
        }
        if (theft == null) {
            problems.add("theft: missing");
        } else {
            boolean ascending = !theft.chancePerThief().isEmpty();
            double previous = 0;
            for (double chance : theft.chancePerThief()) {
                ascending &= chance > previous && chance < 1;
                previous = chance;
            }
            check(
                    problems,
                    ascending,
                    "theft.chance-per-thief must be one or more ascending chances in (0, 1)");
            check(
                    problems,
                    theft.maxChance() > 0 && theft.maxChance() <= 1,
                    "theft.max-chance must be in (0, 1]");
            check(
                    problems,
                    theft.hallReductionPerLevel() >= 0 && theft.hallReductionPerLevel() * TIERS < 1,
                    "theft.hall-reduction-per-level must be >= 0 and leave a chance at hall 3");
        }
        if (fusing == null) {
            problems.add("fusing: missing");
        } else {
            check(problems, fusing.bannersNeeded() >= 2, "fusing.banners-needed must be >= 2");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid banners.yml:\n" + String.join("\n", problems));
        }
    }

    private static void check(List<String> problems, boolean ok, String description) {
        if (!ok) {
            problems.add(description);
        }
    }
}
