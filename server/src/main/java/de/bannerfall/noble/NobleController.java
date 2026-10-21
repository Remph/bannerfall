package de.bannerfall.noble;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/noble}: the player's next noble price and why (02-game-rules §5.7, 04-api). */
@RestController
@RequestMapping("/api/noble")
class NobleController {

    private final NobleService nobles;
    private final PlayerService playerService;

    NobleController(NobleService nobles, PlayerService playerService) {
        this.nobles = nobles;
        this.playerService = playerService;
    }

    @GetMapping
    NobleDto get(@AuthenticationPrincipal AccountPrincipal me) {
        return nobles.of(playerService.idOf(me.accountId()));
    }
}
