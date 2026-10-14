package de.bannerfall.farm;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The farm helper (04-api <i>Farm helper</i>). */
@RestController
@RequestMapping("/api/villages/{villageId}/farm-targets")
class FarmController {

    private final FarmService farmService;
    private final PlayerService playerService;

    FarmController(FarmService farmService, PlayerService playerService) {
        this.farmService = farmService;
        this.playerService = playerService;
    }

    @GetMapping
    List<FarmTargetDto> list(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.list(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FarmTargetDto add(
            @PathVariable long villageId,
            @Valid @RequestBody FarmTargetRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.add(villageId, playerService.idOf(me.accountId()), body);
    }

    @PutMapping("/{targetId}")
    FarmTargetDto changeUnits(
            @PathVariable long villageId,
            @PathVariable long targetId,
            @Valid @RequestBody FarmUnitsRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.changeUnits(
                villageId, targetId, playerService.idOf(me.accountId()), body.units());
    }

    @DeleteMapping("/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @PathVariable long villageId,
            @PathVariable long targetId,
            @AuthenticationPrincipal AccountPrincipal me) {
        farmService.remove(villageId, targetId, playerService.idOf(me.accountId()));
    }

    /**
     * "Send all" (M39): the list from the top, one attack per entry, until it ends or the units run
     * out; the answer says what was sent, skipped and where it stopped.
     */
    @PostMapping("/send-all")
    FarmSendAllDto sendAll(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.sendAll(villageId, playerService.idOf(me.accountId()));
    }

    /** One attack with the target's template; the answer is the movement, like POST …/commands. */
    @PostMapping("/{targetId}/send")
    @ResponseStatus(HttpStatus.CREATED)
    MovementDto send(
            @PathVariable long villageId,
            @PathVariable long targetId,
            @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.send(villageId, targetId, playerService.idOf(me.accountId()));
    }
}
