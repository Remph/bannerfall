package de.bannerfall.combat;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.TheftResult;
import de.bannerfall.rules.BattleInput;
import de.bannerfall.rules.BattleResult;
import de.bannerfall.rules.StandardEffect;
import de.bannerfall.rules.UnitLevels;
import de.bannerfall.village.VillageRefDto;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one battle into the report payloads (02-game-rules §14): the attacker's copy and the
 * defenders' copy, which the village owner and every supporter get alike. Pure.
 *
 * <p>Visibility: the attacker sees the defending side (units and losses per type, wall, catapult
 * damage) only if at least one of his units survived, and what his scouts saw only if, in addition,
 * scouting succeeded – dead units can't tell anything. He sees only the totals, never which units
 * belong to a supporter: the per-stack list is for the defenders. The defenders always see
 * everything about the attackers, and whether their scouting succeeded, but not the scouted data
 * (it's their own village).
 */
final class BattleReports {

    /**
     * Scouts at this research level or higher show the attacker the defenders' levels (M30/M31).
     */
    static final int MIN_SCOUT_LEVEL_TO_SEE_LEVELS = 2;

    private BattleReports() {}

    /**
     * Everything the reports tell about one battle.
     *
     * @param attacker the attacking village (snapshot)
     * @param defender the attacked village (snapshot)
     * @param stackHomes the home village of each defending stack, in the order of {@code
     *     input.defenderStacks()}
     * @param stockBefore the target's stock rounded down, before the loot
     * @param buildingsBefore the target's building levels before the battle
     * @param bannerBefore the banner flying in the target before the battle (M32); null if none
     * @param standardBefore the paladin standard equipped in the target before the battle (1.4);
     *     null if none
     * @param loot what the attackers took
     * @param loyalty what surviving nobles did; null without
     * @param loyaltySeen what the attacker learns about the loyalty (02-game-rules §10): the same
     *     as {@code loyalty} with nobles, else the unchanged value; null if no attacker came back
     * @param banners what banners did (M32b)
     * @param haulCapacity what the surviving attackers could carry when they took loot (1.3); null
     *     when they took none (lost, or conquered the village)
     * @param away the target owner's own units away from the village (in support elsewhere and on
     *     the way) when the attack arrived; the attacker's copy shows them only after good scouting
     */
    record Battle(
            VillageRefDto attacker,
            VillageRefDto defender,
            BattleInput input,
            BattleResult result,
            List<VillageRefDto> stackHomes,
            ResourceAmounts stockBefore,
            Map<BuildingType, Integer> buildingsBefore,
            BattleReport.ScoutedBanner bannerBefore,
            ResourceAmounts loot,
            BattleReport.LoyaltyReport loyalty,
            BattleReport.LoyaltyReport loyaltySeen,
            Banners banners,
            Integer haulCapacity,
            Map<UnitType, Integer> away,
            BattleReport.ScoutedBanner standardBefore) {

        /** A battle without the target's paladin standard. */
        Battle(
                VillageRefDto attacker,
                VillageRefDto defender,
                BattleInput input,
                BattleResult result,
                List<VillageRefDto> stackHomes,
                ResourceAmounts stockBefore,
                Map<BuildingType, Integer> buildingsBefore,
                BattleReport.ScoutedBanner bannerBefore,
                ResourceAmounts loot,
                BattleReport.LoyaltyReport loyalty,
                BattleReport.LoyaltyReport loyaltySeen,
                Banners banners,
                Integer haulCapacity,
                Map<UnitType, Integer> away) {
            this(
                    attacker,
                    defender,
                    input,
                    result,
                    stackHomes,
                    stockBefore,
                    buildingsBefore,
                    bannerBefore,
                    loot,
                    loyalty,
                    loyaltySeen,
                    banners,
                    haulCapacity,
                    away,
                    null);
        }

        /** A battle without the units away. */
        Battle(
                VillageRefDto attacker,
                VillageRefDto defender,
                BattleInput input,
                BattleResult result,
                List<VillageRefDto> stackHomes,
                ResourceAmounts stockBefore,
                Map<BuildingType, Integer> buildingsBefore,
                BattleReport.ScoutedBanner bannerBefore,
                ResourceAmounts loot,
                BattleReport.LoyaltyReport loyalty,
                BattleReport.LoyaltyReport loyaltySeen,
                Banners banners,
                Integer haulCapacity) {
            this(
                    attacker,
                    defender,
                    input,
                    result,
                    stackHomes,
                    stockBefore,
                    buildingsBefore,
                    bannerBefore,
                    loot,
                    loyalty,
                    loyaltySeen,
                    banners,
                    haulCapacity,
                    Map.of());
        }

        /** A battle without the haul capacity. */
        Battle(
                VillageRefDto attacker,
                VillageRefDto defender,
                BattleInput input,
                BattleResult result,
                List<VillageRefDto> stackHomes,
                ResourceAmounts stockBefore,
                Map<BuildingType, Integer> buildingsBefore,
                BattleReport.ScoutedBanner bannerBefore,
                ResourceAmounts loot,
                BattleReport.LoyaltyReport loyalty,
                BattleReport.LoyaltyReport loyaltySeen,
                Banners banners) {
            this(
                    attacker,
                    defender,
                    input,
                    result,
                    stackHomes,
                    stockBefore,
                    buildingsBefore,
                    bannerBefore,
                    loot,
                    loyalty,
                    loyaltySeen,
                    banners,
                    null);
        }

        /** A battle without banners. */
        Battle(
                VillageRefDto attacker,
                VillageRefDto defender,
                BattleInput input,
                BattleResult result,
                List<VillageRefDto> stackHomes,
                ResourceAmounts stockBefore,
                Map<BuildingType, Integer> buildingsBefore,
                BattleReport.ScoutedBanner bannerBefore,
                ResourceAmounts loot,
                BattleReport.LoyaltyReport loyalty,
                BattleReport.LoyaltyReport loyaltySeen) {
            this(
                    attacker,
                    defender,
                    input,
                    result,
                    stackHomes,
                    stockBefore,
                    buildingsBefore,
                    bannerBefore,
                    loot,
                    loyalty,
                    loyaltySeen,
                    Banners.NONE);
        }

        Battle {
            away = away == null ? Map.of() : away;
            if (stackHomes.size() != input.defenderStacks().size()) {
                throw new IllegalArgumentException(
                        "one home village per defending stack: "
                                + stackHomes.size()
                                + " for "
                                + input.defenderStacks().size());
            }
        }
    }

    /**
     * What banners did in one battle (M32b, 02-game-rules §21).
     *
     * @param attackBonus the War banner the attack took along; 0 without
     * @param defenceBonus the target's Bulwark banner; 0 without
     * @param attackerFound the banner the attacker found; null if none
     * @param defenderFound the banner the village owner found; null if none
     * @param theft what the attacker's thieves did; null without thieves
     * @param attackStandard the paladin standard the attack carried (1.4, §21.18); null without
     * @param attackerFoundStandard the standard the attacker found; null if none
     * @param defenderFoundStandard the standard the village owner found; null if none
     * @param standardTheft the defenders' thieves' roll for the attack's standard; null without
     */
    record Banners(
            double attackBonus,
            double defenceBonus,
            BattleReport.ScoutedBanner attackerFound,
            BattleReport.ScoutedBanner defenderFound,
            BattleReport.TheftReport theft,
            BattleReport.StandardReport attackStandard,
            BattleReport.ScoutedBanner attackerFoundStandard,
            BattleReport.ScoutedBanner defenderFoundStandard,
            BattleReport.StandardTheftReport standardTheft) {

        static final Banners NONE = new Banners(0, 0, null, null, null);

        /** Without paladin standards. */
        Banners(
                double attackBonus,
                double defenceBonus,
                BattleReport.ScoutedBanner attackerFound,
                BattleReport.ScoutedBanner defenderFound,
                BattleReport.TheftReport theft) {
            this(
                    attackBonus,
                    defenceBonus,
                    attackerFound,
                    defenderFound,
                    theft,
                    null,
                    null,
                    null,
                    null);
        }

        /**
         * What the attacker learns of the defenders' standard theft: only that they took his
         * standard (his army died, it tells nothing more).
         */
        BattleReport.StandardTheftReport standardLost() {
            return standardTheft != null && standardTheft.stolen()
                    ? new BattleReport.StandardTheftReport(
                            null, null, true, standardTheft.standard())
                    : null;
        }

        /** The banner the thieves took; null if they took none. */
        BattleReport.ScoutedBanner stolen() {
            return theft != null && theft.result() == TheftResult.STOLEN ? theft.banner() : null;
        }
    }

    /** SCOUT for an attack with only scouts, else ATTACK. */
    static ReportKind attackerKind(BattleResult result) {
        return result.mainBattle() == null ? ReportKind.SCOUT : ReportKind.ATTACK;
    }

    static BattleOutcome outcome(BattleResult result) {
        if (result.mainBattle() == null) {
            return BattleOutcome.SCOUTS_ONLY;
        }
        return result.attackerWon() ? BattleOutcome.ATTACKER_WON : BattleOutcome.DEFENDER_WON;
    }

    /** The attacker's copy (kind {@link #attackerKind}). */
    static BattleReport forAttacker(Battle battle) {
        BattleResult result = battle.result();
        boolean someoneCameBack = !result.attackerSurvivors().isEmpty();
        BattleReport.ScoutingReport scouting = null;
        if (result.scouting() != null && someoneCameBack) {
            boolean succeeded = result.scouting().succeeded();
            scouting =
                    new BattleReport.ScoutingReport(
                            succeeded,
                            succeeded ? battle.stockBefore() : null,
                            succeeded ? battle.buildingsBefore() : null,
                            succeeded ? battle.bannerBefore() : null,
                            seesAway(result.scouting()) ? battle.away() : null,
                            succeeded ? battle.standardBefore() : null);
        }
        Banners banners = battle.banners();
        // the Bulwark is part of what a successful scout saw (the banner itself is)
        boolean sawBanner = scouting != null && scouting.succeeded();
        return report(
                battle,
                someoneCameBack ? defenderSide(battle, false) : null,
                scouting,
                someoneCameBack ? battle.loyaltySeen() : null,
                BattleReport.BannerReport.orNull(
                        bonus(banners.attackBonus()),
                        sawBanner ? bonus(banners.defenceBonus()) : null,
                        banners.attackerFound(),
                        banners.theft(),
                        null,
                        banners.attackStandard(),
                        banners.attackerFoundStandard(),
                        banners.standardLost()),
                battle.haulCapacity());
    }

    /**
     * The share of the scouts that must survive the scouting phase for the report to show the units
     * away from the village (balancing round 2, 02-game-rules §7.1).
     */
    static final double AWAY_SURVIVORS = 0.75;

    /** Whether the scouts see the units away: scouting succeeded and enough of them survived. */
    static boolean seesAway(BattleResult.Scouting scouting) {
        return scouting.succeeded()
                && scouting.sent() - scouting.lost() >= AWAY_SURVIVORS * scouting.sent();
    }

    /** The copy for the village owner (kind DEFENSE); supporters get {@link #forSupporters}. */
    static BattleReport forDefenders(Battle battle) {
        return forDefenders(battle, true);
    }

    /**
     * The copy for the players whose supports stood in the village (kind DEFENSE): like the
     * owner's, without the banner the owner found and the banner thieves took from him (M32b,
     * 02-game-rules §21.1: only the owner and successful scouts learn which banner flies there).
     */
    static BattleReport forSupporters(Battle battle) {
        return forDefenders(battle, false);
    }

    private static BattleReport forDefenders(Battle battle, boolean owner) {
        BattleResult.Scouting scouted = battle.result().scouting();
        BattleReport.ScoutingReport scouting =
                scouted == null
                        ? null
                        : new BattleReport.ScoutingReport(scouted.succeeded(), null, null, null);
        Banners banners = battle.banners();
        return report(
                battle,
                defenderSide(battle, true),
                scouting,
                battle.loyalty(),
                BattleReport.BannerReport.orNull(
                        bonus(banners.attackBonus()),
                        bonus(banners.defenceBonus()),
                        owner ? banners.defenderFound() : null,
                        null,
                        owner ? banners.stolen() : null,
                        banners.attackStandard(),
                        owner ? banners.defenderFoundStandard() : null,
                        owner ? banners.standardTheft() : null),
                null);
    }

    /** A paladin standard for the report (1.4); null without one. */
    static BattleReport.StandardReport standardOf(StandardEffect standard) {
        return standard.type() == null
                ? null
                : new BattleReport.StandardReport(
                        standard.type(), standard.tier(), standard.effectiveTier());
    }

    /** A bonus for the report: null for 0, so a battle without banners shows none. */
    private static Double bonus(double fraction) {
        return fraction == 0 ? null : fraction;
    }

    private static BattleReport report(
            Battle battle,
            BattleReport.DefenderSide defenderSide,
            BattleReport.ScoutingReport scouting,
            BattleReport.LoyaltyReport loyalty,
            BattleReport.BannerReport banners,
            Integer haulCapacity) {
        BattleInput input = battle.input();
        BattleResult result = battle.result();
        return new BattleReport(
                battle.attacker(),
                battle.defender(),
                outcome(result),
                result.mainBattle() == null ? null : input.luck(),
                input.attacker(),
                result.attackerLosses(),
                levelsOf(input.attacker(), input.attackerLevels()),
                input.catapultTarget(),
                defenderSide,
                scouting,
                battle.loot(),
                loyalty,
                banners,
                haulCapacity,
                // the night bonus (1.4, §8.1) only matters in a main battle; both copies show it
                result.mainBattle() != null && input.nightFactor() > 1 ? input.nightFactor() : null,
                // morale (1.6, §8.2) likewise, and only below full strength
                result.mainBattle() != null && input.morale() < 1 ? input.morale() : null);
    }

    /** The levels above 1 of the unit types that are in {@code units} (M30). */
    private static Map<UnitType, Integer> levelsOf(
            Map<UnitType, Integer> units, UnitLevels levels) {
        var result = new EnumMap<UnitType, Integer>(UnitType.class);
        units.keySet()
                .forEach(
                        type -> {
                            if (levels.get(type) > 1) {
                                result.put(type, levels.get(type));
                            }
                        });
        return result;
    }

    /**
     * The totals, the wall and the catapult target's levels; with {@code perStack} also every stack
     * that had units (with its home village), else no stacks (the attacker's copy).
     */
    private static BattleReport.DefenderSide defenderSide(Battle battle, boolean perStack) {
        BattleInput input = battle.input();
        BattleResult result = battle.result();
        // upgraded scouts (level 2 or more) that succeeded see the defenders' levels (attacker's
        // copy)
        boolean seesLevels =
                !perStack
                        && result.scouting() != null
                        && result.scouting().succeeded()
                        && input.attackerLevels().get(UnitType.SCOUT)
                                >= MIN_SCOUT_LEVEL_TO_SEE_LEVELS;
        var seen = new EnumMap<UnitType, Integer>(UnitType.class);
        var units = new EnumMap<UnitType, Integer>(UnitType.class);
        var losses = new EnumMap<UnitType, Integer>(UnitType.class);
        List<BattleReport.DefenderStack> stacks = new ArrayList<>();
        for (int i = 0; i < input.defenderStacks().size(); i++) {
            Map<UnitType, Integer> stackUnits = input.defenderStacks().get(i);
            if (stackUnits.isEmpty()) {
                continue;
            }
            Map<UnitType, Integer> stackLosses = result.defenderLosses().get(i);
            stackUnits.forEach((type, count) -> units.merge(type, count, Integer::sum));
            stackLosses.forEach((type, count) -> losses.merge(type, count, Integer::sum));
            if (seesLevels) {
                levelsOf(stackUnits, input.defenderLevels().get(i))
                        .forEach((type, level) -> seen.merge(type, level, Math::max));
            }
            if (perStack) {
                stacks.add(
                        new BattleReport.DefenderStack(
                                battle.stackHomes().get(i),
                                stackUnits,
                                stackLosses,
                                levelsOf(stackUnits, input.defenderLevels().get(i)),
                                standardOf(input.defenderStandards().get(i))));
            }
        }
        boolean catapults = input.catapultTarget() != null;
        return new BattleReport.DefenderSide(
                units,
                losses,
                stacks,
                input.wallLevel(),
                result.wallAfter(),
                catapults ? input.catapultTargetLevel() : null,
                catapults ? result.catapultTargetLevelAfter() : null,
                seen);
    }
}
