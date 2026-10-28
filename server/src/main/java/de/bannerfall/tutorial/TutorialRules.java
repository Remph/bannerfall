package de.bannerfall.tutorial;

import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.world.WorldSettings;

/** The rules of the tutorial's steps and lessons (02-game-rules §27), without any state. */
public final class TutorialRules {

    /** How many spearmen the recruiting step asks for. */
    static final int SPEARMEN_ASKED = 5;

    /** Town Hall level the Barracks and the Market need. */
    static final int HEADQUARTERS_ASKED = 3;

    private TutorialRules() {}

    /**
     * Whether the world has this step at all: a step for a feature the world has switched off never
     * appears.
     */
    public static boolean inWorld(TutorialStep step, WorldSettings world) {
        return switch (step) {
            case GUILD -> world.guildsEnabled();
            case MAP_BARBARIAN -> hasBarbarians(world);
            case LESSON_UNIT_LEVELS -> world.unitLevelsEnabled();
            case LESSON_AUTOMATION -> world.automationVillages() > 0;
            case LESSON_BANNER_HALL -> world.bannersEnabled();
            default -> true;
        };
    }

    /** Whether the world has barbarian villages at all: from the start, per player or grown. */
    private static boolean hasBarbarians(WorldSettings world) {
        return world.barbarianInitialCount() > 0
                || world.barbariansPerNewPlayer() > 0
                || world.barbarianGrowthEnabled();
    }

    /**
     * Whether the player's game has reached the point where the step makes sense (its building is
     * open to him, its feature exists). The step before it in the chain is checked by the service.
     */
    public static boolean available(TutorialStep step, TutorialFacts facts, WorldSettings world) {
        return switch (step) {
            case BARRACKS -> facts.headquarters() >= HEADQUARTERS_ASKED;
            case SPEARMEN -> facts.barracks() >= 1;
            case MARKET -> facts.headquarters() >= HEADQUARTERS_ASKED && facts.warehouse() >= 2;
            case LESSON_UNIT_LEVELS -> world.unitLevelsEnabled() && facts.smithy() >= 1;
            case LESSON_CONQUEST -> facts.academy() >= 1;
            case LESSON_AUTOMATION ->
                    world.automationVillages() > 0
                            && facts.villages() >= world.automationVillages();
            case LESSON_BANNER_HALL -> world.bannersEnabled() && facts.bannerHall() >= 1;
            default -> true;
        };
    }

    /** Whether the game state shows a {@link TutorialStep.Detection#STATE} step as done. */
    public static boolean done(TutorialStep step, TutorialFacts facts) {
        return switch (step) {
            case WOODCUTTER -> facts.woodcutter() >= 2;
            case HEADQUARTERS -> facts.headquarters() >= HEADQUARTERS_ASKED;
            case BARRACKS -> facts.barracks() >= 1;
            case SPEARMEN -> facts.spearmen() >= SPEARMEN_ASKED;
            case ATTACK -> facts.attackSent();
            case REPORT -> facts.attackReportRead();
            case FARM_LIST -> facts.farmListEntries() > 0;
            case MARKET -> facts.market() >= 1;
            case GUILD -> facts.inGuild();
            default -> false;
        };
    }

    /**
     * What finishing the step gives back, the price of what it asked for; {@link
     * ResourceAmounts#ZERO} for a step without a refund.
     */
    public static ResourceAmounts refund(TutorialStep step, GameData gameData) {
        return switch (step) {
            case WOODCUTTER -> buildingCost(gameData, BuildingType.WOODCUTTER, 2);
            case BARRACKS -> buildingCost(gameData, BuildingType.BARRACKS, 1);
            case MARKET -> buildingCost(gameData, BuildingType.MARKET, 1);
            case SPEARMEN -> RecruitRules.cost(gameData.unit(UnitType.SPEARMAN), SPEARMEN_ASKED);
            default -> ResourceAmounts.ZERO;
        };
    }

    private static ResourceAmounts buildingCost(GameData gameData, BuildingType type, int level) {
        BuildingDef def = gameData.building(type);
        return BuildRules.cost(def, level);
    }
}
