package de.bannerfall.village;

import de.bannerfall.achievement.AchievementService;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.player.Player;
import de.bannerfall.rules.BuildRules;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Village and player points (02-game-rules §4.2): a village is worth the points of its buildings, a
 * player the sum over his villages.
 */
@Service
public class PointsService {

    private final GameData gameData;
    private final VillageRepository villages;
    private final VillageLocker locker;
    private final AchievementService achievements;

    PointsService(
            GameData gameData,
            VillageRepository villages,
            VillageLocker locker,
            AchievementService achievements) {
        this.gameData = gameData;
        this.villages = villages;
        this.locker = locker;
        this.achievements = achievements;
    }

    /**
     * Recomputes the points of a village from its building levels, then the points of its owner as
     * the sum over all his villages (so they can't drift apart). Barbarian villages have no owner.
     * Call it on a <b>locked</b> village after its levels changed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(Village village) {
        village.setPoints(BuildRules.villagePoints(gameData, village.getBuildings().toMap()));
        Player owner = village.getPlayer();
        if (owner != null) {
            // The query makes Hibernate write the new village points first, so the sum has them.
            owner.updatePoints(Math.toIntExact(villages.sumPointsOfPlayer(owner.getId())));
            achievements.checkState(owner); // Growth (02-game-rules §19)
        }
    }

    /**
     * The villages whose stored points differ from what their building levels are worth, in id
     * order. Empty in a world that was always played with today's numbers; after a change of the
     * points formula or of a points base (1.2.3: growth 1.2 → 1.15) it is every developed village.
     */
    @Transactional(readOnly = true)
    public List<Long> findStale() {
        return villages.findAll().stream()
                .filter(
                        v ->
                                v.getPoints()
                                        != BuildRules.villagePoints(
                                                gameData, v.getBuildings().toMap()))
                .map(Village::getId)
                .sorted()
                .toList();
    }

    /**
     * Locks the villages and {@link #recalculate recalculates} each one, its owner's points with
     * it. One transaction; the caller keeps the batches small.
     */
    @Transactional
    public void recalculateAll(Collection<Long> ids) {
        for (Village village : locker.lock(ids.toArray(Long[]::new))) {
            recalculate(village);
        }
    }
}
