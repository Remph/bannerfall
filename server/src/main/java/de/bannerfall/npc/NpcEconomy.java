package de.bannerfall.npc;

import de.bannerfall.building.BuildOrder;
import de.bannerfall.building.BuildService;
import de.bannerfall.gamedata.BuildingDef;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitDef;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.market.ExchangeRequest;
import de.bannerfall.market.MarketExchangeService;
import de.bannerfall.npc.NpcPlanning.Kind;
import de.bannerfall.npc.NpcPlanning.Next;
import de.bannerfall.recruit.RecruitOrder;
import de.bannerfall.recruit.RecruitService;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.Population;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.rules.Storage;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What an NPC lord does in one village each round (09-npc-lords §5): build the template's next step
 * and recruit the unit mix, split by the spending ledger. Everything goes through {@link
 * BuildService} and {@link RecruitService}, every action is checked with the same rules first, so
 * in normal play nothing throws. The decisions themselves are in {@link NpcPlanning}.
 */
@Component
class NpcEconomy {

    /** The troop share never goes above this, whatever bonus is added. */
    static final double MAX_TROOP_SHARE = 0.95;

    private final VillageStateService stateService;
    private final BuildService buildService;
    private final RecruitService recruitService;
    private final UnitService unitService;
    private final GameData gameData;
    private final WorldService worldService;
    private final NpcBalance balance;
    private final MarketExchangeService exchanges;

    NpcEconomy(
            VillageStateService stateService,
            BuildService buildService,
            RecruitService recruitService,
            UnitService unitService,
            GameData gameData,
            WorldService worldService,
            NpcBalance balance,
            MarketExchangeService exchanges) {
        this.stateService = stateService;
        this.buildService = buildService;
        this.recruitService = recruitService;
        this.unitService = unitService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.balance = balance;
        this.exchanges = exchanges;
    }

    /**
     * One village's turn: build and recruit in the order the ledger asks for (§5.2). The caller
     * holds the village's lock ({@code VillageLocker}) in this transaction.
     *
     * @param ledger the village's spending before this round (decayed here)
     * @param wantsNobles the village wants nobles (09 §6.6): with an academy and fewer than the
     *     template's nobles, it saves for them instead of building (§5.1, 5)
     * @param troopShareBonus added to the troop share (a DEFENSIVE lord's grudge, §6.7)
     * @return the ledger after the round
     */
    NpcMemory.Ledger run(
            Village village,
            long playerId,
            Instant now,
            NpcTemplates.Template template,
            NpcMemory.Ledger ledger,
            boolean wantsNobles,
            double troopShareBonus) {
        NpcBalanceSettings.Balance general = balance.general();
        Turn turn = new Turn(village, playerId, now, template, wantsNobles, troopShareBonus);
        turn.build = ledger.build() * general.ledgerDecay();
        turn.troops = ledger.troops() * general.ledgerDecay();

        double behind = NpcPlanning.troopDebt(turn.share, turn.build, turn.troops);
        if (behind > 0 && turn.build > 0) {
            turn.recruit(behind, false);
            turn.buildUp();
        } else {
            turn.buildUp();
            turn.recruit(null, true);
        }
        if (turn.stalled) {
            // Nothing could be bought: no troop debt piles up meanwhile (§5.2).
            turn.troops = Math.max(turn.troops, turn.share / (1 - turn.share) * turn.build);
        }
        return new NpcMemory.Ledger(turn.build, turn.troops);
    }

    /**
     * The state of the village's turn. {@link Snap} is loaded once and again after every action (an
     * enqueue changes the stock, the queues and the population); a turn that buys nothing loads it
     * once.
     */
    private final class Turn {
        final Village village;
        final long playerId;
        final Instant now;
        final NpcTemplates.Template template;
        final WorldSettings world = worldService.settings();
        final NpcTemplates.Stage stage;
        final double share;
        final boolean buildQuiet;

        /**
         * The last round before the build quiet hours: the queue may fill the paid slots up to
         * {@code buildQueueMax} (§5.1, M13c); otherwise it stays within {@code buildQueueSize}.
         */
        final int buildSlots;

        /** The village saves for nobles (§5.1, 5). */
        final boolean nobles;

        double build;
        double troops;
        boolean stalled;

        /** Units ordered this round: one order per type (§5.3). */
        final Set<UnitType> ordered = EnumSet.noneOf(UnitType.class);

