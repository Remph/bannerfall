package de.bannerfall.farm;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The player's farm templates A to E (04-api <i>Farm helper</i>, 1.4). */
@RestController
@RequestMapping("/api/farm-templates")
class FarmTemplateController {

    private final FarmService farmService;
    private final PlayerService playerService;

    FarmTemplateController(FarmService farmService, PlayerService playerService) {
        this.farmService = farmService;
        this.playerService = playerService;
    }

    @GetMapping
    List<FarmTemplateDto> list(@AuthenticationPrincipal AccountPrincipal me) {
        return farmService.templates(playerService.idOf(me.accountId()));
    }

    /** Makes the template or changes it; the entries that use it change with it. */
    @PutMapping("/{letter}")
    FarmTemplateDto save(
            @PathVariable FarmTemplateLetter letter,
            @Valid @RequestBody FarmTemplateRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return farmService.saveTemplate(playerService.idOf(me.accountId()), letter, body.units());
    }

    /** Deletes the template; its entries keep their units as their own. */
    @DeleteMapping("/{letter}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable FarmTemplateLetter letter, @AuthenticationPrincipal AccountPrincipal me) {
        farmService.deleteTemplate(playerService.idOf(me.accountId()), letter);
    }
}
