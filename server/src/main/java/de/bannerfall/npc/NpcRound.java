package de.bannerfall.npc;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.ReportRepository;
import de.bannerfall.rules.Production;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.village.VillageStateService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One decision round of an NPC lord (09-npc-lords §3), after the event's own transaction has
 * checked the lord and scheduled the next round. Every step is a short transaction of its own
 * ({@code REQUIRES_NEW}), loading what it needs fresh: reading the reports into memory, in a solo
 * world the orders step (2.1 S3: answer and carry out a human's order, {@link NpcOrders}), then one
 * transaction per village (so a village that fails is rolled back and logged, and the round goes on
 * with the next) – defend, fly the best banners, build and recruit, ship, attack – in a solo world
 * the help step (2.1 S4: ask a human of its guild for help, thank, {@link NpcHelp}), then the
 * finish step, fusing banners (lord banners, 09 §14.3), the allies step (2.1, 12-solo-and-allies
 * §5: answer invitations, leave a human's guild) and the guild step.
 */
@Component
public class NpcRound {

    private static final Logger log = LoggerFactory.getLogger(NpcRound.class);

    private final TransactionTemplate tx;
    private final NpcBrainRepository brains;
    private final PlayerRepository players;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final UnitStackRepository stacks;
    private final NpcTemplates templates;
    private final NpcEconomy economy;
    private final NpcBalance balance;
    private final NpcIntel intel;
    private final NpcDefence defence;
    private final NpcWarfare warfare;
    private final NpcWorld world;
    private final WorldService worldService;
    private final VillageStateService stateService;
    private final ReportRepository reports;
    private final MovementRepository movements;
    private final GameData gameData;
    private final NpcGuilds guilds;
    private final NpcBanners banners;
    private final NpcLessons lessons;
    private final NpcShipments shipments;
    private final NpcAllies allies;
    private final NpcOrders orders;
    private final NpcHelp help;

    NpcRound(
            PlatformTransactionManager transactionManager,
            NpcBrainRepository brains,
            PlayerRepository players,
            VillageRepository villages,
            VillageLocker locker,
            UnitStackRepository stacks,
            NpcTemplates templates,
            NpcEconomy economy,
            NpcBalance balance,
            NpcIntel intel,
            NpcDefence defence,
            NpcWarfare warfare,
            NpcWorld world,
            WorldService worldService,
            VillageStateService stateService,
            ReportRepository reports,
            MovementRepository movements,
            GameData gameData,
            NpcGuilds guilds,
            NpcBanners banners,
            NpcLessons lessons,
            NpcShipments shipments,
            NpcAllies allies,
            NpcOrders orders,
            NpcHelp help) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.brains = brains;
        this.players = players;
        this.villages = villages;
        this.locker = locker;
        this.stacks = stacks;
        this.templates = templates;
        this.economy = economy;
        this.balance = balance;
        this.intel = intel;
        this.defence = defence;
        this.warfare = warfare;
        this.world = world;
        this.worldService = worldService;
        this.stateService = stateService;
        this.reports = reports;
        this.movements = movements;
        this.gameData = gameData;
        this.guilds = guilds;
        this.banners = banners;
        this.lessons = lessons;
        this.shipments = shipments;
        this.allies = allies;
        this.orders = orders;
        this.help = help;
    }

    /**
     * Runs the round of one lord at {@code now} (the clock, 09 §3). Never throws: a step that fails
     * is rolled back and logged, and the round goes on. An exception here would roll back the
     * event's transaction with the next round in it, and the event worker would retry the whole
     * round (villages already committed would act twice) and give the lord up after three tries.
     */
    public void run(long playerId, Instant now) {
        step(playerId, "reading the reports", () -> readReports(playerId, now));
        NpcTurn.View view = null;
        try {
            view = tx.execute(status -> view(playerId, now));
        } catch (RuntimeException e) {
            log.warn("NPC lord {}: looking at the world failed", playerId, e);
        }
        if (view != null && view.world().soloMode()) {
            // the orders step (2.1 S3): before the village turns, so an order answered in this
            // round is carried out in it; the turns then see the order as it left it
            orders.run(view, now);
            NpcTurn.View before = view;
            try {
                view =
                        view.withOrders(
                                tx.execute(
                                        status -> orders.standing(playerId, before.guildMates())));
            } catch (RuntimeException e) {
                log.warn("NPC lord {}: reading its order failed", playerId, e);
            }
        }
        if (view != null) {
            List<Long> villageIds = tx.execute(status -> villages.findIdsByPlayerId(playerId));
            for (long villageId : villageIds) {
                NpcTurn.View seen = view;
                step(
                        playerId,
                        "the turn of village " + villageId,
                        () -> villageTurn(seen, villageId, now));
            }
            if (view.world().soloMode()) {
                // the help step (2.1 S4): after the turns, which know the threats and shortages
                help.run(view, now);
            }
        }
        step(playerId, "the finish step", () -> finish(playerId, now));
        step(playerId, "the fusing step", () -> fuse(playerId));
        step(playerId, "the allies step", () -> allies.run(playerId, now));
        step(playerId, "the guild step", () -> guilds.form(playerId));
    }

    /** One step in a transaction of its own; a failure is rolled back and logged at WARN. */
    private void step(long playerId, String what, Runnable body) {
        long started = System.nanoTime();
        try {
            tx.executeWithoutResult(status -> body.run());
            if (log.isDebugEnabled()) {
                log.debug(
                        "NPC lord {}: {} took {} ms",
                        playerId,
                        what,
                        (System.nanoTime() - started) / 1_000_000);
            }
        } catch (RuntimeException e) {
            log.warn("NPC lord {}: {} failed and was rolled back", playerId, what, e);
        }
    }

    /**
     * §3 step 1: new reports into intel and grudges (§4), old memory pruned. The reports are marked
     * read: nobody else reads them.
     */
    private void readReports(long playerId, Instant now) {
        NpcBrain brain = brains.findById(playerId).orElseThrow();
        WorldSettings settings = worldService.settings();
        double speed = settings.speed();
        NpcMemory.Draft memory = brain.getMemory().draft();
        NpcTemplates.Template template = templates.of(brain);
        intel.read(playerId, memory, speed, balance.general(), template.grudgeFactor());
        Map<Long, NpcWorld.MapVillage> map = world.villages(now);
        Set<Long> mates = guilds.mates(playerId);
        if (!mates.isEmpty() || !memory.grudges.isEmpty()) {
            List<Map<Long, NpcMemory.Grudge>> mateGrudges =
                    brains.findAllById(mates).stream().map(b -> b.getMemory().grudges()).toList();
            List<Coordinates> own =
                    map.values().stream()
                            .filter(v -> v.playerId() != null && v.playerId() == playerId)
                            .map(NpcWorld.MapVillage::coordinates)
                            .toList();
            NpcIntel.shareGrudges(
                    memory,
                    playerId,
                    mates,
                    mateGrudges,
                    own,
                    map,
                    settings.mapSpread().scale(template.conquestRadius()),
                    now,
                    longerGrudges(template, speed));
        }
        Set<Long> answered =
                memory.supported.stream()
                        .map(NpcMemory.Supported::attackId)
                        .collect(Collectors.toSet());
        NpcIntel.prune(memory, playerId, now, speed, map, world.existingMovements(answered));
        NpcMemory updated = memory.build();
        if (!updated.equals(brain.getMemory())) {
            brain.setMemory(updated);
        }
    }

    /**
     * How much longer than its guild mate's a shared grudge lasts with this lord (09 §16.4): the
     * extra {@code (grudge-factor − 1) × grudge-hours}, scaled by the speed; none for the types.
     */
    private Duration longerGrudges(NpcTemplates.Template template, double speed) {
        if (template.grudgeFactor() <= 1) {
            return Duration.ZERO;
        }
        long seconds =
                Math.round(balance.general().grudgeHours() * (template.grudgeFactor() - 1) * 3600);
        return NpcTargeting.scaled(Duration.ofSeconds(seconds), speed);
    }

    /** What the lord sees in this round (§4), loaded once for all its villages. */
    private NpcTurn.View view(long playerId, Instant now) {
        NpcBrain brain = brains.findById(playerId).orElseThrow();
        Player lord = players.findById(playerId).orElseThrow();
        WorldSettings settings = worldService.settings();
        // the other lords' commands to each human in the cap window (a handful of rows)
        Map<Long, Integer> toHumans = new HashMap<>();
        for (NpcBrainRepository.AttacksOnPlayer sent :
                brains.attacksSentByOthersSince(playerId, now.minus(NpcBalance.CAP_WINDOW))) {
            toHumans.put(sent.getTarget(), sent.getAttacks());
        }
        Difficulty difficulty = balance.difficulty();
        Set<Long> mates = guilds.mates(playerId);
        Set<Long> side = new HashSet<>(mates);
        side.add(playerId);
        boolean farmBudgetSpent =
                difficulty.farmLootShare() > 0
                        && NpcBalance.farmBudgetSpent(
                                difficulty,
                                reports.sumLootSince(
                                        playerId, now.minus(NpcBalance.FARM_BUDGET_WINDOW)),
                                reports.sumLootSince(
                                        playerId, now.minus(NpcBalance.FARM_PACE_WINDOW)),
                                carryUnderWay(playerId),
                                productionPerHour(lord, settings),
                                NpcBalance.farmHours(difficulty));
        NpcTemplates.Template template = templates.of(brain);
        return new NpcTurn.View(
                playerId,
                brain.getPersonality(),
                template,
                difficulty,
                balance.general(),
                settings,
                world.villages(now),
                world.incomingAttacks(side),
                toHumans,
                lord.getPoints(),
                brain.getMemory().stance().braked(),
                farmBudgetSpent,
                Set.copyOf(mates),
                mates.isEmpty() ? Set.of() : world.trainTargets(mates),
                settings.npcLearningEnabled()
                        ? lessons.of(brain, mates, lessons.wins(now), now)
                        : NpcLearning.Lesson.NONE,
                template.prefersFalling() ? world.fallingPlayers() : Set.of(),
                settings.soloMode() ? orders.standing(playerId, mates) : NpcOrders.Standing.NONE);
    }

    /**
     * What the lord's attacks on the way can carry (09 §6.2): their loot isn't in a report yet, so
     * the farm budget counts it at most, and a round doesn't send more farms on top of a full
     * budget.
     */
    private long carryUnderWay(long playerId) {
        long carry = 0;
        for (Movement attack : movements.findAttacksUnderWay(playerId)) {
            for (UnitType type : UnitType.values()) {
                carry += (long) attack.getUnits().get(type) * gameData.unit(type).carry();
            }
        }
        return carry;
    }

    /** What the lord's villages produce per real hour, all three resources (09 §6.2). */
    private double productionPerHour(Player lord, WorldSettings settings) {
        double sum = 0;
        for (Village village : villages.findByPlayerIdOrderByIdAsc(lord.getId())) {
            for (BuildingType mine :
                    new BuildingType[] {
                        BuildingType.WOODCUTTER, BuildingType.CLAY_PIT, BuildingType.IRON_MINE
                    }) {
                sum +=
                        Production.perHour(
                                village.getBuildings().get(mine),
                                settings.speed(),
                                lord.getProductionFactor());
            }
        }
        return sum;
    }

    /**
     * §3 step 2: lock the village together with the home villages of the lord's own support stacks
     * in it (the withdraw step sends those home, and {@code returnStack} locks both villages of a
     * stack), bring it forward, then defend, fly the best banners (09 §14.3), build and recruit,
     * ship what would overflow (§5.5), attack.
     */
    private void villageTurn(NpcTurn.View view, long villageId, Instant now) {
        long playerId = view.lordId();
        NpcBrain brain = brains.findById(playerId).orElseThrow();
        List<Long> ids = new ArrayList<>();
        ids.add(villageId);
        ids.addAll(stacks.findOwnSupportHomeIds(villageId, playerId));
        // and the villages where this village's supports stand with others (09 §13.3), so the
        // withdraw step can send them home without locking out of order
        for (UnitStack away : stacks.findByHomeVillageId(villageId)) {
            if (!away.isHome()) {
                ids.add(away.getLocationVillage().getId());
            }
        }
        List<Village> locked = locker.lock(ids.toArray(Long[]::new));
        Village village =
                locked.stream().filter(v -> v.getId() == villageId).findFirst().orElse(null);
        if (village == null
                || village.getPlayer() == null
                || village.getPlayer().getId() != playerId) {
            return; // conquered meanwhile
        }
        Set<Long> lockedIds = new HashSet<>();
        for (Village v : locked) {
            lockedIds.add(v.getId());
        }
        // caught up first: the defence step counts the units at home (recruits finished meanwhile)
        stateService.catchUp(village, now);
        NpcMemory.Draft memory = brain.getMemory().draft();
        NpcTurn turn =
                new NpcTurn(
                        view, village, now, memory, lockedIds, world.ownAttackTargets(playerId));

        defence.run(turn);
        banners.fly(turn);
        turn.wantsNobles = warfare.wantsNobles(turn);
        double bonus = memory.build().anyGrudgeAt(now) ? view.template().grudgeTroopBonus() : 0;
        Integer wallGoal =
                view.world().npcLearningEnabled()
                        ? NpcLearning.wallGoal(memory.lessons, villageId, now)
                        : null;
        NpcMemory.Ledger ledger =
                economy.run(
                        village,
                        playerId,
                        now,
                        view.template(),
                        memory.ledger.getOrDefault(villageId, NpcMemory.Ledger.ZERO),
                        turn.wantsNobles,
                        bonus,
                        view.lesson(),
                        wallGoal);
        memory.ledger.put(villageId, ledger);
        shipments.ship(turn, wallGoal);
        if (view.world().soloMode()) {
            shortage(turn, wallGoal, memory);
        }
        warfare.attack(turn);
        brain.setMemory(memory.build());
    }

    /**
     * 2.1 S4 (12 §9): notes how long the village's stock has been short of its next noble or wall
     * level, so the help step can ask a human of its guild for the resources once it has held for
     * {@code shortage-hours}. Another next step, or enough in stock, ends the wait.
     */
    private void shortage(NpcTurn turn, Integer wallGoal, NpcMemory.Draft memory) {
        NpcEconomy.Goal goal =
                economy.nextGoal(
                        turn.village,
                        turn.lordId(),
                        turn.now,
                        turn.template(),
                        turn.wantsNobles,
                        turn.view.lesson(),
                        wallGoal);
        ResourceAmounts missing =
                goal.asksForHelp()
                        ? NpcHelpRules.shortfall(goal.cost(), turn.village.floorResources())
                        : ResourceAmounts.ZERO;
        long villageId = turn.villageId();
        if (missing.total() == 0) {
            memory.shortages.remove(villageId);
            return;
        }
        NpcMemory.Shortage before = memory.shortages.get(villageId);
        Instant since =
                before != null && before.goal().equals(goal.key()) ? before.since() : turn.now;
        memory.shortages.put(villageId, new NpcMemory.Shortage(goal.key(), since, missing));
    }

    /** Lord banners (09 §14.3): the stash's triples fused, in a step of its own. */
    private void fuse(long playerId) {
        NpcBrain brain = brains.findById(playerId).orElseThrow();
        banners.fuse(playerId, templates.of(brain));
    }

    /**
     * §3 step 3: the lord's last think time, the rubber band and the production factor (§8), after
     * the villages were caught up, so the old factor applied up to now.
     */
    private void finish(long playerId, Instant now) {
        NpcBrain brain = brains.findById(playerId).orElseThrow();
        Player player = players.findById(playerId).orElseThrow();
        Difficulty difficulty = balance.difficulty();
        List<Integer> humanPoints =
                players.findHumansWithVillages().stream().map(Player::getPoints).toList();
        NpcMemory.Stance stance =
                NpcBalance.stance(
                        player.getPoints(),
                        guilds.guildAverage(playerId).orElse(null),
                        guilds.strongestHumanSide(),
                        humanPoints,
                        difficulty);
        double factor = NpcBalance.productionFactor(difficulty, stance);
        if (player.getProductionFactor() != factor) {
            player.setProductionFactor(factor);
        }
        NpcMemory memory =
                brain.getMemory()
                        .withStance(stance)
                        .keepLedgersOf(new HashSet<>(villages.findIdsByPlayerId(playerId)));
        brain.setMemory(memory);
        brain.thoughtAt(now);
    }
}
