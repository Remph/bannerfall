package de.bannerfall.combat;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.banner.BannerBattleService;
import de.bannerfall.banner.VillageBanners;
import de.bannerfall.building.BuildService;
import de.bannerfall.common.RandomSource;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandBanner;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementKind;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.research.UnitLevelService;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.rules.BattleInput;
import de.bannerfall.rules.BattleResult;
import de.bannerfall.rules.Combat;
import de.bannerfall.rules.Loot;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.rules.Siege;
import de.bannerfall.rules.StandardEffect;
import de.bannerfall.rules.Travel;
import de.bannerfall.statistics.BattleStats;
import de.bannerfall.statistics.StatisticsService;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.LoyaltySightings;
import de.bannerfall.village.PointsService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageBannerDto;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.world.NightBonusService;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An attack arrives and fights (02-game-rules §7–§9, §14): the pure rules ({@link Combat}, {@link
 * Loot}) decide, this service reads their input from the database and writes their result back.
 */
@Service
public class BattleService {

    /** Puts the village's own units first, then the supports in the order they came. */
    private static final Comparator<UnitStack> HOME_FIRST =
            Comparator.comparing((UnitStack stack) -> !stack.isHome())
                    .thenComparing(UnitStack::getId);

    private final UnitStackRepository stacks;
    private final MovementRepository movements;
    private final EventScheduler scheduler;
    private final BuildService buildService;
    private final PointsService pointsService;
    private final ReportService reportService;
    private final ConquestService conquestService;
    private final GameData gameData;
    private final WorldService worldService;
    private final RandomSource random;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AchievementService achievements;
    private final LoyaltySightings sightings;
    private final UnitLevelService unitLevels;
    private final VillageBanners villageBanners;
    private final BannerBattleService bannerBattles;
    private final NightBonusService nightBonus;
    private final StatisticsService statistics;

    BattleService(
            UnitStackRepository stacks,
            MovementRepository movements,
            EventScheduler scheduler,
            BuildService buildService,
            PointsService pointsService,
            ReportService reportService,
            ConquestService conquestService,
            GameData gameData,
            WorldService worldService,
            RandomSource random,
            ApplicationEventPublisher events,
            Clock clock,
            AchievementService achievements,
            LoyaltySightings sightings,
            UnitLevelService unitLevels,
            VillageBanners villageBanners,
            BannerBattleService bannerBattles,
            NightBonusService nightBonus,
            StatisticsService statistics) {
        this.stacks = stacks;
        this.movements = movements;
        this.scheduler = scheduler;
        this.buildService = buildService;
        this.pointsService = pointsService;
        this.reportService = reportService;
        this.conquestService = conquestService;
        this.gameData = gameData;
        this.worldService = worldService;
        this.random = random;
        this.events = events;
        this.clock = clock;
        this.achievements = achievements;
        this.sightings = sightings;
        this.unitLevels = unitLevels;
        this.villageBanners = villageBanners;
        this.bannerBattles = bannerBattles;
        this.nightBonus = nightBonus;
        this.statistics = statistics;
    }

