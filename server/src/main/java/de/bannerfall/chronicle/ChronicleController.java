package de.bannerfall.chronicle;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/chronicle} (2.1 I1, 04-api *Chronicle*): the story of the world once it has
 * ended, for every player of it.
 */
@RestController
class ChronicleController {

    private final ChronicleService chronicle;
    private final PlayerService playerService;

    ChronicleController(ChronicleService chronicle, PlayerService playerService) {
        this.chronicle = chronicle;
        this.playerService = playerService;
    }

    /** {@code 409 WORLD_NOT_ENDED} while the world runs. */
    @GetMapping("/api/chronicle")
    ChronicleDto chronicle(@AuthenticationPrincipal AccountPrincipal me) {
        return chronicle.read(playerService.idOf(me.accountId()));
    }
}
