package de.bannerfall.movement;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Troops and commands (04-api). Village paths are own villages only (404 {@code
 * VILLAGE_NOT_FOUND}); a movement or stack that isn't the player's is 404 as well.
 */
@RestController
@RequestMapping("/api")
class CommandController {

    private final CommandService commandService;
    private final TroopsService troopsService;
    private final PlayerService playerService;
    private final ReturnWatchService returnWatches;

    CommandController(
            CommandService commandService,
            TroopsService troopsService,
            PlayerService playerService,
            ReturnWatchService returnWatches) {
        this.commandService = commandService;
        this.troopsService = troopsService;
        this.playerService = playerService;
        this.returnWatches = returnWatches;
    }

    @GetMapping("/villages/{villageId}/troops")
    TroopsDto troops(@PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return troopsService.troops(villageId, playerService.idOf(me.accountId()));
    }

    @PostMapping("/villages/{villageId}/commands/preview")
    CommandPreviewDto preview(
            @PathVariable long villageId,
            @Valid @RequestBody CommandRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.preview(villageId, playerService.idOf(me.accountId()), body);
    }

    @PostMapping("/villages/{villageId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    MovementDto send(
            @PathVariable long villageId,
            @Valid @RequestBody CommandRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.send(villageId, playerService.idOf(me.accountId()), body);
    }

    @PostMapping("/villages/{villageId}/commands/train/preview")
    TrainPreviewDto previewTrain(
            @PathVariable long villageId,
            @Valid @RequestBody TrainRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.previewTrain(villageId, playerService.idOf(me.accountId()), body);
    }

    @PostMapping("/villages/{villageId}/commands/train")
    @ResponseStatus(HttpStatus.CREATED)
    List<MovementDto> sendTrain(
            @PathVariable long villageId,
            @Valid @RequestBody TrainRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.sendTrain(villageId, playerService.idOf(me.accountId()), body);
    }

    @PostMapping("/movements/{movementId}/cancel")
    MovementDto cancel(
            @PathVariable long movementId, @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.cancel(movementId, playerService.idOf(me.accountId()));
    }

    /** The bell (1.6): tell the player when the units of the attack or return are home. */
    @PutMapping("/movements/{movementId}/return-push")
    MovementDto returnPush(
            @PathVariable long movementId,
            @Valid @RequestBody ReturnPushRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return returnWatches.setWatched(movementId, playerService.idOf(me.accountId()), body.on());
    }

    @PostMapping("/stacks/{stackId}/return")
    @ResponseStatus(HttpStatus.CREATED)
    MovementDto returnStack(
            @PathVariable long stackId,
            @Valid @RequestBody StackReturnRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return commandService.returnStack(
                stackId, playerService.idOf(me.accountId()), body.units());
    }
}
