package de.bannerfall.research;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Own villages only: a foreign or unknown village is 404 {@code VILLAGE_NOT_FOUND}. */
@RestController
@RequestMapping("/api/villages/{villageId}")
class ResearchController {

    private final ResearchService researchService;
    private final PlayerService playerService;

    ResearchController(ResearchService researchService, PlayerService playerService) {
        this.researchService = researchService;
        this.playerService = playerService;
    }

    @GetMapping("/smithy")
    SmithyDto smithy(@PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return researchService.smithy(villageId, playerService.idOf(me.accountId()));
    }

    @GetMapping("/banner-hall")
    HallTrainingDto bannerHall(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return researchService.hallTraining(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/research-orders")
    @ResponseStatus(HttpStatus.CREATED)
    ResearchOrderDto start(
            @PathVariable long villageId,
            @Valid @RequestBody ResearchOrderRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return researchService.start(villageId, playerService.idOf(me.accountId()), body.unit());
    }

    @DeleteMapping("/research-orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(
            @PathVariable long villageId,
            @PathVariable long orderId,
            @AuthenticationPrincipal AccountPrincipal me) {
        researchService.cancel(villageId, orderId, playerService.idOf(me.accountId()));
    }
}
