package de.bannerfall.automation;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A village's automation (04-api <i>Village automation</i>, 1.6). */
@RestController
@RequestMapping("/api/villages/{villageId}/automation")
class AutomationController {

    private final AutomationService automation;
    private final PlayerService playerService;

    AutomationController(AutomationService automation, PlayerService playerService) {
        this.automation = automation;
        this.playerService = playerService;
    }

    @GetMapping
    VillageAutomationDto status(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return automation.status(villageId, playerService.idOf(me.accountId()));
    }

    /** Picks the plan, switches it on or off; a null plan drops the automation. */
    @PutMapping
    VillageAutomationDto set(
            @PathVariable long villageId,
            @Valid @RequestBody VillageAutomationRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return automation.set(
                villageId, playerService.idOf(me.accountId()), body.plan(), body.enabled());
    }
}
