package de.bannerfall.village;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The player's own tag on one of his villages (1.7); {@code /api/me} carries it back. */
@RestController
class VillageTagController {

    private final VillageTagService tags;
    private final PlayerService playerService;

    VillageTagController(VillageTagService tags, PlayerService playerService) {
        this.tags = tags;
        this.playerService = playerService;
    }

    @PutMapping("/api/villages/{id}/tag")
    VillageTagDto save(
            @PathVariable long id,
            @Valid @RequestBody VillageTagRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return tags.save(playerService.idOf(me.accountId()), id, body.tag());
    }
}
