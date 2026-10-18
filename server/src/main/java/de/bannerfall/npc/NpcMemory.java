package de.bannerfall.npc;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an NPC lord keeps between rounds ({@code npc_brain.memory}, 09-npc-lords §4). Immutable: a
 * step takes a {@link Draft}, changes it and saves the result. Memory written by an older version
 * stays readable: missing parts are empty ({@link NpcMemoryConverter}).
 *
 * @param lastReportId the last report the lord has read (0 = none)
 * @param ledger decayed spending per village id (09 §5.2)
 * @param stance the last rubber-band result (09 §8)
 * @param intel what the lord knows about foreign villages, per village id (09 §4)
 * @param grudges per attacking player id (09 §6.7)
 * @param attacksSent every command towards a player village in the last 24 real hours (09 §7)
 * @param farmed per village id: arrival of the lord's last farm or attack there (09 §6.2)
 * @param supported the incoming attacks a village of the lord already answered (09 §6.5)
 * @param conquest per academy village id: its current conquest target (09 §6.6)
 */
public record NpcMemory(
        long lastReportId,
        Map<Long, Ledger> ledger,
        Stance stance,
        Map<Long, Intel> intel,
        Map<Long, Grudge> grudges,
        List<SentAttack> attacksSent,
        Map<Long, Instant> farmed,
        List<Supported> supported,
        Map<Long, ConquestTarget> conquest) {

    public NpcMemory {
        ledger = ledger == null ? Map.of() : Map.copyOf(ledger);
        stance = stance == null ? Stance.NONE : stance;
        intel = intel == null ? Map.of() : Map.copyOf(intel);
        grudges = grudges == null ? Map.of() : Map.copyOf(grudges);
        attacksSent = attacksSent == null ? List.of() : List.copyOf(attacksSent);
        farmed = farmed == null ? Map.of() : Map.copyOf(farmed);
        supported = supported == null ? List.of() : List.copyOf(supported);
        conquest = conquest == null ? Map.of() : Map.copyOf(conquest);
    }

    public static NpcMemory empty() {
        return new NpcMemory(0, null, null, null, null, null, null, null, null);
    }

    /** Spending on buildings (and nobles) and on troops, in resources, decayed every round. */
    public record Ledger(double build, double troops) {
        public static final Ledger ZERO = new Ledger(0, 0);

        public Ledger decayed(double factor) {
            return new Ledger(build * factor, troops * factor);
        }
    }

    /** The last rubber-band result (09 §8): for the admin list and the nobles (§6.6). */
    public record Stance(boolean boosted, boolean braked) {
        public static final Stance NONE = new Stance(false, false);
    }

    /**
     * What the lord's reports told about one foreign village (09 §4).
     *
     * @param at the time of the battle or scouting the latest part comes from
     * @param ownerId the village's owner at that time; null for a barbarian village
     * @param units the defending units seen (all stacks summed, after the battle); null if the lord
     *     has never seen them
     * @param wall the wall level after the battle; null if never seen
     * @param buildings the levels scouted; null if never scouted
     * @param resources the stock scouted minus the loot taken then; null if the last visit didn't
     *     scout it
     * @param loot what the last visit took (zero if none)
     * @param lootFull the last loot filled the carry capacity of the survivors
     * @param outcome the outcome of the last visit
     * @param scoutFailures failed scoutings in a row (all scouts died)
     * @param leftAloneUntil after two failed scoutings the lord leaves the village alone until then
     * @param banner the banner the last successful scouting saw flying there (M32c, 02-game-rules
     *     §21.11); null if none flew there or the village was never scouted. Kept like {@code
     *     buildings} until the next successful scouting
     * @param standard the paladin standard the last successful scouting saw equipped there (1.4,
     *     §21.18); kept like {@code banner}
     */
    public record Intel(
            Instant at,
            Long ownerId,
            Map<UnitType, Integer> units,
            Integer wall,
            Map<BuildingType, Integer> buildings,
            ResourceAmounts resources,
            ResourceAmounts loot,
            boolean lootFull,
            BattleOutcome outcome,
            int scoutFailures,
            Instant leftAloneUntil,
            BattleReport.ScoutedBanner banner,
            BattleReport.ScoutedBanner standard) {

        /** Intel without a paladin standard (none seen, or from before 1.4). */
        public Intel(
                Instant at,
                Long ownerId,
                Map<UnitType, Integer> units,
                Integer wall,
                Map<BuildingType, Integer> buildings,
                ResourceAmounts resources,
                ResourceAmounts loot,
                boolean lootFull,
                BattleOutcome outcome,
                int scoutFailures,
                Instant leftAloneUntil,
                BattleReport.ScoutedBanner banner) {
            this(
                    at,
                    ownerId,
                    units,
                    wall,
                    buildings,
                    resources,
                    loot,
                    lootFull,
                    outcome,
                    scoutFailures,
                    leftAloneUntil,
                    banner,
                    null);
        }

        public Intel {
            units = units == null ? null : Map.copyOf(units);
            buildings = buildings == null ? null : Map.copyOf(buildings);
            loot = loot == null ? ResourceAmounts.ZERO : loot;
        }

        /** Whether defending units were seen there (null units: unknown, not "none"). */
        public boolean showedDefenders() {
            return units != null && units.values().stream().anyMatch(c -> c > 0);
        }

        /** The same intel with another wall level. */
        public Intel withWall(Integer level) {
            return new Intel(
                    at,
                    ownerId,
                    units,
                    level,
                    buildings,
                    resources,
                    loot,
                    lootFull,
                    outcome,
                    scoutFailures,
                    leftAloneUntil,
                    banner,
                    standard);
        }

        /** The number of scouts seen there (0 if none or unknown). */
        public int scoutsSeen() {
            return units == null ? 0 : units.getOrDefault(UnitType.SCOUT, 0);
        }
    }

    /**
     * A grudge against a player who attacked the lord (09 §6.7).
     *
     * @param until when it expires
     * @param sourceVillageId the village the latest attack came from
     * @param retaliated the lord has retaliated (or given up on it) for this grudge
     * @param scouts the most scouts the attacks from {@code sourceVillageId} brought home (sent
     *     minus lost): at least that many stand there, so the lord's own scouting has to beat them
     *     (09 §6.3). 0 in memory written before this was kept
     * @param shared taken over from a guild mate (09 §13.4): not passed on again; false in memory
     *     written before lord guilds
     */
    public record Grudge(
            Instant until, long sourceVillageId, boolean retaliated, int scouts, boolean shared) {

        /** The lord's own grudge (an attack on itself). */
        public Grudge(Instant until, long sourceVillageId, boolean retaliated, int scouts) {
            this(until, sourceVillageId, retaliated, scouts, false);
        }
    }

    /** A command towards a player village (a conquest train counts once). */
    public record SentAttack(long targetPlayerId, Instant at) {}

    /** A village of the lord ({@code fromVillageId}) answered the incoming attack with support. */
    public record Supported(long attackId, long fromVillageId) {}

    /** An academy village's conquest target, kept until {@code until}. */
    public record ConquestTarget(long villageId, Instant until) {}

    /** The ledger of a village (zero if it has none yet). */
    public Ledger ledgerOf(long villageId) {
        return ledger.getOrDefault(villageId, Ledger.ZERO);
    }

    public NpcMemory withLastReportId(long id) {
        Draft draft = draft();
        draft.lastReportId = id;
        return draft.build();
    }

    public NpcMemory withStance(Stance newStance) {
        Draft draft = draft();
        draft.stance = newStance;
        return draft.build();
    }

    public NpcMemory withLedger(long villageId, Ledger entry) {
        Draft draft = draft();
        draft.ledger.put(villageId, entry);
        return draft.build();
    }

    /** Forgets the ledgers and conquest targets of villages the lord no longer owns. */
    public NpcMemory keepLedgersOf(Set<Long> villageIds) {
        Draft draft = draft();
        draft.ledger.keySet().retainAll(villageIds);
        draft.conquest.keySet().retainAll(villageIds);
        return draft.build();
    }

    /** Whether the lord holds a grudge against anyone at {@code now}. */
    public boolean anyGrudgeAt(Instant now) {
        return grudges.values().stream().anyMatch(g -> g.until().isAfter(now));
    }

    /** A mutable copy to change during a step. */
    public Draft draft() {
        return new Draft(this);
    }

    /** The memory while a step changes it; {@link #build()} makes the new record. */
    public static final class Draft {
        public long lastReportId;
        public final Map<Long, Ledger> ledger;
        public Stance stance;
        public final Map<Long, Intel> intel;
        public final Map<Long, Grudge> grudges;
        public final List<SentAttack> attacksSent;
        public final Map<Long, Instant> farmed;
        public final List<Supported> supported;
        public final Map<Long, ConquestTarget> conquest;

        private Draft(NpcMemory m) {
            lastReportId = m.lastReportId;
            ledger = new HashMap<>(m.ledger);
            stance = m.stance;
            intel = new HashMap<>(m.intel);
            grudges = new HashMap<>(m.grudges);
            attacksSent = new ArrayList<>(m.attacksSent);
            farmed = new HashMap<>(m.farmed);
            supported = new ArrayList<>(m.supported);
            conquest = new HashMap<>(m.conquest);
        }

        public NpcMemory build() {
            return new NpcMemory(
                    lastReportId,
                    ledger,
                    stance,
                    intel,
                    grudges,
                    attacksSent,
                    farmed,
                    supported,
                    conquest);
        }
    }
}
