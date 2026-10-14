package de.bannerfall.combat;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.banner.BannerService;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.Movement;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportKind;
import de.bannerfall.report.ReportService;
import de.bannerfall.unit.UnitCounts;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.OwnerChange;
import de.bannerfall.village.SpawnService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageLocker;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A village changes owner after its loyalty fell to 0 (02-game-rules §10). Called by {@link
 * BattleService} in the battle's transaction, after the loot.
 */
@Service
public class ConquestService {

    private final OwnerChange ownerChange;
    private final UnitStackRepository stacks;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final PlayerRepository players;
    private final ReportService reportService;
    private final ApplicationEventPublisher events;
    private final AchievementService achievements;
    private final BannerService bannerService;

    ConquestService(
            OwnerChange ownerChange,
            UnitStackRepository stacks,
            VillageRepository villages,
            VillageLocker locker,
            PlayerRepository players,
            ReportService reportService,
            ApplicationEventPublisher events,
            AchievementService achievements,
            BannerService bannerService) {
        this.ownerChange = ownerChange;
        this.stacks = stacks;
        this.villages = villages;
        this.locker = locker;
        this.players = players;
        this.reportService = reportService;
        this.events = events;
        this.achievements = achievements;
        this.bannerService = bannerService;
    }

    /**
     * The attacking player takes the village (02-game-rules §10):
     *
     * <ol>
     *   <li>its build and recruit orders are deleted without refund, every unit of the village
     *       outside it is lost (supports elsewhere, movements with their events);
     *   <li>new owner, loyalty 25; a barbarian village gets the default name of a new village; the
     *       banner flying there goes to the conqueror's stash (both reports name it);
     *   <li>one noble is consumed, the other survivors stay as a support stack of their home
     *       village – no RETURN;
     *   <li>village counts and points of both players;
     *   <li>reports CONQUEST (attacker) and VILLAGE_LOST (previous owner, not for barbarians), and
     *       the notifications VILLAGE_CONQUERED and VILLAGE_LOST.
     * </ol>
     *
     * <p>The caller has locked the attacker's home and the village and caught both up to {@code
     * at}. The villages where the conquered village's supports stand are locked here; {@code
     * MovementArriveHandler} already locks them in id order for an attack with nobles, so this
     * normally finds them locked by us.
     *
     * @param survivors the attacking units that survived the battle, at least one noble among them
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void conquer(
            Village village, Movement attack, Map<UnitType, Integer> survivors, Instant at) {
        if (survivors.getOrDefault(UnitType.NOBLE, 0) < 1) {
            throw new IllegalArgumentException("A conquest needs a surviving noble");
        }
        Village from = attack.getHomeVillage();
        VillageRefDto before = VillageRefDto.of(village);
        Player previousOwner = village.getPlayer();
        Long previousOwnerId = previousOwner == null ? null : previousOwner.getId();
        Player conqueror = players.findById(attack.getOwnerPlayerId()).orElseThrow();

        List<Long> supportLocations = stacks.findSupportLocationIds(List.of(village.getId()));
        if (!supportLocations.isEmpty()) {
            locker.lock(supportLocations.toArray(Long[]::new));
        }
        ownerChange.dropOrders(village.getId(), at);
        ownerChange.dropUnits(village, previousOwnerId);

        village.conquer(conqueror, SpawnService.defaultName(conqueror.getName()), at);
        // the banner flying there goes with the village (02-game-rules §21.6.1)
        ConquestReport.ConqueredBanner banner =
                bannerService.villageConquered(village, conqueror, at);
        stayAsSupport(from, village, survivors);

        if (previousOwner != null) {
            previousOwner.loseVillage();
            // the query flushes the new owner first, so the sum no longer has the village
            previousOwner.updatePoints(
                    Math.toIntExact(villages.sumPointsOfPlayer(previousOwner.getId())));
        }
        conqueror.addVillage(village.getPoints());
        conqueror.updatePoints(Math.toIntExact(villages.sumPointsOfPlayer(conqueror.getId())));
        grantAchievements(conqueror, previousOwner);

        ConquestReport report =
                new ConquestReport(before, VillageRefDto.of(from), village.getName(), banner);
        reportService.create(ReportKind.CONQUEST, at, report, conqueror.getId());
        reportService.create(ReportKind.VILLAGE_LOST, at, report, previousOwnerId);
        events.publishEvent(
                new PlayerNotification(
                        conqueror.getId(), new GameNotification.VillageConquered(village.getId())));
        if (previousOwnerId != null) {
            events.publishEvent(
                    new PlayerNotification(
                            previousOwnerId, new GameNotification.VillageLost(village.getId())));
        }
    }

    /**
     * Conqueror, Toppler, Lord Breaker, Fallen and Landlord (02-game-rules §19). Nothing for an NPC
     * lord; a barbarian village has no previous owner. Only a conquest counts as "lost": a village
     * the host turns barbarian goes through another path.
     */
    private void grantAchievements(Player conqueror, Player previousOwner) {
        achievements.grant(conqueror, Achievement.CONQUEROR);
        if (previousOwner != null) {
            achievements.grant(
                    conqueror,
                    previousOwner.isNpc() ? Achievement.LORD_BREAKER : Achievement.TOPPLER);
            achievements.grant(previousOwner, Achievement.FALLEN);
        }
        achievements.checkState(conqueror);
    }

    /** All survivors but one noble join (or create) their home village's stack in the village. */
    private void stayAsSupport(Village home, Village village, Map<UnitType, Integer> survivors) {
        var staying = new EnumMap<UnitType, Integer>(survivors);
        staying.merge(UnitType.NOBLE, -1, Integer::sum);
        UnitCounts units = UnitCounts.of(staying);
        if (units.isEmpty()) {
            return;
        }
        // The query flushes first, so a stack the battle just deleted is really gone.
        UnitStack stack =
                stacks.findByHomeVillageIdAndLocationVillageId(home.getId(), village.getId())
                        .orElseGet(() -> stacks.save(new UnitStack(home, village)));
        units.toMap().forEach(stack.getUnits()::add);
    }
}