    /**
     * The battle of an attack that arrives at {@code at}, in the order of 02-game-rules §7: every
     * unit standing in the target defends (its own and every support); scouting phase and main
     * battle ({@link Combat#fight}); losses per stack; if the attacker won the wall and the
     * catapult target lose levels, surviving nobles lower the loyalty and at 0 the village is
     * conquered ({@link ConquestService}: the survivors stay there and take no loot), otherwise the
     * survivors take loot and walk home as a RETURN with it; reports for the attacker, the village
     * owner and every supporter; points. VILLAGE_UPDATED (after commit) for the target (to its
     * owner after the battle), the attacker's village (the attack became a return or vanished) and
     * the home village of every support stack that stood in the target.
     *
     * <p>The caller ({@code MovementArriveHandler}) has locked the attacker's home and the target
     * in id order and caught both up to {@code at}, so units recruited by then are home and defend.
     * It deletes the arrived movement afterwards. The supporters' home villages aren't locked:
     * their stacks here only change under the target's lock (arrival, withdraw, send back all lock
     * the location too), and their population is counted from the stacks.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolve(Movement attack, Instant at) {
        if (attack.getKind() != MovementKind.ATTACK) {
            throw new IllegalArgumentException("Movement " + attack.getId() + " is no attack");
        }
        Village home = attack.getHomeVillage();
        Village target = attack.getTargetVillage();
        WorldSettings world = worldService.settings();
        BuildingLevels levels = target.getBuildings();
        // snapshots and the owner before a conquest changes them
        VillageRefDto attackerRef = VillageRefDto.of(home);
        VillageRefDto defenderRef = VillageRefDto.of(target);
        Long defenderPlayerId = playerId(target);

        // Everything as it was when the attackers arrived.
        List<UnitStack> defenders = new ArrayList<>(stacks.findByLocationVillageId(target.getId()));
        defenders.sort(HOME_FIRST);
        List<Map<UnitType, Integer>> defendingUnits =
                defenders.stream().map(stack -> stack.getUnits().toMap()).toList();
        List<VillageRefDto> stackHomes =
                defenders.stream().map(stack -> VillageRefDto.of(stack.getHomeVillage())).toList();
        List<Long> stackOwners =
                defenders.stream().map(stack -> playerId(stack.getHomeVillage())).toList();
        List<Long> supporters =
                defenders.stream()
                        .filter(stack -> !stack.isHome() && !stack.getUnits().isEmpty())
                        .map(stack -> playerId(stack.getHomeVillage()))
                        .toList();
        Set<Village> changed = new LinkedHashSet<>(List.of(target, home));
        defenders.stream()
                .filter(stack -> !stack.isHome())
                .forEach(stack -> changed.add(stack.getHomeVillage()));
        BuildingType catapultTarget = catapultTarget(attack);
        int wallBefore = levels.get(BuildingType.WALL);
        int catapultLevelBefore = catapultTarget == null ? 0 : levels.get(catapultTarget);
        Map<BuildingType, Integer> buildingsBefore = levels.toMap();
        ResourceAmounts stockBefore = target.floorResources();
        BattleReport.ScoutedBanner bannerBefore = scoutedBanner(target, world);
        BattleReport.ScoutedBanner standardBefore =
                villageBanners
                        .equippedStandard(target)
                        .map(b -> new BattleReport.ScoutedBanner(b.getType(), b.getTier()))
                        .orElse(null);
        // Banners (02-game-rules §21.2): War, Plunder and Haste came along with the attack when it
        // left (the host's switch still turns them off), Bulwark and Oath are the target's now.
        CommandBanner trip = world.bannersEnabled() ? attack.getBanner() : CommandBanner.NONE;
        BannerEffect targetBanner = villageBanners.effectOf(target);
        // Paladin standards (1.4, §21.18): the attack's came along like War, each defending stack
        // carries its home village's standard now, if a paladin stands in it
        StandardEffect attackStandard = villageBanners.effectOf(trip.standard());
        List<StandardEffect> defenderStandards =
                defenders.stream()
                        .map(
                                stack ->
                                        villageBanners.standardEffectOf(
                                                stack.getHomeVillage(),
                                                stack.getUnits().get(UnitType.PALADIN) > 0,
                                                at))
                        .toList();

        BattleInput input =
                new BattleInput(
                        attack.getUnits().toMap(),
                        defendingUnits,
                        wallBefore,
                        catapultTarget,
                        catapultLevelBefore,
                        Combat.drawLuck(random, world.luckRange()),
                        // unit levels (M30): each side fights with its owners' levels as they are
                        // at the arrival; a support stack brings its own owner's
                        unitLevels.levelsOf(home.getPlayer(), at),
                        defenders.stream()
                                .map(
                                        stack ->
                                                unitLevels.levelsOf(
                                                        stack.getHomeVillage().getPlayer(), at))
                                .toList(),
                        trip.attack(),
                        targetBanner.bulwark(),
                        attackStandard,
                        defenderStandards,
                        // the night bonus (1.4, §8.1) at the arrival, for an owned village
                        nightBonus.current().factorAt(at, target.getPlayer() != null));
        BattleResult result = Combat.fight(gameData, input);
        // what good scouts see outside the village (§7.1), as it was when they arrived
        Map<UnitType, Integer> away =
                result.scouting() != null && BattleReports.seesAway(result.scouting())
                        ? unitsAway(target)
                        : Map.of();

        applyLosses(defenders, result.defenderLosses());
        ResourceAmounts loot = ResourceAmounts.ZERO;
        Integer haulCapacity = null;
        BattleReport.LoyaltyReport loyalty = null;
        if (result.attackerWon()) {
            // 02-game-rules §9: the wall and the catapult target first (the hiding place may be
            // the target), then the loot.
            boolean damaged = lowerLevel(target, BuildingType.WALL, result.wallAfter());
            if (catapultTarget != null && catapultTarget != BuildingType.WALL) {
                damaged |= lowerLevel(target, catapultTarget, result.catapultTargetLevelAfter());
            }
            if (damaged) {
                pointsService.recalculate(target);
            }
            loyalty =
                    lowerLoyalty(
                            attack, target, result.attackerSurvivors(), targetBanner.oath(), at);
            // A conquering army stays in the village, so it takes no loot: the resources stay
            // there and belong to the conqueror with it.
            if (loyalty == null || !loyalty.conquered()) {
                haulCapacity = Loot.capacity(gameData, result.attackerSurvivors(), trip.carry());
                loot =
                        Loot.distribute(
                                haulCapacity,
                                Loot.availableAbove(
                                        target.floorResources(),
                                        villageBanners.hidingCapacity(target)));
                target.spend(loot);
            }
        }
        awardAchievements(attack, target, input, result, stackOwners, loot, at, world);
        statistics.add(
                BattleStats.of(
                        attack.getOwnerPlayerId(),
                        defenderPlayerId,
                        input.defenderStacks(),
                        stackOwners,
                        result,
                        loot.total(),
                        type -> gameData.unit(type).pop()));
        BattleReport.LoyaltyReport loyaltySeen = loyaltySeen(attack, target, loyalty, result, at);
        boolean conquered = loyalty != null && loyalty.conquered();
        // before the owner changes: thieves steal from the defender, drops know who lost
        BattleReport.TheftReport theft =
                steal(
                        attack,
                        target,
                        result,
                        input.attackerLevels().get(UnitType.BANNER_THIEF),
                        conquered,
                        at);
        Found found = rollDrops(attack, target, defenders, defendingUnits, result, at);
        BattleReport.StandardTheftReport standardTheft =
                stealStandard(attack, target, trip, defenders, input, result, at);
        if (!conquered) {
            sendSurvivorsHome(attack, result.attackerSurvivors(), loot, trip, at, world);
        }

        writeReports(
                attack,
                at,
                new BattleReports.Battle(
                        attackerRef,
                        defenderRef,
                        input,
                        result,
                        stackHomes,
                        stockBefore,
                        buildingsBefore,
                        bannerBefore,
                        loot,
                        loyalty,
                        loyaltySeen,
                        new BattleReports.Banners(
                                trip.attack(),
                                targetBanner.bulwark(),
                                found.attacker(),
                                found.defender(),
                                theft,
                                BattleReports.standardOf(attackStandard),
                                found.attackerStandard(),
                                found.defenderStandard(),
                                standardTheft),
                        haulCapacity,
                        away,
                        standardBefore),
                defenderPlayerId,
                supporters);
        if (conquered) {
            // after the battle reports, so CONQUEST and VILLAGE_LOST are the newest
            conquestService.conquer(target, attack, result.attackerSurvivors(), at);
        }
        for (Village village : changed) {
            PlayerNotification.toOwnerOf(
                    village, new GameNotification.VillageUpdated(village.getId()), events);
        }
    }

    /**
     * The village owner's own units away from {@code village} (02-game-rules §7.1, balancing round
     * 2): its support stacks in other villages and every movement whose home it is (attacks and
     * supports on the way, units walking home).
     */
    private Map<UnitType, Integer> unitsAway(Village village) {
        UnitCounts away = new UnitCounts();
        for (UnitStack stack : stacks.findByHomeVillageId(village.getId())) {
            if (!stack.isHome()) {
                away = away.plus(stack.getUnits());
            }
        }
        for (Movement movement : movements.findByHomeVillageId(village.getId())) {
            away = away.plus(movement.getUnits());
        }
        return away.toMap();
    }

