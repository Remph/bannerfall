package de.bannerfall.simulator;

import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.rules.BannerRules;
import de.bannerfall.rules.BattleInput;
import de.bannerfall.rules.BattleResult;
import de.bannerfall.rules.Combat;
import de.bannerfall.rules.Loot;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.rules.UnitLevels;
import de.bannerfall.world.WorldService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The attack simulator (02-game-rules §20): runs {@link Combat#fight}, the very code real battles
 * use, on numbers the player types in. Nothing is read from or written to the database, so the
 * player can try any army, also units and buildings he doesn't have.
 */
@Service
public class SimulatorService {

    private final GameData gameData;
    private final WorldService worldService;
    private final BannerCatalog banners;

    SimulatorService(GameData gameData, WorldService worldService, BannerCatalog banners) {
        this.gameData = gameData;
        this.worldService = worldService;
        this.banners = banners;
    }

    /**
     * The battle with the luck at its worst, at none and at its best.
     *
     * @throws GameRuleException {@code EMPTY_COMMAND} without an attacking unit
     * @throws ApiException 400 {@code VALIDATION} for a level above the building's maximum, or two
     *     banners for one village (War and Plunder, Bulwark and Oath)
     */
    public SimulationDto simulate(SimulatorRequest request) {
        Map<UnitType, Integer> attacker = units(request.attacker());
        Map<UnitType, Integer> defender = units(request.defender());
        if (attacker.isEmpty()) {
            throw new GameRuleException(ErrorCode.EMPTY_COMMAND, "The attack has no units");
        }
        int wall = request.wall();
        requireLevel(BuildingType.WALL, wall);
        int hall = request.bannerHall() == null ? 0 : request.bannerHall();
        requireLevel(BuildingType.BANNER_HALL, hall);

        // like a real attack: only catapults aim, at the Town Hall unless told otherwise
        BuildingType target = null;
        int targetLevel = 0;
        if (attacker.containsKey(UnitType.CATAPULT)) {
            target =
                    request.catapultTarget() != null
                            ? request.catapultTarget()
                            : BuildingType.HEADQUARTERS;
            targetLevel =
                    target == BuildingType.WALL
                            ? wall
                            : target == BuildingType.BANNER_HALL
                                    ? hall
                                    : request.catapultTargetLevel() == null
                                            ? 0
                                            : request.catapultTargetLevel();
            requireLevel(target, targetLevel);
        }
        int loyalty = request.loyalty() == null ? (int) Loyalty.MAX : request.loyalty();

        // unit levels (M30) only count in a world that has them; the sides' own maps otherwise
        boolean levelsOn = worldService.settings().unitLevelsEnabled();
        UnitLevels attackerLevels = levelsOn ? levels(request.attackerLevels()) : UnitLevels.NONE;
        UnitLevels defenderLevels = levelsOn ? levels(request.defenderLevels()) : UnitLevels.NONE;

        // banners (M32c) like in a real battle: War of the attacker's village, Bulwark capped by
        // the target's hall (02-game-rules §21.2); nothing while the world has them off
        boolean bannersOn = worldService.settings().bannersEnabled();
        double attackBonus = bannersOn ? effect(BannerType.WAR, request.warTier()) : 0;
        double defenceBonus =
                bannersOn
                        ? effect(
                                BannerType.BULWARK,
                                BannerRules.effectiveTier(
                                        request.bulwarkTier() == null ? 0 : request.bulwarkTier(),
                                        hall))
                        : 0;
        // a village flies one banner (§21.1): the attack brings War or Plunder, the target has
        // Bulwark or Oath
        if (positive(request.warTier()) && positive(request.plunderTier())) {
            throw oneBanner("attacking");
        }
        if (positive(request.bulwarkTier()) && positive(request.oathTier())) {
            throw oneBanner("target");
        }
        double plunderBonus = bannersOn ? effect(BannerType.PLUNDER, request.plunderTier()) : 0;
        double oathBonus =
                bannersOn
                        ? effect(
                                BannerType.OATH,
                                BannerRules.effectiveTier(
                                        request.oathTier() == null ? 0 : request.oathTier(), hall))
                        : 0;
        int thieves = attacker.getOrDefault(UnitType.BANNER_THIEF, 0);
        boolean theft = bannersOn && thieves > 0;

        double luckRange = worldService.settings().luckRange();
        List<Double> lucks = luckRange > 0 ? List.of(-luckRange, 0.0, luckRange) : List.of(0.0);
        List<SimulationDto.Scenario> scenarios = new ArrayList<>();
        for (double luck : lucks) {
            BattleInput input =
                    new BattleInput(
                            attacker,
                            List.of(defender),
                            wall,
                            target,
                            targetLevel,
                            luck,
                            attackerLevels,
                            List.of(defenderLevels),
                            attackBonus,
                            defenceBonus);
            BattleResult result = Combat.fight(gameData, input);
            scenarios.add(
                    scenario(
                            input,
                            result,
                            loyalty,
                            oathBonus,
                            plunderBonus,
                            theft ? theft(input, result, thieves, hall) : null));
        }
        return new SimulationDto(
                List.copyOf(scenarios), attackBonus, defenceBonus, plunderBonus, oathBonus);
    }

    private static boolean positive(Integer tier) {
        return tier != null && tier > 0;
    }

    private static ApiException oneBanner(String village) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION,
                "The " + village + " village flies one banner, not two");
    }

    /** The effect of a banner at a tier, 0 for none (null or 0). */
    private double effect(BannerType type, Integer tier) {
        return tier == null || tier < 1 ? 0 : banners.effect(type, tier);
    }

    /**
     * 02-game-rules §21.6.2: the survivors' chance after a won battle, with the hall as the
     * catapults left it.
     */
    private SimulationDto.Theft theft(
            BattleInput input, BattleResult result, int sent, int hallBefore) {
        int survived = result.attackerSurvivors().getOrDefault(UnitType.BANNER_THIEF, 0);
        if (!result.attackerWon() || survived == 0) {
            return new SimulationDto.Theft(sent, survived, 0);
        }
        int hall =
                input.catapultTarget() == BuildingType.BANNER_HALL
                        ? result.catapultTargetLevelAfter()
                        : hallBefore;
        BannerCatalog.Theft rules = banners.theft();
        return new SimulationDto.Theft(
                sent,
                survived,
                BannerRules.theftChance(
                        survived,
                        hall,
                        rules.chancePerThief(),
                        rules.maxChance(),
                        rules.hallReductionPerLevel()));
    }

    private SimulationDto.Scenario scenario(
            BattleInput input,
            BattleResult result,
            int loyalty,
            double oath,
            double plunder,
            SimulationDto.Theft theft) {
        BattleResult.MainBattle battle = result.mainBattle();
        BattleOutcome outcome =
                battle == null
                        ? BattleOutcome.SCOUTS_ONLY
                        : battle.attackerWon()
                                ? BattleOutcome.ATTACKER_WON
                                : BattleOutcome.DEFENDER_WON;
        Map<UnitType, Integer> defenders = input.defenderStacks().get(0);
        Map<UnitType, Integer> defenderLosses = result.defenderLosses().get(0);
        var defenderSurvivors = new EnumMap<UnitType, Integer>(UnitType.class);
        defenders.forEach(
                (type, count) -> {
                    int left = count - defenderLosses.getOrDefault(type, 0);
                    if (left > 0) {
                        defenderSurvivors.put(type, left);
                    }
                });
        BuildingType target = input.catapultTarget();
        BattleResult.Scouting scouting = result.scouting();
        return new SimulationDto.Scenario(
                input.luck(),
                outcome,
                battle == null ? null : battle.attack(),
                battle == null ? null : battle.defense(),
                battle == null ? null : battle.effectiveWall(),
                result.attackerLosses(),
                result.attackerSurvivors(),
                defenderLosses,
                Map.copyOf(defenderSurvivors),
                input.wallLevel(),
                result.wallAfter(),
                target,
                target == null ? null : input.catapultTargetLevel(),
                target == null ? null : result.catapultTargetLevelAfter(),
                scouting == null
                        ? null
                        : new SimulationDto.Scouting(
                                scouting.sent(),
                                scouting.defending(),
                                scouting.lost(),
                                scouting.succeeded()),
                outlook(result, loyalty, oath),
                Loot.capacity(gameData, result.attackerSurvivors(), plunder),
                theft);
    }

    /**
     * 02-game-rules §10: only a won battle lets the surviving nobles work, every one lowers the
     * loyalty by 20 to 35, each hit less the Oath banner and rounded like in {@link
     * Loyalty#afterNobles(double, int, de.bannerfall.common.RandomSource, double)}.
     */
    private static SimulationDto.LoyaltyOutlook outlook(
            BattleResult result, int loyalty, double oath) {
        int nobles = result.attackerSurvivors().getOrDefault(UnitType.NOBLE, 0);
        if (!result.attackerWon() || nobles == 0) {
            return null;
        }
        long weakest = (long) nobles * hit(Loyalty.MIN_HIT, oath);
        long strongest = (long) nobles * hit(Loyalty.MAX_HIT, oath);
        SimulationDto.Conquest conquered =
                loyalty - weakest <= 0
                        ? SimulationDto.Conquest.YES
                        : loyalty - strongest <= 0
                                ? SimulationDto.Conquest.POSSIBLE
                                : SimulationDto.Conquest.NO;
        return new SimulationDto.LoyaltyOutlook(
                nobles,
                loyalty,
                (int) Math.max(0, loyalty - weakest),
                (int) Math.max(0, loyalty - strongest),
                conquered);
    }

    /** One noble's hit after the Oath, rounded like a real one. */
    private static long hit(int hit, double oath) {
        return oath == 0 ? hit : Math.round(hit * (1 - oath));
    }

    private static UnitLevels levels(Map<UnitType, Integer> levels) {
        return levels == null ? UnitLevels.NONE : new UnitLevels(levels);
    }

    /** The counts above 0 only, in the canonical order. */
    private static Map<UnitType, Integer> units(Map<UnitType, Integer> counts) {
        var result = new EnumMap<UnitType, Integer>(UnitType.class);
        counts.forEach(
                (type, count) -> {
                    if (count > 0) {
                        result.put(type, count);
                    }
                });
        return result;
    }

    private void requireLevel(BuildingType type, int level) {
        if (level > gameData.building(type).maxLevel()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "The " + type + " has no level " + level);
        }
    }
}
