package de.bannerfall.village;

import de.bannerfall.achievement.AchievementService;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.player.Player;
import de.bannerfall.rules.BuildRules;
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
    private final AchievementService achievements;

    PointsService(GameData gameData, VillageRepository villages, AchievementService achievements) {
        this.gameData = gameData;
        this.villages = villages;
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
}