    /**
     * Counted wins and defences, Plunderer and Breach (02-game-rules §19) and the battle
     * achievements of 1.4 (§19.1). Called after the battle is decided and the loot taken, before a
     * conquest changes the owner: {@code target} still belongs to the defender. Scouting-only
     * attacks and attacks of fewer than {@value AchievementService#MIN_UNITS_FOR_WIN} units count
     * for no win or defence; a win or a defence against the same village counts once per {@code 24
     * h ÷ speed} for the same attacker. NPC lords earn nothing, so their battles leave no trace
     * (only a lord's win is remembered for the Avenger of the human he beat). An attack on a
     * village of the attacker's own (possible only if he took it while the attack was on its way)
     * counts for nothing.
     *
     * @param stackOwners the player of each defending stack, in the order of {@code
     *     input.defenderStacks()} (null for barbarians)
     */
    private void awardAchievements(
            Movement attack,
            Village target,
            BattleInput input,
            BattleResult result,
            List<Long> stackOwners,
            ResourceAmounts loot,
            Instant at,
            WorldSettings world) {
        Player attacker = attack.getHomeVillage().getPlayer();
        Player defender = target.getPlayer();
        if (attacker == null || (defender != null && defender.getId().equals(attacker.getId()))) {
            return;
        }
        int sent = total(input.attacker());
        int attackerLost = total(result.attackerLosses());
        killCounters(attacker, defender, result, stackOwners, attackerLost);
        BattleResult.Scouting scouting = result.scouting();
        if (scouting != null && scouting.sent() > 0 && scouting.lost() == scouting.sent()) {
            achievements.grant(defender, Achievement.SPY_CATCHER);
        }
        if (result.mainBattle() == null) {
            return;
        }
        boolean enoughUnits = sent >= AchievementService.MIN_UNITS_FOR_WIN;
        if (result.attackerWon()) {
            if (!attacker.isNpc()
                    && enoughUnits
                    && achievements.creditBattle(
                            attacker.getId(), target.getId(), "WIN", at, world.speed())) {
                achievements.grant(attacker, Achievement.FIRST_BLOOD);
                achievements.count(attacker, Achievement.VICTOR, 1);
                achievements.count(attacker, Achievement.PLUNDERER, loot.total());
                if (achievements.avenges(attacker, defender, at, world.speed())) {
                    achievements.grant(attacker, Achievement.AVENGER);
                }
            }
            if (enoughUnits) {
                achievements.beaten(defender, attacker, at);
            }
            if (attack.getUnits().get(UnitType.RAM) > 0 && result.wallAfter() < input.wallLevel()) {
                achievements.grant(attacker, Achievement.BREACH);
            }
            if (sent >= AchievementService.PYRRHIC_MIN_UNITS && attackerLost * 4 >= sent * 3) {
                achievements.grant(attacker, Achievement.PYRRHIC_VICTORY);
            }
            // aimed at the wall, the surviving rams work first: only what the catapults took
            int beforeCatapults =
                    input.catapultTarget() == BuildingType.WALL
                            ? Siege.wallAfterRams(
                                    input.wallLevel(),
                                    result.attackerSurvivors().getOrDefault(UnitType.RAM, 0))
                            : input.catapultTargetLevel();
            if (input.catapultTarget() != null
                    && beforeCatapults - result.catapultTargetLevelAfter()
                            >= AchievementService.DEMOLISHER_LEVELS) {
                achievements.grant(attacker, Achievement.DEMOLISHER);
            }
            // won with luck, lost without it: A / (1 + luck) is the strength without luck
            BattleResult.MainBattle battle = result.mainBattle();
            if (input.luck() > 0 && battle.attack() / (1 + input.luck()) <= battle.defense()) {
                achievements.grant(attacker, Achievement.LUCKY_STRIKE);
            }
        } else if (defender != null
                && !defender.isNpc()
                && enoughUnits
                && achievements.creditBattle(
                        attacker.getId(), target.getId(), "DEFENCE", at, world.speed())) {
            achievements.grant(defender, Achievement.SHIELD);
            achievements.count(defender, Achievement.BULWARK, 1);
            int defending = input.defenderStacks().stream().mapToInt(BattleService::total).sum();
            if (sent > defending) {
                achievements.grant(defender, Achievement.LAST_STAND);
            }
            // every other player whose support stood there (the village's own stack is first)
            for (int i = 0; i < stackOwners.size(); i++) {
                Long owner = stackOwners.get(i);
                if (owner != null
                        && !owner.equals(defender.getId())
                        && !owner.equals(attacker.getId())
                        && total(input.defenderStacks().get(i)) > 0) {
                    achievements.grant(owner, Achievement.BROTHERS_IN_ARMS);
                }
            }
        }
    }

