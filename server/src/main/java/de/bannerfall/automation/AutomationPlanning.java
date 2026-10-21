package de.bannerfall.automation;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.ResourceType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.recruit.RecruitBuildingDto;
import de.bannerfall.recruit.RecruitOrderDto;
import de.bannerfall.recruit.RecruitOrderRequest;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.unit.UnitCounts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The rules of the village automation (02-game-rules §25) as plain functions: which line of a build
 * plan is next, whether the recruit line is reached, when the stock pays a cost, and what the
 * recruit template recruits now. {@code AutomationService} feeds them a locked village.
 */
public final class AutomationPlanning {

    /** The automation keeps at most this many of its own orders in the build queue. */
    public static final int OWN_BUILD_ORDERS = 2;

    /** A recruiting building gets new automatic orders while its queue holds fewer than this. */
    public static final int RECRUIT_QUEUE_BUSY = 2;

    /** Units the automation never recruits: they stay a decision of the player (decided in 1.6). */
    public static final Set<UnitType> BY_HAND = EnumSet.of(UnitType.NOBLE, UnitType.BANNER_THIEF);

    private AutomationPlanning() {}

    /**
     * The index of the plan's current line: the first building line whose level the village doesn't
     * reach with its current and queued levels. The recruit line is skipped, and so are Banner Hall
     * lines in a world without the hall. {@code lines.size()} once every line is reached.
     *
     * @param levels current levels plus queued orders
     */
    public static int currentLine(
            List<BuildPlanLine> lines, Map<BuildingType, Integer> levels, boolean bannerHall) {
        for (int i = 0; i < lines.size(); i++) {
            BuildPlanLine line = lines.get(i);
            if (line.startsRecruiting() || skipped(line, bannerHall)) {
                continue;
            }
            if (levels.get(line.building()) < line.level()) {
                return i;
            }
        }
        return lines.size();
    }

    /** The index of the recruit line, -1 if the plan has none. */
    public static int recruitLine(List<BuildPlanLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsRecruiting()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the village recruits: the plan has a recruit line and every line above it is reached.
     */
    public static boolean recruiting(List<BuildPlanLine> lines, int currentLine) {
        int recruit = recruitLine(lines);
        return recruit >= 0 && currentLine > recruit;
    }

    /**
     * The building lines as the steps of {@code NpcPlanning.nextBuild}: one building each, so the
     * first step not reached is the current line. Banner Hall lines are left out where the hall
     * can't be built.
     */
    public static List<Map<BuildingType, Integer>> steps(
            List<BuildPlanLine> lines, boolean bannerHall) {
        List<Map<BuildingType, Integer>> steps = new ArrayList<>();
        for (BuildPlanLine line : lines) {
            if (!line.startsRecruiting() && !skipped(line, bannerHall)) {
                steps.add(Map.of(line.building(), line.level()));
            }
        }
        return steps;
    }

    private static boolean skipped(BuildPlanLine line, boolean bannerHall) {
        return !bannerHall && line.building() == BuildingType.BANNER_HALL;
    }

    /**
     * Seconds until the stock pays {@code cost} with the production per hour: 0 if it pays now,
     * empty if a missing resource isn't produced. Rounded up, so the stock is there by then.
     */
    public static OptionalLong secondsUntil(
            ResourceAmounts cost, ResourceAmounts stock, Map<ResourceType, Double> perHour) {
        long seconds = 0;
        for (ResourceType type : ResourceType.values()) {
            int missing = cost.get(type) - stock.get(type);
            if (missing <= 0) {
                continue;
            }
            double rate = perHour.getOrDefault(type, 0.0);
            if (rate <= 0) {
                return OptionalLong.empty();
            }
            seconds = Math.max(seconds, (long) Math.ceil(missing * 3600.0 / rate));
        }
        return OptionalLong.of(seconds);
    }

    /**
     * The orders that fill the recruit template now (02-game-rules §5.2b and §25), the server's
     * twin of the recruit page's plan: per unit of the goal, in building and unit order, what the
     * village still lacks ({@code have} = all its own units: at home, away and in its recruit
     * orders); units by hand ({@link #BY_HAND}), locked units and buildings whose queue is busy are
     * skipped, each recruited unit takes a place in its building's queue, the paladin is capped at
     * the free slots; then all of it is cut by one share until the stock and the free population
     * pay. Units not listed in {@code buildings} (switched off in this world) are left out.
     *
     * @param buildings the recruiting buildings as the recruit page lists them
     * @param have the village's own units everywhere, those in recruit orders included
     * @param stock what recruiting may spend
     * @param freePop the free population recruiting may use
     * @param freePaladinSlots the player's free paladin slots
     */
    public static List<RecruitOrderRequest> recruitOrders(
            UnitCounts goal,
            List<RecruitBuildingDto> buildings,
            UnitCounts have,
            ResourceAmounts stock,
            int freePop,
            int recruitQueueSize,
            int freePaladinSlots) {
        Map<UnitType, Integer> missing = new EnumMap<>(UnitType.class);
        Map<UnitType, RecruitBuildingDto.RecruitUnitDto> defs = new EnumMap<>(UnitType.class);
        for (RecruitBuildingDto building : buildings) {
            List<RecruitOrderDto> queue = building.queue();
            int places = queue.size() < RECRUIT_QUEUE_BUSY ? recruitQueueSize - queue.size() : 0;
            for (RecruitBuildingDto.RecruitUnitDto unit : building.units()) {
                UnitType type = unit.unit();
                int open = Math.max(0, goal.get(type) - have.get(type));
                if (type == UnitType.PALADIN) {
                    open = Math.min(open, Math.max(0, freePaladinSlots));
                }
                if (open == 0
                        || BY_HAND.contains(type)
                        || !unit.available()
                        || building.level() == 0
                        || places <= 0) {
                    continue;
                }
                places--;
                missing.put(type, open);
                defs.put(type, unit);
            }
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        long[] cost = new long[ResourceType.values().length];
        long pop = 0;
        for (Map.Entry<UnitType, Integer> entry : missing.entrySet()) {
            RecruitBuildingDto.RecruitUnitDto unit = defs.get(entry.getKey());
            for (ResourceType type : ResourceType.values()) {
                cost[type.ordinal()] += (long) unit.cost().get(type) * entry.getValue();
            }
            pop += (long) unit.pop() * entry.getValue();
        }
        double share = 1;
        for (ResourceType type : ResourceType.values()) {
            if (cost[type.ordinal()] > 0) {
                share =
                        Math.min(
                                share,
                                (double) Math.max(0, stock.get(type)) / cost[type.ordinal()]);
            }
        }
        if (pop > 0) {
            share = Math.min(share, (double) Math.max(0, freePop) / pop);
        }
        List<RecruitOrderRequest> orders = new ArrayList<>();
        for (Map.Entry<UnitType, Integer> entry : missing.entrySet()) {
            int now = (int) Math.min(RecruitRules.MAX_COUNT, Math.floor(entry.getValue() * share));
            if (now > 0) {
                orders.add(new RecruitOrderRequest(entry.getKey(), now));
            }
        }
        return orders;
    }
}
