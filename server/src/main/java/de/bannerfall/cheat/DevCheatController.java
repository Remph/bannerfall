package de.bannerfall.cheat;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.player.PlayerService;
import de.bannerfall.village.VillageDto;
import de.bannerfall.village.VillageService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev cheats (04-api). {@code @Profile("dev")}: the class doesn't exist in production, so the paths
 * are plain 404 there. Every cheat answers with the village as it is afterwards.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/dev/villages/{villageId}")
class DevCheatController {

    private final CheatService cheats;
    private final VillageService villageService;
    private final PlayerService playerService;

    DevCheatController(
            CheatService cheats, VillageService villageService, PlayerService playerService) {
        this.cheats = cheats;
        this.villageService = villageService;
        this.playerService = playerService;
    }

    @PostMapping("/resources")
    VillageDto addResources(
            @PathVariable long villageId,
            @Valid @RequestBody AddResourcesRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        cheats.addResources(
                CheatTarget.own(villageId, playerId),
                new ResourceAmounts(body.wood(), body.clay(), body.iron()));
        return villageService.get(villageId, playerId);
    }

    @PostMapping("/units")
    VillageDto addUnits(
            @PathVariable long villageId,
            @Valid @RequestBody AddUnitsRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        cheats.addUnits(CheatTarget.own(villageId, playerId), body.units());
        return villageService.get(villageId, playerId);
    }

    @PostMapping("/finish-movements")
    VillageDto finishMovements(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        cheats.finishMovements(CheatTarget.own(villageId, playerId));
        return villageService.get(villageId, playerId);
    }

    @PostMapping("/finish-orders")
    VillageDto finishOrders(
            @PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        long playerId = playerService.idOf(me.accountId());
        cheats.finishOrders(CheatTarget.own(villageId, playerId));
        return villageService.get(villageId, playerId);
    }
}
