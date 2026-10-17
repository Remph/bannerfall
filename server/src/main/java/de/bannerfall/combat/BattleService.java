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
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.BuildingLevels;
import de.bannerfall.village.LoyaltySightings;
import de.bannerfall.village.PointsService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageBannerDto;
import de.bannerfall.village.VillageRefDto;
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
            BannerBattleService bannerBattles) {
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
        // Banners (02-game-rules §21.2): War, Plunder and Haste came along with the attack when it
        // left (the host's switch still turns them off), Bulwark and Oath are the target's now.
        CommandBanner trip = world.bannersEnabled() ? attack.getBanner() : CommandBanner.NONE;
        BannerEffect targetBanner = villageBanners.effectOf(target);

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
                        targetBanner.bulwark());
        BattleResult result = Combat.fight(gameData, input);

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
        awardAchievements(attack, target, result, wallBefore, loot, at, world);
        BattleReport.LoyaltyReport loyaltySeen = loyaltySeen(attack, target, loyalty, result, at);
        boolean conquered = loyalty != null && loyalty.conquered();
        // before the owner changes: thieves steal from the defender, drops know who lost
        BattleReport.TheftReport theft = steal(attack, target, result, conquered, at);
        Found found = rollDrops(attack, target, defendingUnits, result, at);
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
                                theft),
                        haulCapacity),
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
     * Counted wins and defences, Plunderer and Breach (02-game-rules §19). Called after the battle
     * is decided and the loot taken, before a conquest changes the owner: {@code target} still
     * belongs to the defender. Scouting-only attacks and attacks of fewer than {@value
     * AchievementService#MIN_UNITS_FOR_WIN} units count for nothing; a win or a defence against the
     * same village counts once per {@code 24 h ÷ speed} for the same attacker. NPC lords earn
     * nothing, so their battles leave no trace.
     */
    private void awardAchievements(
            Movement attack,
            Village target,
            BattleResult result,
            int wallBefore,
            ResourceAmounts loot,
            Instant at,
            WorldSettings world) {
        Player attacker = attack.getHomeVillage().getPlayer();
        Player defender = target.getPlayer();
        if (result.mainBattle() == null || attacker == null) {
            return;
        }
        boolean enoughUnits =
                attack.getUnits().toMap().values().stream().mapToInt(Integer::intValue).sum()
                        >= AchievementService.MIN_UNITS_FOR_WIN;
        if (result.attackerWon()) {
            if (!attacker.isNpc()
                    && enoughUnits
                    && achievements.creditBattle(
                            attacker.getId(), target.getId(), "WIN", at, world.speed())) {
                achievements.grant(attacker, Achievement.FIRST_BLOOD);
                achievements.count(attacker, Achievement.VICTOR, 1);
                achievements.count(attacker, Achievement.PLUNDERER, loot.total());
            }
            if (attack.getUnits().get(UnitType.RAM) > 0 && result.wallAfter() < wallBefore) {
                achievements.grant(attacker, Achievement.BREACH);
            }
        } else if (defender != null
                && !defender.isNpc()
                && enoughUnits
                && achievements.creditBattle(
                        attacker.getId(), target.getId(), "DEFENCE", at, world.speed())) {
            achievements.grant(defender, Achievement.SHIELD);
            achievements.count(defender, Achievement.BULWARK, 1);
        }
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
     */
    private BattleReport.TheftReport steal(
            Movement attack, Village target, BattleResult result, boolean conquered, Instant at) {
        int sent = attack.getUnits().get(UnitType.BANNER_THIEF);
        if (sent == 0) {
            return null;
        }
        return bannerBattles.steal(
                target,
                attack.getOwnerPlayerId(),
                sent,
                result.attackerSurvivors().getOrDefault(UnitType.BANNER_THIEF, 0),
                result.attackerWon(),
                conquered,
                target.getBuildings().get(BuildingType.BANNER_HALL),
                at);
    }

    /** The banner a battle dropped, for the attacker or for the village owner (at most one). */
    private record Found(BattleReport.ScoutedBanner attacker, BattleReport.ScoutedBanner defender) {

        static final Found NONE = new Found(null, null);
    }

    /**
     * The winner of the main battle rolls for a banner (02-game-rules §21.7): the attacker, or the
     * owner of the village that held (not his supporters). The size filter looks at the losing
     * side: the attacker's army, or every unit that stood in the village; against barbarians at the
     * attacker's army. Scouting-only attacks roll nothing. The target is still the defender's.
     */
    private Found rollDrops(
            Movement attack,
            Village target,
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
            return new Found(
                    bannerBattles.rollDrop(attacker, loser, loserPop, target, at).orElse(null),
                    null);
        }
        if (defender == null) {
            return Found.NONE;
        }
        return new Found(
                null,
                bannerBattles
                        .rollDrop(
                                defender,
                                BannerBattleService.Loser.player(attack.getOwnerPlayerId()),
                                attackerPop,
                                target,
                                at)
                        .orElse(null));
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