        Turn(
                Village village,
                long playerId,
                Instant now,
                NpcTemplates.Template template,
                boolean wantsNobles,
                double troopShareBonus) {
            this.village = village;
            this.playerId = playerId;
            this.now = now;
            this.template = template;
            this.stage = template.stageFor(village.getPoints());
            this.buildQuiet = balance.inBuildQuietHours(now);
            long interval =
                    NpcPlanning.thinkIntervalSeconds(world.npcThinkMinutes(), world.speed());
            long maxDelay =
                    NpcPlanning.maxThinkDelaySeconds(interval, balance.general().thinkJitter());
            this.buildSlots =
                    balance.lastRoundBeforeBuildQuietHours(now, maxDelay)
                            ? world.buildQueueMax()
                            : world.buildQueueSize();
            this.nobles =
                    wantsNobles
                            && village.getBuildings().get(BuildingType.ACADEMY) >= 1
                            && nobleCount() < template.nobles();
            double base = nobles ? balance.general().nobleTroopShare() : stage.troopShare();
            this.share = Math.min(MAX_TROOP_SHARE, base + troopShareBonus);
        }

        /** The nobles the village owns: home, elsewhere, on the way, in orders. */
        int nobleCount() {
            List<RecruitOrder> orders = stateService.catchUp(village, now);
            return unitService.unitsOf(village.getId(), orders).all().get(UnitType.NOBLE);
        }

        private Snap cached;

        Snap snap() {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }

        /** Call after every enqueue: the next {@link #snap()} reloads. */
        void changed() {
            cached = null;
        }

        private Snap load() {
            List<RecruitOrder> orders = stateService.catchUp(village, now);
            List<BuildOrder> queue = buildService.queueOf(village.getId());
            UnitCounts all = unitService.unitsOf(village.getId(), orders).all();
            Map<BuildingType, Integer> current = village.getBuildings().toMap();
            Map<BuildingType, Integer> levels =
                    BuildRules.levelsWithQueued(
                            current, queue.stream().map(BuildOrder::getBuilding).toList());
            int used = buildService.populationUsed(village, queue, all);
            int freePop = Population.free(current.get(BuildingType.FARM), used);
            return new Snap(orders, queue, all, current, levels, freePop, village.floorResources());
        }

        Next next(Snap s) {
            if (nobles) {
                return NpcPlanning.nextNoble(s.levels, s.current, gameData, s.freePop);
            }
            return NpcPlanning.nextBuild(
                    template.build(), s.levels, s.current, gameData, s.freePop);
        }

        /**
         * The next order's cost at the next free queue position, a paid slot's surcharge included
         * (02 §4.3). With the queue full for this round, the order comes later at a normal slot.
         */
        ResourceAmounts buildCost(Snap s, BuildingDef def, int level) {
            int position = s.queue.size() + 1;
            if (position > buildSlots) {
                return BuildRules.cost(def, level);
            }
            return BuildRules.cost(def, level, position, world.buildQueueSize());
        }

        /** §5.1: enqueue steps while the queue has room, the stock pays and the farm has room. */
        void buildUp() {
            while (true) {
                Snap s = snap();
                Next next = next(s);
                if (next.kind() == Kind.NOBLE) {
                    recruitNoble(s);
                    return;
                }
                if (next.kind() != Kind.BUILD || buildQuiet || s.queue.size() >= buildSlots) {
                    return;
                }
                BuildingType type = next.type();
                BuildingDef def = gameData.building(type);
                int level = s.levels.get(type) + 1;
                ResourceAmounts cost = buildCost(s, def, level);
                if (BuildRules.popDelta(def, level) > s.freePop
                        || !BuildRules.missingRequirements(def.requires(), s.levels).isEmpty()) {
                    return;
                }
                if (!cost.fitsInto(s.stock)) {
                    if (exchangeFor(s, cost)) {
                        continue; // paid now: the next pass enqueues it
                    }
                    return;
                }
                buildService.enqueue(village.getId(), playerId, type);
                changed();
                build += cost.total();
            }
        }

        /**
         * §5.1 (5): one noble if the academy's queue has room and the stock and the farm pay it; it
         * counts as building in the ledger. Otherwise the village saves for it.
         */
        void recruitNoble(Snap s) {
            UnitDef def = gameData.unit(UnitType.NOBLE);
            long inQueue = s.orders.stream().filter(o -> o.getBuilding() == def.building()).count();
            if (ordered.contains(UnitType.NOBLE)
                    || inQueue >= world.recruitQueueSize()
                    || !BuildRules.missingRequirements(def.requires(), s.current).isEmpty()
                    || s.freePop < def.pop()) {
                return;
            }
            if (!def.cost().fitsInto(s.stock)) {
                if (!exchangeFor(s, def.cost())) {
                    return;
                }
                s = snap();
            }
            recruitService.enqueue(village.getId(), playerId, UnitType.NOBLE, 1);
            changed();
            ordered.add(UnitType.NOBLE);
            build += def.cost().total();
        }

