package de.bannerfall.automation;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.world.WorldService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Build plans (02-game-rules §25, 1.6): up to five per player, each a name and an ordered list of
 * buildings and levels with at most one recruit line. A plan is only a list; the village automation
 * queues its levels through the normal build queue.
 */
@Service
public class BuildPlanService {

    /** Longest plan name: a button on a phone (like the templates). */
    public static final int MAX_NAME_LENGTH = 16;

    /** Most lines in a plan: every building from scratch to its top level takes about 30. */
    public static final int MAX_LINES = 80;

    private final BuildPlanRepository plans;
    private final PlayerRepository players;
    private final GameData gameData;
    private final AutomationService automation;
    private final WorldService worldService;

    BuildPlanService(
            BuildPlanRepository plans,
            PlayerRepository players,
            GameData gameData,
            AutomationService automation,
            WorldService worldService) {
        this.plans = plans;
        this.players = players;
        this.gameData = gameData;
        this.automation = automation;
        this.worldService = worldService;
    }

    /** Every building with its start and max level and its requirements, for the plan editor. */
    public List<PlanBuildingDto> buildings() {
        boolean hall = worldService.settings().bannerHallAvailable();
        List<PlanBuildingDto> result = new ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            result.add(
                    PlanBuildingDto.of(
                            type,
                            gameData.building(type),
                            hall || type != BuildingType.BANNER_HALL));
        }
        return result;
    }

    /** The player's plans, A to E as far as he has made them. */
    @Transactional(readOnly = true)
    public List<BuildPlanDto> plans(long playerId) {
        return plans.findByPlayerIdOrderByLetterAsc(playerId).stream()
                .map(BuildPlanDto::of)
                .toList();
    }

    /**
     * Makes or changes one of the player's plans.
     *
     * @throws ApiException 400 {@code VALIDATION} for a line that isn't valid (see {@link
     *     #checked})
     */
    @Transactional
    public BuildPlanDto save(
            long playerId, BuildPlanLetter letter, String name, List<BuildPlanLine> lines) {
        List<BuildPlanLine> checked = checked(lines, gameData);
        // two saves of a new letter at once would both insert: the player row serialises them
        players.lockById(playerId);
        BuildPlan plan = plans.findByPlayerIdAndLetter(playerId, letter).orElse(null);
        if (plan == null) {
            plan = plans.save(new BuildPlan(playerId, letter, name.trim(), checked));
        } else {
            plan.change(name.trim(), checked);
        }
        automation.planChanged(playerId, letter);
        return BuildPlanDto.of(plan);
    }

    /**
     * Deletes one of the player's plans. Villages that worked through it stop (their status says
     * the plan is gone) until the player picks another.
     *
     * @throws NotFoundException {@code BUILD_PLAN_NOT_FOUND}
     */
    @Transactional
    public void delete(long playerId, BuildPlanLetter letter) {
        BuildPlan plan =
                plans.findByPlayerIdAndLetter(playerId, letter)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.BUILD_PLAN_NOT_FOUND));
        plans.delete(plan);
    }

    /**
     * The lines as they are stored: a building line names a building and a level of 1 … its max
     * level; the mines line (1.6.2) names a level of 1 … the lowest top level of the three mines
     * and no building; the recruit line names a recruit template and nothing else (its level is
     * stored as 0), and there is at most one. Each building's requirements must be met by the lines
     * in front of it, starting from the levels of a new village, so the plan never waits on a
     * building it doesn't build itself.
     */
    static List<BuildPlanLine> checked(List<BuildPlanLine> lines, GameData data) {
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw invalid("A plan has 1 … " + MAX_LINES + " lines");
        }
        Map<BuildingType, Integer> reached = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            reached.put(type, data.building(type).startLevel());
        }
        List<BuildPlanLine> result = new ArrayList<>();
        boolean recruit = false;
        for (int i = 0; i < lines.size(); i++) {
            BuildPlanLine line = lines.get(i);
            if (line.startsRecruiting()) {
                if (line.building() != null || line.mines()) {
                    throw invalid("Line " + (i + 1) + " names a building and a recruit template");
                }
                if (recruit) {
                    throw invalid("A plan has at most one recruit line");
                }
                recruit = true;
                result.add(BuildPlanLine.recruit(line.recruit()));
                continue;
            }
            if (line.mines()) {
                if (line.building() != null) {
                    throw invalid("Line " + (i + 1) + " names a building and the mines");
                }
                int top =
                        BuildPlanLine.MINES.stream()
                                .mapToInt(mine -> data.building(mine).maxLevel())
                                .min()
                                .orElseThrow();
                if (line.level() < 1 || line.level() > top) {
                    throw invalid("Line " + (i + 1) + ": the level must be 1 … " + top);
                }
                for (BuildingType mine : BuildPlanLine.MINES) {
                    requireAbove(i, mine, data, reached);
                    reached.merge(mine, line.level(), Math::max);
                }
                result.add(BuildPlanLine.allMines(line.level()));
                continue;
            }
            if (line.building() == null) {
                throw invalid("Line " + (i + 1) + " names no building");
            }
            BuildingDef def = data.building(line.building());
            if (line.level() < 1 || line.level() > def.maxLevel()) {
                throw invalid("Line " + (i + 1) + ": the level must be 1 … " + def.maxLevel());
            }
            requireAbove(i, line.building(), data, reached);
            reached.merge(line.building(), line.level(), Math::max);
            result.add(BuildPlanLine.build(line.building(), line.level()));
        }
        return result;
    }

    private static void requireAbove(
            int index, BuildingType type, GameData data, Map<BuildingType, Integer> reached) {
        BuildingDef def = data.building(type);
        if (!BuildRules.missingRequirements(def.requires(), reached).isEmpty()) {
            throw invalid(
                    "Line "
                            + (index + 1)
                            + ": "
                            + type
                            + " needs "
                            + def.requires()
                            + " in a line above it");
        }
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, detail);
    }
}
