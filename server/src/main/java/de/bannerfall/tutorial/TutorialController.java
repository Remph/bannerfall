package de.bannerfall.tutorial;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The player's own tutorial (04-api <i>Tutorial</i>, 02-game-rules §27). */
@RestController
@RequestMapping("/api/tutorial")
class TutorialController {

    private final TutorialService tutorial;
    private final PlayerService playerService;

    TutorialController(TutorialService tutorial, PlayerService playerService) {
        this.tutorial = tutorial;
        this.playerService = playerService;
    }

    /**
     * The tutorial now; records the steps the game shows as done and pays their refunds. A POST
     * because it writes, although the client calls it like a read.
     */
    @PostMapping("/sync")
    TutorialDto sync(@AuthenticationPrincipal AccountPrincipal me) {
        return tutorial.sync(playerService.idOf(me.accountId()));
    }

    @PostMapping("/start")
    TutorialDto start(@AuthenticationPrincipal AccountPrincipal me) {
        return tutorial.start(playerService.idOf(me.accountId()));
    }

    @PostMapping("/stop")
    TutorialDto stop(@AuthenticationPrincipal AccountPrincipal me) {
        return tutorial.stop(playerService.idOf(me.accountId()));
    }

    @PostMapping("/steps/{key}/done")
    TutorialDto done(@PathVariable String key, @AuthenticationPrincipal AccountPrincipal me) {
        return tutorial.done(playerService.idOf(me.accountId()), step(key));
    }

    @PostMapping("/steps/{key}/skip")
    TutorialDto skip(@PathVariable String key, @AuthenticationPrincipal AccountPrincipal me) {
        return tutorial.skip(playerService.idOf(me.accountId()), step(key));
    }

    private static TutorialStep step(String key) {
        try {
            return TutorialStep.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(ErrorCode.TUTORIAL_STEP_NOT_FOUND);
        }
    }
}
