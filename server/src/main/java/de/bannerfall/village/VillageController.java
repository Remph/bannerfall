package de.bannerfall.village;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Own villages only: a foreign or unknown id is 404 {@code VILLAGE_NOT_FOUND}. */
@RestController
@RequestMapping("/api/villages")
class VillageController {

    private final VillageService villageService;
    private final PlayerService playerService;

    VillageController(VillageService villageService, PlayerService playerService) {
        this.villageService = villageService;
        this.playerService = playerService;
    }

    @GetMapping("/{id}")
    VillageDto get(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal me) {
        return villageService.get(id, playerService.idOf(me.accountId()));
    }

    @PatchMapping("/{id}")
    VillageDto rename(
            @PathVariable long id,
            @Valid @RequestBody RenameVillageRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return villageService.rename(id, playerService.idOf(me.accountId()), body.name());
    }
}