    /**
     * Slayer and Warden (02-game-rules §19.1): the attacker counts every defending unit that died,
     * except his own support standing there; the village's owner counts every attacking unit that
     * died, scouts included, whoever's units killed them.
     */
    private void killCounters(
            Player attacker,
            Player defender,
            BattleResult result,
            List<Long> stackOwners,
            int attackerLost) {
        int killed = 0;
        for (int i = 0; i < result.defenderLosses().size(); i++) {
            if (!attacker.getId().equals(stackOwners.get(i))) {
                killed += total(result.defenderLosses().get(i));
            }
        }
        if (killed > 0) {
            achievements.count(attacker, Achievement.SLAYER, killed);
        }
        if (attackerLost > 0 && defender != null) {
            achievements.count(defender, Achievement.WARDEN, attackerLost);
        }
    }

    private static int total(Map<UnitType, Integer> units) {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 02-game-rules §10: every surviving noble lowers the loyalty by {@code randomInt(20, 35)},
     * less the village's Oath banner (§21.2), unless the attacker owns the target already. At 0 or
     * below the village is conquered; the loyalty is then set by {@link ConquestService}.
     *
     * @return null without surviving nobles (or against an own village)
     */
    private BattleReport.LoyaltyReport lowerLoyalty(
            Movement attack,
            Village target,
            Map<UnitType, Integer> survivors,
            double oath,
            Instant at) {
        int nobles = survivors.getOrDefault(UnitType.NOBLE, 0);
        Long ownerId = playerId(target);
        if (nobles == 0 || (ownerId != null && ownerId == attack.getOwnerPlayerId())) {
            return null;
        }
        double before = target.getLoyalty();
        double after = Loyalty.afterNobles(before, nobles, random, oath);
        boolean conquered = Loyalty.conquers(after);
        if (!conquered) {
            // keep the anchor: a request may have caught the village up past `at` already
            target.setLoyalty(after, Loyalty.newAnchor(target.getLoyaltyUpdatedAt(), at));
        }
        return new BattleReport.LoyaltyReport(
                Loyalty.shown(before), Loyalty.shown(after), conquered);
    }

    /**
     * What the attacker learns about the village's loyalty (02-game-rules §10): the loyalty after
     * the battle, if at least one of his units came back and the village is not his. The surviving
     * nobles' report ({@code hit}) already says it; without nobles the loyalty is unchanged. A
     * human attacker's last sighting is written for the map; a village that fell is his own now and
     * needs none. Null when he learns nothing.
     */
    private BattleReport.LoyaltyReport loyaltySeen(
            Movement attack,
            Village target,
            BattleReport.LoyaltyReport hit,
            BattleResult result,
            Instant at) {
        Long ownerId = playerId(target);
        if (result.attackerSurvivors().isEmpty()
                || (ownerId != null && ownerId == attack.getOwnerPlayerId())) {
            return null;
        }
        int shown = Loyalty.shown(target.getLoyalty());
        BattleReport.LoyaltyReport seen =
                hit != null ? hit : new BattleReport.LoyaltyReport(shown, shown, false);
        Player attacker = attack.getHomeVillage().getPlayer();
        if (attacker != null && !attacker.isNpc() && !seen.conquered()) {
            sightings.record(attacker.getId(), target.getId(), seen.after(), at);
        }
        return seen;
    }

    /**
     * The movement's catapult target; an attack with catapults always has one (it is set when
     * sending, {@code HEADQUARTERS} by default), an attack without has none.
     */
    private static BuildingType catapultTarget(Movement attack) {
        if (attack.getUnits().get(UnitType.CATAPULT) == 0) {
            return null;
        }
        BuildingType chosen = attack.getCatapultTarget();
        return chosen != null ? chosen : BuildingType.HEADQUARTERS;
    }

    /** Every stack loses its share; a support stack that is empty now is deleted. */
    private void applyLosses(List<UnitStack> defenders, List<Map<UnitType, Integer>> losses) {
        for (int i = 0; i < defenders.size(); i++) {
            UnitStack stack = defenders.get(i);
            UnitCounts units = stack.getUnits();
            losses.get(i).forEach((type, lost) -> units.add(type, -lost));
            if (!stack.isHome() && units.isEmpty()) {
                stacks.delete(stack);
            }
        }
    }

    /**
     * Sets a building to the lower level the battle left; its queued build orders aim lower too.
     *
     * @return true if the level changed
     */
    private boolean lowerLevel(Village village, BuildingType building, int levelAfter) {
        int before = village.getBuildings().get(building);
        if (levelAfter >= before) {
            return false;
        }
        village.getBuildings().set(building, levelAfter);
        buildService.levelsLost(village.getId(), building, before - levelAfter);
        if (building == BuildingType.WALL && village.getPlayer() == null) {
            village.markWallRazed(); // barbarians rebuild it only if the world says so (M29)
        }
        return true;
    }

    /**
     * The survivors walk home at the speed of the slowest survivor and carry the loot; the Haste
     * banner of the trip shortens the way back too (02-game-rules §21.2). Nobody survived: nothing
     * walks.
     */
    private void sendSurvivorsHome(
            Movement attack,
            Map<UnitType, Integer> survivors,
            ResourceAmounts loot,
            CommandBanner trip,
            Instant at,
            WorldSettings world) {
        if (survivors.isEmpty()) {
            return;
        }
        Village home = attack.getHomeVillage();
        Village target = attack.getTargetVillage();
        long seconds =
                Travel.withHaste(
                        Travel.travelSeconds(
                                gameData,
                                world,
                                target.coordinates(),
                                home.coordinates(),
                                survivors),
                        trip.haste());
        Movement back =
                movements.save(
                        Movement.returning(
                                home,
                                target,
                                attack.getOwnerPlayerId(),
                                UnitCounts.of(survivors),
                                loot,
                                trip,
                                at,
                                at.plusSeconds(seconds),
                                // only the row's creation time; the game time is `at`
                                clock.instant()));
        scheduler.schedule(GameEventKind.MOVEMENT_ARRIVE, back.getId(), back.getArriveAt());
    }

    /**
     * The banner thieves of the attack (02-game-rules §21.6.2); null without thieves or with
     * banners switched off. The target is still the defender's.
     *
     * @param thiefLevel the attacker's thief level at the arrival (Hall training, 1.4)
     */
    private BattleReport.TheftReport steal(
            Movement attack,
            Village target,
            BattleResult result,
            int thiefLevel,
            boolean conquered,
            Instant at) {
        int sent = attack.getUnits().get(UnitType.BANNER_THIEF);
        if (sent == 0) {
            return null;
        }
        return bannerBattles.steal(
                target,
                attack.getOwnerPlayerId(),
                sent,
                result.attackerSurvivors().getOrDefault(UnitType.BANNER_THIEF, 0),
                thiefLevel,
                result.attackerWon(),
                conquered,
                target.getBuildings().get(BuildingType.BANNER_HALL),
                at);
    }

    /**
     * The banner and the paladin standard a battle dropped, for the attacker or for the village
     * owner (at most one of each).
     */
    private record Found(
            BattleReport.ScoutedBanner attacker,
            BattleReport.ScoutedBanner defender,
            BattleReport.ScoutedBanner attackerStandard,
            BattleReport.ScoutedBanner defenderStandard) {

        static final Found NONE = new Found(null, null, null, null);
    }

    /**
     * The winner of the main battle rolls for a banner (02-game-rules §21.7): the attacker, or the
     * owner of the village that held (not his supporters). The size filter looks at the losing
     * side: the attacker's army, or every unit that stood in the village; against barbarians at the
     * attacker's army. Scouting-only attacks roll nothing. The target is still the defender's.
     *
     * <p>If the winner's own paladin fought (in the attacking army, or in one of the village
     * owner's stacks in the village), he rolls once more, for a paladin standard (1.4, §21.18).
     */
    private Found rollDrops(
            Movement attack,
            Village target,
            List<UnitStack> defenders,
            List<Map<UnitType, Integer>> defendingUnits,
            BattleResult result,
            Instant at) {
        if (result.mainBattle() == null) {
            return Found.NONE;
        }
        int attackerPop = population(attack.getUnits().toMap());
        Player defender = target.getPlayer();
        if (result.attackerWon()) {
            Player attacker = attack.getHomeVillage().getPlayer();
            BannerBattleService.Loser loser = BannerBattleService.Loser.defenderOf(target);
            int loserPop =
                    defender == null
                            ? attackerPop
                            : defendingUnits.stream().mapToInt(this::population).sum();
            BattleReport.ScoutedBanner banner =
                    bannerBattles.rollDrop(attacker, loser, loserPop, target, at).orElse(null);
            BattleReport.ScoutedBanner standard =
                    attack.getUnits().get(UnitType.PALADIN) > 0
                            ? bannerBattles
                                    .rollStandardDrop(attacker, loser, loserPop, target, at)
                                    .orElse(null)
                            : null;
            return new Found(banner, null, standard, null);
        }
        if (defender == null) {
            return Found.NONE;
        }
        BannerBattleService.Loser loser =
                BannerBattleService.Loser.player(attack.getOwnerPlayerId());
        BattleReport.ScoutedBanner banner =
                bannerBattles.rollDrop(defender, loser, attackerPop, target, at).orElse(null);
        boolean paladinFought = false;
        for (int i = 0; i < defenders.size(); i++) {
            Player owner = defenders.get(i).getHomeVillage().getPlayer();
            paladinFought |=
                    owner != null
                            && owner.getId().equals(defender.getId())
                            && defendingUnits.get(i).getOrDefault(UnitType.PALADIN, 0) > 0;
        }
        BattleReport.ScoutedBanner standard =
                paladinFought
                        ? bannerBattles
                                .rollStandardDrop(defender, loser, attackerPop, target, at)
                                .orElse(null)
                        : null;
        return new Found(null, banner, null, standard);
    }

    /**
     * After a lost attack the village owner's own thieves at home may take the paladin standard the
     * attack carried (1.4, 02-game-rules §21.18); null if there is no roll. The target is still the
     * defender's.
     */
    private BattleReport.StandardTheftReport stealStandard(
            Movement attack,
            Village target,
            CommandBanner trip,
            List<UnitStack> defenders,
            BattleInput input,
            BattleResult result,
            Instant at) {
        if (trip.standard() == null
                || result.mainBattle() == null
                || result.attackerWon()
                || defenders.isEmpty()
                || !defenders.get(0).isHome()) {
            return null;
        }
        // HOME_FIRST: the village's own units are the first stack
        int thieves = input.defenderStacks().get(0).getOrDefault(UnitType.BANNER_THIEF, 0);
        int survivors =
                thieves - result.defenderLosses().get(0).getOrDefault(UnitType.BANNER_THIEF, 0);
        return bannerBattles.stealStandard(
                trip.standard(),
                attack.getOwnerPlayerId(),
                attack.getHomeVillage().getId(),
                target,
                survivors,
                input.defenderLevels().get(0).get(UnitType.BANNER_THIEF),
                at);
    }

    /** The population of these units (02-game-rules §5.3). */
    private int population(Map<UnitType, Integer> units) {
        int pop = 0;
        for (var entry : units.entrySet()) {
            pop += entry.getValue() * gameData.unit(entry.getKey()).pop();
        }
        return pop;
    }

    /**
     * The attacker gets ATTACK (or SCOUT), the village owner DEFENSE (barbarians get nothing).
     * Supporters get DEFENSE too when there was a main battle: their units fought. Against scouts
     * only nothing happens to them, so they aren't bothered. A player gets each kind once, even
     * with several stacks in the village.
     *
     * @param defenderPlayerId the target's owner before the battle (null: barbarians)
     * @param supporters the owners of the support stacks in the target before the battle
     */
    private void writeReports(
            Movement attack,
            Instant at,
            BattleReports.Battle battle,
            Long defenderPlayerId,
            List<Long> supporters) {
        BattleResult result = battle.result();
        reportService.create(
                BattleReports.attackerKind(result),
                at,
                BattleReports.forAttacker(battle),
                attack.getOwnerPlayerId());

        reportService.create(
                ReportKind.DEFENSE, at, BattleReports.forDefenders(battle), defenderPlayerId);
        if (result.mainBattle() != null) {
            // their own copy: the banner the owner found or lost is his business (M32b)
            Long[] others =
                    supporters.stream()
                            .filter(id -> id != null && !id.equals(defenderPlayerId))
                            .toArray(Long[]::new);
            if (others.length > 0) {
                reportService.create(
                        ReportKind.DEFENSE, at, BattleReports.forSupporters(battle), others);
            }
        }
    }

    /**
     * The banner a successful scout learns about (02-game-rules §21.7): its type and tier, also a
     * dormant one. None in a world with banners off.
     */
    private BattleReport.ScoutedBanner scoutedBanner(Village target, WorldSettings world) {
        if (!world.bannersEnabled()) {
            return null;
        }
        VillageBannerDto flying = villageBanners.flying(target);
        return flying == null ? null : new BattleReport.ScoutedBanner(flying.type(), flying.tier());
    }

    /** The owner's id, null for a barbarian village. */
    private static Long playerId(Village village) {
        Player owner = village.getPlayer();
        return owner == null ? null : owner.getId();
    }
}
