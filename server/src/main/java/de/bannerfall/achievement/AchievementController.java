package de.bannerfall.achievement;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The player's own achievements (04-api <i>Achievements</i>). The profile carries the earned. */
@RestController
@RequestMapping("/api/achievements")
class AchievementController {

    private final AchievementService achievements;
    private final PlayerService playerService;

    AchievementController(AchievementService achievements, PlayerService playerService) {
        this.achievements = achievements;
        this.playerService = playerService;
    }

    @GetMapping
    AchievementsDto mine(@AuthenticationPrincipal AccountPrincipal me) {
        return achievements.mine(playerService.idOf(me.accountId()));
    }
}
