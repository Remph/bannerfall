package de.bannerfall.paladin;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/paladin}: the player's paladin slots (02-game-rules §5.5, 04-api). */
@RestController
@RequestMapping("/api/paladin")
class PaladinController {

    private final PaladinService paladins;
    private final PlayerService playerService;

    PaladinController(PaladinService paladins, PlayerService playerService) {
        this.paladins = paladins;
        this.playerService = playerService;
    }

    @GetMapping
    PaladinDto get(@AuthenticationPrincipal AccountPrincipal me) {
        return paladins.of(playerService.idOf(me.accountId()));
    }
}
