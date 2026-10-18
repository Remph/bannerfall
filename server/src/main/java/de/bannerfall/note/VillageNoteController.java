package de.bannerfall.note;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The player's own note on any village (1.4); the map chunks carry it back. */
@RestController
class VillageNoteController {

    private final VillageNoteService notes;
    private final PlayerService playerService;

    VillageNoteController(VillageNoteService notes, PlayerService playerService) {
        this.notes = notes;
        this.playerService = playerService;
    }

    @PutMapping("/api/villages/{id}/note")
    VillageNoteDto save(
            @PathVariable long id,
            @Valid @RequestBody VillageNoteRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return notes.save(playerService.idOf(me.accountId()), id, body.text());
    }
}
