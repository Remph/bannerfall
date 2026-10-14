package de.bannerfall.recruit;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
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
class RecruitController {

    private final RecruitService recruitService;
    private final DismissService dismissService;
    private final PlayerService playerService;

    RecruitController(
            RecruitService recruitService,
            DismissService dismissService,
            PlayerService playerService) {
        this.recruitService = recruitService;
        this.dismissService = dismissService;
        this.playerService = playerService;
    }

    @GetMapping("/recruitment")
    List<RecruitBuildingDto> recruitment(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return recruitService.recruitment(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/recruit-orders")
    @ResponseStatus(HttpStatus.CREATED)
    RecruitOrderDto enqueue(
            @PathVariable long villageId,
            @Valid @RequestBody RecruitOrderRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return recruitService.enqueue(
                villageId, playerService.idOf(me.accountId()), body.unit(), body.count());
    }

    /** "Recruit all": several orders at once, all or none (02-game-rules §5.2). */
    @PostMapping("/recruit-orders/all")
    @ResponseStatus(HttpStatus.CREATED)
    List<RecruitOrderDto> enqueueAll(
            @PathVariable long villageId,
            @Valid @RequestBody RecruitAllRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return recruitService.enqueueAll(
                villageId, playerService.idOf(me.accountId()), body.orders());
    }

    @DeleteMapping("/recruit-orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(
            @PathVariable long villageId,
            @PathVariable long orderId,
            @AuthenticationPrincipal AccountPrincipal me) {
        recruitService.cancel(villageId, orderId, playerService.idOf(me.accountId()));
    }

    /** Dismissing units from the home stack (02-game-rules §5.2a). */
    @PostMapping("/dismiss-orders")
    @ResponseStatus(HttpStatus.CREATED)
    DismissOrderDto dismiss(
            @PathVariable long villageId,
            @Valid @RequestBody DismissOrderRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return dismissService.enqueue(
                villageId, playerService.idOf(me.accountId()), body.unit(), body.count());
    }

    @DeleteMapping("/dismiss-orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelDismissal(
            @PathVariable long villageId,
            @PathVariable long orderId,
            @AuthenticationPrincipal AccountPrincipal me) {
        dismissService.cancel(villageId, orderId, playerService.idOf(me.accountId()));
    }
}
