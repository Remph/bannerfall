package de.bannerfall.npc;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcMemory.Grudge;
import de.bannerfall.npc.NpcMemory.Intel;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.Report;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportRepository;
import de.bannerfall.rules.Loot;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reports into memory (09-npc-lords §4): what the lord's attacks and scouts saw becomes intel about
 * the target village, an attack on the lord becomes a grudge. Old memory is pruned here too. The
 * pure parts ({@link #learn}, {@link #prune}) are unit-tested.
 */
@Component
class NpcIntel {

    /** A target is left alone this long (game time, ÷ speed) after two failed scoutings. */
    static final Duration LEAVE_ALONE = Duration.ofDays(1);

    /** The farm cooldown memory is kept this long (game time, ÷ speed). */
    static final Duration FARMED_KEEP = Duration.ofDays(1);

    private final ReportRepository reports;
    private final GameData gameData;

    NpcIntel(ReportRepository reports, GameData gameData) {
        this.reports = reports;
        this.gameData = gameData;
    }

    /**
     * Reads the lord's new reports in id order into {@code memory} and marks them read: nobody else
     * reads them (§4).
     */
    void read(
            long playerId,
            NpcMemory.Draft memory,
            double speed,
            NpcBalanceSettings.Balance general) {
        long newest = reports.maxIdOfPlayer(playerId);
        if (newest <= memory.lastReportId) {
            return;
        }
        for (Report report :
                reports.findByPlayerIdAndIdGreaterThanOrderByIdAsc(playerId, memory.lastReportId)) {
            learn(
                    memory,
                    playerId,
                    report.getKind(),
                    report.getHappenedAt(),
                    report.getPayload() instanceof BattleReport b ? b : null,
                    gameData,
                    speed,
                    general.grudgeHours(),
                    general.learning());
            memory.lastReportId = Math.max(memory.lastReportId, report.getId());
        }
        memory.lastReportId = Math.max(memory.lastReportId, newest);
        reports.markAllRead(playerId);
    }

    /** One report into memory, without the lessons of learning lords (09 §15). */
    static void learn(
            NpcMemory.Draft memory,
            long lordId,
            ReportKind kind,
            Instant at,
            BattleReport battle,
            GameData data,
            double speed,
            double grudgeHours) {
        learn(memory, lordId, kind, at, battle, data, speed, grudgeHours, null);
    }

    /**
     * One report into memory. ATTACK and SCOUT: intel about the attacked village; DEFENSE about an
     * attack on one of the lord's own villages: a grudge against the attacker (scouts included)
     * and, with {@code learning}, the lessons (09 §15.1, §15.4). Other reports teach nothing.
     *
     * @param battle the payload of a battle report, else null
     * @param learning the numbers of learning lords; null = no lessons
     */
    static void learn(
            NpcMemory.Draft memory,
            long lordId,
            ReportKind kind,
            Instant at,
            BattleReport battle,
            GameData data,
            double speed,
            double grudgeHours,
            NpcBalanceSettings.Learning learning) {
        if (battle == null) {
            return;
        }
        countLosses(memory, lordId, kind, battle, data);
        switch (kind) {
            case ATTACK, SCOUT -> {
                long villageId = battle.defender().id();
                Intel old = memory.intel.get(villageId);
                if (old != null && !Objects.equals(old.ownerId(), battle.defender().playerId())) {
                    old = null; // someone else's village now: what was seen is worthless
                }
                memory.intel.put(villageId, intelFrom(old, battle, at, data, speed));
            }
            case DEFENSE -> {
                Long attacker = battle.attacker().playerId();
                if (attacker == null
                        || attacker == lordId
                        || !Objects.equals(battle.defender().playerId(), lordId)) {
                    return; // a support of the lord's fought elsewhere: no attack on the lord
                }
                Instant until =
                        at.plus(
                                NpcTargeting.scaled(
                                        Duration.ofSeconds(Math.round(grudgeHours * 3600)), speed));
                Grudge old = memory.grudges.get(attacker);
                if (old != null && old.until().isAfter(until)) {
                    until = old.until();
                }
                // the scouts that went home are waiting there for the lord's own scouts
                long source = battle.attacker().id();
                int scouts =
                        battle.attackerUnits().getOrDefault(UnitType.SCOUT, 0)
                                - battle.attackerLosses().getOrDefault(UnitType.SCOUT, 0);
                if (old != null && old.sourceVillageId() == source) {
                    scouts = Math.max(scouts, old.scouts());
                }
                // a new attack renews the grudge, and with it the wish to retaliate
                memory.grudges.put(attacker, new Grudge(until, source, false, Math.max(0, scouts)));
                if (learning != null) {
                    memory.lessons = NpcLearning.learn(memory.lessons, at, battle, data, learning);
                }
            }
            default -> {}
        }
    }

    /**
     * The grudges of the lord's guild mates (09 §13.4): the lord takes over every active one it
     * doesn't hold itself, against a player outside its guild, if one of its villages is within
     * {@code radius} (its template's {@code conquest-radius}) of the grudge's source village and
     * that village still belongs to the attacker. The copy is marked shared, so it isn't passed on
     * again; a renewal at the guild mate doesn't renew it. Grudges against guild mates are dropped.
     *
     * @param mateGrudges the grudges of each guild mate
     * @param ownVillages where the lord's villages are
     */
    static void shareGrudges(
            NpcMemory.Draft memory,
            long lordId,
            Set<Long> mates,
            Collection<Map<Long, Grudge>> mateGrudges,
            Collection<Coordinates> ownVillages,
            Map<Long, MapVillage> villages,
            double radius,
            Instant now) {
        memory.grudges.keySet().removeAll(mates);
        for (Map<Long, Grudge> grudges : mateGrudges) {
            grudges.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            e -> {
                                long attacker = e.getKey();
                                Grudge grudge = e.getValue();
                                Grudge own = memory.grudges.get(attacker);
                                if (grudge.shared()
                                        || !grudge.until().isAfter(now)
                                        || attacker == lordId
                                        || mates.contains(attacker)
                                        || (own != null && own.until().isAfter(now))) {
                                    return;
                                }
                                MapVillage source = villages.get(grudge.sourceVillageId());
                                if (source == null
                                        || !Objects.equals(source.playerId(), attacker)
                                        || ownVillages.stream()
                                                .noneMatch(
                                                        v ->
                                                                v.distanceTo(source.coordinates())
                                                                        <= radius)) {
                                    return;
                                }
                                memory.grudges.put(
                                        attacker,
                                        new Grudge(
                                                grudge.until(),
                                                grudge.sourceVillageId(),
                                                false,
                                                grudge.scouts(),
                                                true));
                            });
        }
    }

    /**
     * Units the lord lost come off the troop spending of their home village (09 §5.2, balancing
     * round 2): the ledger counts what the army is worth, not what it once cost, so a village that
     * lost its army has a troop debt and recruits back to its troop share before it builds on.
     * Nobles count as building in the ledger and are left out.
     */
    static void countLosses(
            NpcMemory.Draft memory,
            long lordId,
            ReportKind kind,
            BattleReport battle,
            GameData data) {
        switch (kind) {
            case ATTACK, SCOUT ->
                    lose(memory, battle.attacker().id(), battle.attackerLosses(), data);
            case DEFENSE -> {
                if (battle.defenderSide() == null) {
                    return;
                }
                for (BattleReport.DefenderStack stack : battle.defenderSide().stacks()) {
                    if (Objects.equals(stack.home().playerId(), lordId)) {
                        lose(memory, stack.home().id(), stack.losses(), data);
                    }
                }
            }
            default -> {}
        }
    }

    private static void lose(
            NpcMemory.Draft memory, long villageId, Map<UnitType, Integer> losses, GameData data) {
        NpcMemory.Ledger ledger = memory.ledger.get(villageId);
        if (ledger == null) {
            return; // not (or no longer) one of the lord's villages with a ledger
        }
        double value = 0;
        for (Map.Entry<UnitType, Integer> lost : losses.entrySet()) {
            if (lost.getKey() != UnitType.NOBLE) {
                value += (double) lost.getValue() * data.unit(lost.getKey()).cost().total();
            }
        }
        if (value > 0) {
            memory.ledger.put(
                    villageId,
                    new NpcMemory.Ledger(ledger.build(), Math.max(0, ledger.troops() - value)));
        }
    }

    /** The intel after one of the lord's attacks or scoutings. */
    static Intel intelFrom(Intel old, BattleReport b, Instant at, GameData data, double speed) {
        boolean scoutsOnly = b.outcome() == BattleOutcome.SCOUTS_ONLY;
        Long owner = b.defender().playerId();
        BattleReport.DefenderSide side = b.defenderSide();
        int failures = old == null ? 0 : old.scoutFailures();
        Instant leftAlone = old == null ? null : old.leftAloneUntil();
        if (side == null) {
            // Nobody came back: the lord learns only that it went wrong.
            if (scoutsOnly) {
                failures++;
                if (failures >= NpcTargeting.SCOUT_FAILURES_TO_GIVE_UP) {
                    leftAlone = at.plus(NpcTargeting.scaled(LEAVE_ALONE, speed));
                }
                if (old == null) {
                    return new Intel(
                            at,
                            owner,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            b.outcome(),
                            failures,
                            leftAlone,
                            null);
                }
                return new Intel(
                        old.at(),
                        owner,
                        old.units(),
                        old.wall(),
                        old.buildings(),
                        old.resources(),
                        old.loot(),
                        old.lootFull(),
                        old.outcome(),
                        failures,
                        leftAlone,
                        old.banner(),
                        old.standard());
            }
            return new Intel(
                    at,
                    owner,
                    null,
                    old == null ? null : old.wall(),
                    old == null ? null : old.buildings(),
                    null,
                    ResourceAmounts.ZERO,
                    false,
                    BattleOutcome.DEFENDER_WON,
                    failures,
                    leftAlone,
                    old == null ? null : old.banner(),
                    old == null ? null : old.standard());
        }
        Map<UnitType, Integer> units = NpcTargeting.minus(side.units(), side.losses());
        Map<BuildingType, Integer> buildings = old == null ? null : old.buildings();
        BattleReport.ScoutedBanner banner = old == null ? null : old.banner();
        BattleReport.ScoutedBanner standard = old == null ? null : old.standard();
        ResourceAmounts resources = null;
        BattleReport.ScoutingReport scouting = b.scouting();
        if (scouting != null && scouting.succeeded() && scouting.buildings() != null) {
            buildings = scouting.buildings();
            banner = scouting.banner(); // null: none flies there now
            standard = scouting.standard(); // the paladin standard (1.4), likewise
            ResourceAmounts stock =
                    scouting.resources() == null ? ResourceAmounts.ZERO : scouting.resources();
            resources =
                    new ResourceAmounts(
                            Math.max(0, stock.wood() - b.loot().wood()),
                            Math.max(0, stock.clay() - b.loot().clay()),
                            Math.max(0, stock.iron() - b.loot().iron()));
            failures = 0;
            leftAlone = null;
        }
        if (buildings != null) {
            Map<BuildingType, Integer> copy = new EnumMap<>(BuildingType.class);
            copy.putAll(buildings);
            copy.put(BuildingType.WALL, side.wallAfter());
            buildings = copy;
        }
        ResourceAmounts loot = b.loot();
        boolean lootFull = false;
        if (scoutsOnly && old != null) {
            loot = old.loot();
            lootFull = old.lootFull();
        } else if (loot.total() > 0) {
            Map<UnitType, Integer> survivors =
                    NpcTargeting.minus(b.attackerUnits(), b.attackerLosses());
            lootFull = loot.total() >= Loot.capacity(data, survivors);
        }
        return new Intel(
                at,
                owner,
                units,
                side.wallAfter(),
                buildings,
                resources,
                loot,
                lootFull,
                b.outcome(),
                failures,
                leftAlone,
                banner,
                standard);
    }

    /**
     * Forgets what is too old or no longer true (§4): intel older than 7 days ÷ speed or about a
     * village that changed owner, expired grudges, commands older than the cap window, farm
     * arrivals older than a day ÷ speed, answered attacks that have arrived, and conquest targets
     * that expired, fell to the lord or whose academy village is lost.
     *
     * @param villages the map now
     * @param existingMovements which of the movements in {@code memory.supported} still exist
     */
    static void prune(
            NpcMemory.Draft memory,
            long lordId,
            Instant now,
            double speed,
            Map<Long, MapVillage> villages,
            Set<Long> existingMovements) {
        Instant intelFrom = now.minus(NpcTargeting.scaled(NpcTargeting.INTEL_KEEP, speed));
        memory.intel
                .entrySet()
                .removeIf(
                        e -> {
                            MapVillage v = villages.get(e.getKey());
                            Intel intel = e.getValue();
                            boolean leftAlone =
                                    intel.leftAloneUntil() != null
                                            && intel.leftAloneUntil().isAfter(now);
                            return v == null
                                    || !Objects.equals(v.playerId(), intel.ownerId())
                                    || (intel.at().isBefore(intelFrom) && !leftAlone);
                        });
        memory.grudges.values().removeIf(g -> !g.until().isAfter(now));
        memory.lessons = NpcLearning.prune(memory.lessons, now);
        Instant capFrom = now.minus(NpcBalance.CAP_WINDOW);
        memory.attacksSent.removeIf(a -> !a.at().isAfter(capFrom));
        Instant farmedFrom = now.minus(NpcTargeting.scaled(FARMED_KEEP, speed));
        memory.farmed.values().removeIf(at -> at.isBefore(farmedFrom));
        memory.supported.removeIf(s -> !existingMovements.contains(s.attackId()));
        memory.conquest
                .entrySet()
                .removeIf(
                        e -> {
                            MapVillage academy = villages.get(e.getKey());
                            MapVillage target = villages.get(e.getValue().villageId());
                            return !e.getValue().until().isAfter(now)
                                    || academy == null
                                    || !Objects.equals(academy.playerId(), lordId)
                                    || target == null
                                    || Objects.equals(target.playerId(), lordId);
                        });
    }
}
