package de.bannerfall.npc;

import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The balance side of NPC lords (09-npc-lords §7, §8): the difficulty in force, the build quiet
 * hours, the rubber band with the production factor it leads to, and the limits towards players:
 * the attack caps, the quiet hours for arrivals and the spare-last-village rule.
 */
@Component
public class NpcBalance {

    private static final Logger log = LoggerFactory.getLogger(NpcBalance.class);

    private final NpcBalanceSettings settings;
    private final WorldService worldService;

    /** The last invalid CUSTOM values warned about, so the log says it once, not every round. */
    private volatile Difficulty warnedCustom;

    NpcBalance(NpcBalanceSettings settings, WorldService worldService) {
        this.settings = settings;
        this.worldService = worldService;
    }

    public NpcBalanceSettings.Balance general() {
        return settings.balance();
    }

    /**
     * The difficulty of the world right now (CUSTOM from the world settings, 09 §8). CUSTOM values
     * that don't pass the validation (the admin API refuses them, but the row can be edited by
     * hand) count as NORMAL, with one WARN: a null time or a factor of 0 would otherwise break
     * every round or stop the lords' production.
     */
    public Difficulty difficulty() {
        WorldSettings world = worldService.settings();
        Difficulty custom = world.npcCustom();
        if (world.npcDifficulty() == NpcDifficulty.CUSTOM && custom != null) {
            List<String> problems = custom.problems("npc_custom");
            if (!problems.isEmpty()) {
                if (!custom.equals(warnedCustom)) {
                    warnedCustom = custom;
                    log.warn("Invalid CUSTOM NPC difficulty, NORMAL is used: {}", problems);
                }
                custom = null;
            }
        }
        return settings.balance().difficulty(world.npcDifficulty(), custom);
    }

    /** Whether the lords may not queue new buildings at {@code now} (09 §5.1). */
    public boolean inBuildQuietHours(Instant now) {
        Difficulty difficulty = difficulty();
        return NpcPlanning.inQuietWindow(
                now,
                difficulty.buildQuietFromTime(),
                difficulty.buildQuietToTime(),
                settings.balance().zone());
    }

    /**
     * Whether a round at {@code now} is a lord's last one before the build quiet hours (09 §5.1,
     * M13c): the window starts before the next round can come ({@code maxDelaySeconds}). Such a
     * round may fill the paid build queue slots.
     */
    public boolean lastRoundBeforeBuildQuietHours(Instant now, long maxDelaySeconds) {
        Difficulty difficulty = difficulty();
        return NpcPlanning.quietWindowStartsWithin(
                now,
                difficulty.buildQuietFromTime(),
                difficulty.buildQuietToTime(),
                settings.balance().zone(),
                maxDelaySeconds);
    }

    /**
     * The rubber band (09 §8). Only when at least one human owns a village; with none (the
     * simulation) it is off. Brake: the lord is above {@code leadMargin} × the strongest human.
     * Boost: below {@code boostBelow} × the humans' average (a {@code boostBelow} of 0 is off).
     *
     * @param humanPoints the points of every human who owns a village
     */
    public static NpcMemory.Stance stance(int lordPoints, List<Integer> humanPoints, Difficulty d) {
        return stance(lordPoints, null, 0, humanPoints, d);
    }

    /**
     * The rubber band with the brake per guild (09 §13.5): a lord in a lord guild is braked also
     * while its guild's points per member are above {@code leadMargin} × those of the strongest
     * human side. Per member since 1.6: compared as sums, a guild of ten lords against two friends
     * in one guild started five times ahead and never conquered.
     *
     * @param guildAverage the points of the lord's guild per member, null without one
     * @param strongestHumanSide the points per member of the strongest human side (a guild of
     *     humans or a human without one), the side with the most per member
     */
    public static NpcMemory.Stance stance(
            int lordPoints,
            Double guildAverage,
            double strongestHumanSide,
            List<Integer> humanPoints,
            Difficulty d) {
        if (humanPoints.isEmpty()) {
            return NpcMemory.Stance.NONE;
        }
        int strongest = humanPoints.stream().mapToInt(Integer::intValue).max().orElse(0);
        double average = humanPoints.stream().mapToInt(Integer::intValue).average().orElse(0);
        boolean braked =
                lordPoints > d.leadMargin() * strongest
                        || (guildAverage != null
                                && guildAverage > d.leadMargin() * strongestHumanSide);
        boolean boosted = d.boostBelow() > 0 && lordPoints < d.boostBelow() * average;
        return new NpcMemory.Stance(boosted, braked);
    }

    /** The production factor of a lord: the difficulty's, times the boost while boosted. */
    public static double productionFactor(Difficulty d, NpcMemory.Stance stance) {
        return d.productionFactor() * (stance.boosted() ? d.boostFactor() : 1.0);
    }

