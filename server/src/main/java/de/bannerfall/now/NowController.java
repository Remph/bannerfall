package de.bannerfall.now;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What is running in the player's villages (04-api <i>Now</i>). */
@RestController
@RequestMapping("/api")
class NowController {

    private final NowService nowService;
    private final PlayerService playerService;

    NowController(NowService nowService, PlayerService playerService) {
        this.nowService = nowService;
        this.playerService = playerService;
    }

    @GetMapping("/now")
    NowDto now(@AuthenticationPrincipal AccountPrincipal me) {
        return nowService.now(playerService.idOf(me.accountId()));
    }
}
