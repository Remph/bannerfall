package de.bannerfall.building;

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
class BuildController {

    private final BuildService buildService;
    private final PlayerService playerService;

    BuildController(BuildService buildService, PlayerService playerService) {
        this.buildService = buildService;
        this.playerService = playerService;
    }

    @GetMapping("/buildings")
    List<BuildingInfoDto> buildings(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return buildService.buildings(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/build-orders")
    @ResponseStatus(HttpStatus.CREATED)
    BuildOrderDto enqueue(
            @PathVariable long villageId,
            @Valid @RequestBody BuildOrderRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return buildService.enqueue(villageId, playerService.idOf(me.accountId()), body.building());
    }

    @DeleteMapping("/build-orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(
            @PathVariable long villageId,
            @PathVariable long orderId,
            @AuthenticationPrincipal AccountPrincipal me) {
        buildService.cancel(villageId, orderId, playerService.idOf(me.accountId()));
    }

    /** Answers with the whole queue in its new order. */
    @PostMapping("/build-orders/{orderId}/move")
    List<BuildOrderDto> move(
            @PathVariable long villageId,
            @PathVariable long orderId,
            @Valid @RequestBody BuildOrderMoveRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return buildService.move(
                villageId, orderId, body.position(), playerService.idOf(me.accountId()));
    }
}