        /**
         * v1.3 (§5.4): trades with the market itself so that the stock pays {@code cost} now, if
         * the village has a market and today's amounts and its spare resources allow it. Checked
         * with the same rules first, so the trades don't throw.
         *
         * @return whether it traded (the next {@link #snap()} reloads)
         */
        boolean exchangeFor(Snap s, ResourceAmounts cost) {
            if (world.marketExchangeRatio() <= 0 || s.current.get(BuildingType.MARKET) == 0) {
                return false;
            }
            List<NpcPlanning.Exchange> trades =
                    NpcPlanning.exchangesFor(
                            cost,
                            s.stock,
                            Storage.warehouseCapacity(s.current.get(BuildingType.WAREHOUSE)),
                            world.marketExchangeRatio(),
                            exchanges.leftToday(village));
            if (trades.isEmpty()) {
                return false;
            }
            for (NpcPlanning.Exchange trade : trades) {
                exchanges.exchange(
                        village.getId(),
                        playerId,
                        new ExchangeRequest(trade.give(), trade.receive(), trade.amount()));
            }
            changed();
            return true;
        }

        /**
         * §5.3: recruit the unit mix.
         *
         * @param budget the most to spend, in resources (null = no limit)
         * @param surplus only what the next building doesn't need (§5.2)
         */
        void recruit(Double budget, boolean surplus) {
            Snap s = snap();
            Next next = next(s);
            ResourceAmounts reserve = ResourceAmounts.ZERO;
            int nextPop = 0;
            if (next.kind() == Kind.BUILD) {
                BuildingDef def = gameData.building(next.type());
                int level = s.levels.get(next.type()) + 1;
                reserve = surplus ? buildCost(s, def, level) : ResourceAmounts.ZERO;
                nextPop = BuildRules.popDelta(def, level);
            } else if (next.kind() == Kind.NOBLE) {
                UnitDef noble = gameData.unit(UnitType.NOBLE);
                reserve = surplus ? noble.cost() : ResourceAmounts.ZERO;
                nextPop = noble.pop();
            }
            if (surplus && next.kind() != Kind.DONE) {
                int capacity = Storage.warehouseCapacity(s.current.get(BuildingType.WAREHOUSE));
                boolean queueFull = s.queue.size() >= buildSlots;
                boolean near = NpcPlanning.nearCapacity(s.stock, capacity);
                if (!(buildQuiet ? near : queueFull || near)) {
                    return;
                }
            }

            double budgetLeft = budget == null ? Double.POSITIVE_INFINITY : budget;
            int farmCapacity = Population.farmCapacity(s.current.get(BuildingType.FARM));
            int popReserve = NpcPlanning.troopPopReserve(farmCapacity, nextPop);
            Map<UnitType, Integer> mix = recruitableMix(s.current);
            if (mix.isEmpty()) {
                stalled = true;
                return;
            }
            Map<UnitType, Integer> havePop = populationOf(s.all);
            while (true) {
                s = snap();
                Set<UnitType> excluded = EnumSet.copyOf(ordered);
                for (UnitType type : mix.keySet()) {
                    BuildingType building = gameData.unit(type).building();
                    long inQueue =
                            s.orders.stream().filter(o -> o.getBuilding() == building).count();
                    // at least one order (09 §5): with a queue of 1 or 2, "size − 2" would let the
                    // lord
                    // recruit nothing at all
                    if (inQueue >= Math.max(1, world.recruitQueueSize() - 2)) {
                        excluded.add(type);
                    }
                }
                UnitType pick = NpcPlanning.pickUnit(mix, havePop, excluded);
                if (pick == null) {
                    return;
                }
                UnitDef def = gameData.unit(pick);
                int popLeft = s.freePop - popReserve;
                ResourceAmounts stock = NpcPlanning.minusReserve(s.stock, reserve);
                int count =
                        Math.min(
                                NpcPlanning.chunk(farmCapacity, def),
                                RecruitRules.maxAffordable(def, stock, popLeft));
                int unitCost = def.cost().total();
                count = (int) Math.min(count, Math.floor(budgetLeft / unitCost));
                if (count < 1) {
                    if (popLeft < def.pop()) {
                        stalled = true;
                    }
                    return;
                }
                recruitService.enqueue(village.getId(), playerId, pick, count);
                changed();
                ordered.add(pick);
                troops += (double) unitCost * count;
                budgetLeft -= (double) unitCost * count;
                havePop.merge(pick, count * def.pop(), Integer::sum);
            }
        }

        /** The stage's mix limited to the units the village can recruit now, requirements met. */
        Map<UnitType, Integer> recruitableMix(Map<BuildingType, Integer> current) {
            Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
            for (var entry : template.mixOf(stage).entrySet()) {
                UnitDef def = gameData.unit(entry.getKey());
                if (BuildRules.missingRequirements(def.requires(), current).isEmpty()) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }

        Map<UnitType, Integer> populationOf(UnitCounts all) {
            Map<UnitType, Integer> pop = new EnumMap<>(UnitType.class);
            all.forEach((type, count) -> pop.put(type, count * gameData.unit(type).pop()));
            return pop;
        }
    }

    private record Snap(
            List<RecruitOrder> orders,
            List<BuildOrder> queue,
            UnitCounts all,
            Map<BuildingType, Integer> current,
            Map<BuildingType, Integer> levels,
            int freePop,
            ResourceAmounts stock) {}
}