    // ---- the farm budget (09 §6.2, v1.3) ----

    /** The farm budget counts the loot of the last 24 real hours. */
    public static final Duration FARM_BUDGET_WINDOW = Duration.ofHours(24);

    /**
     * The pace window (balancing round 2): within any 2 real hours a lord takes at most its share
     * of the day's budget, so the farm runs spread over the day instead of one burst.
     */
    public static final Duration FARM_PACE_WINDOW = Duration.ofHours(2);

    /**
     * Whether the lord has farmed enough for now (09 §6.2): its loot of the last 24 real hours plus
     * the loot on the way is at least the day's budget, the difficulty's {@code farmLootShare} of
     * what its villages produce in 24 hours; or its loot of the last 2 hours plus the loot on the
     * way is at least the pace window's part of that budget (the budget spread over the hours the
     * lord may farm). A share of 0 is no budget.
     *
     * @param loot the loot of the lord's own ATTACK reports in the last 24 real hours
     * @param recentLoot the same for the last {@link #FARM_PACE_WINDOW}
     * @param underWay what the lord's attacks on the way can carry (an upper bound of their loot)
     * @param productionPerHour the lord's production per real hour, all villages and resources
     *     (world speed and production factor included)
     * @param farmHours the real hours a day the lord may farm: 24, or 24 minus the build quiet
     *     hours with {@code farm-rest}
     */
    public static boolean farmBudgetSpent(
            Difficulty d,
            long loot,
            long recentLoot,
            long underWay,
            double productionPerHour,
            double farmHours) {
        double share = d.farmLootShare();
        if (share <= 0) {
            return false;
        }
        double budget = share * productionPerHour * FARM_BUDGET_WINDOW.toHours();
        double pace = budget * FARM_PACE_WINDOW.toHours() / Math.max(farmHours, 1);
        return loot + underWay >= budget || recentLoot + underWay >= pace;
    }

    /**
     * The real hours a day the lords may farm (09 §6.2): 24, less the build quiet hours with rest.
     */
    public static double farmHours(Difficulty d) {
        if (!d.farmRest()) {
            return 24;
        }
        long quiet = Duration.between(d.buildQuietFromTime(), d.buildQuietToTime()).toMinutes();
        if (quiet < 0) {
            quiet += 24 * 60; // the window passes midnight, 23:00–07:00
        }
        return 24 - quiet / 60.0;
    }

    // ---- limits towards players (§7) ----

    /** The caps count the commands of the last 24 real hours. */
    public static final Duration CAP_WINDOW = Duration.ofHours(24);

    /**
     * How many of {@code sent} went to {@code playerId} within the cap window before {@code now}
     * (an entry exactly 24 hours old no longer counts).
     */
    public static int sentTo(Collection<NpcMemory.SentAttack> sent, long playerId, Instant now) {
        Instant from = now.minus(CAP_WINDOW);
        return (int)
                sent.stream()
                        .filter(a -> a.targetPlayerId() == playerId && a.at().isAfter(from))
                        .count();
    }

    /**
     * Whether {@code commands} more commands towards a player stay within the caps (§7): this
     * lord's to that player, and for a human also all lords' together.
     *
     * @param ownToPlayer this lord's commands to the player in the window
     * @param allLordsToHuman all lords' commands to the human in the window (this lord's included);
     *     ignored for an NPC lord's village
     */
    public static boolean capsAllow(
            Difficulty d, int ownToPlayer, boolean human, int allLordsToHuman, int commands) {
        if (ownToPlayer + commands > d.attacksPerPlayerPerDay()) {
            return false;
        }
        return !human || allLordsToHuman + commands <= d.attacksPerHumanPerDay();
    }

    /**
     * Whether a command arriving at a human village at {@code arriveAt} would land in the quiet
     * hours (§7; only where the difficulty has them).
     */
    public boolean quietArrival(Difficulty d, Instant arriveAt) {
        NpcBalanceSettings.Balance general = settings.balance();
        return d.quietHours()
                && NpcPlanning.inQuietWindow(
                        arriveAt, general.quietFromTime(), general.quietToTime(), general.zone());
    }

    /**
     * Whether nobles may not go to this village (§7): it is a human's last village and the
     * difficulty spares it, or it is another lord's last village (on every difficulty since M12d –
     * no lord is wiped out by the others).
     */
    public static boolean spared(Difficulty d, NpcWorld.MapVillage target) {
        if (target.npc()) {
            return target.ownerVillages() <= 1;
        }
        return d.spareLastVillage() && target.human() && target.ownerVillages() <= 1;
    }
}
