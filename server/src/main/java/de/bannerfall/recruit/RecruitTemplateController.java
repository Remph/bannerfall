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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The player's recruit templates A to E (04-api <i>Recruitment</i>, 1.4). */
@RestController
@RequestMapping("/api/recruit-templates")
class RecruitTemplateController {

    private final RecruitTemplateService templateService;
    private final PlayerService playerService;

    RecruitTemplateController(RecruitTemplateService templateService, PlayerService playerService) {
        this.templateService = templateService;
        this.playerService = playerService;
    }

    @GetMapping
    List<RecruitTemplateDto> list(@AuthenticationPrincipal AccountPrincipal me) {
        return templateService.templates(playerService.idOf(me.accountId()));
    }

    /** Makes the template or changes it. */
    @PutMapping("/{letter}")
    RecruitTemplateDto save(
            @PathVariable RecruitTemplateLetter letter,
            @Valid @RequestBody RecruitTemplateRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return templateService.save(
                playerService.idOf(me.accountId()), letter, body.name(), body.units());
    }

    @DeleteMapping("/{letter}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable RecruitTemplateLetter letter,
            @AuthenticationPrincipal AccountPrincipal me) {
        templateService.delete(playerService.idOf(me.accountId()), letter);
    }
}
