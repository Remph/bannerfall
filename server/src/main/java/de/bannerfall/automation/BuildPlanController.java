package de.bannerfall.automation;

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

/** The player's build plans A to E (04-api <i>Village automation</i>, 1.6). */
@RestController
@RequestMapping("/api/build-plans")
class BuildPlanController {

    private final BuildPlanService planService;
    private final PlayerService playerService;

    BuildPlanController(BuildPlanService planService, PlayerService playerService) {
        this.planService = planService;
        this.playerService = playerService;
    }

    @GetMapping
    List<BuildPlanDto> list(@AuthenticationPrincipal AccountPrincipal me) {
        return planService.plans(playerService.idOf(me.accountId()));
    }

    /** What the plan editor needs of each building (start and max level, requirements). */
    @GetMapping("/buildings")
    List<PlanBuildingDto> buildings() {
        return planService.buildings();
    }

    /** Makes the plan or changes it. */
    @PutMapping("/{letter}")
    BuildPlanDto save(
            @PathVariable BuildPlanLetter letter,
            @Valid @RequestBody BuildPlanRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return planService.save(
                playerService.idOf(me.accountId()), letter, body.name(), body.lines());
    }

    @DeleteMapping("/{letter}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable BuildPlanLetter letter, @AuthenticationPrincipal AccountPrincipal me) {
        planService.delete(playerService.idOf(me.accountId()), letter);
    }
}
