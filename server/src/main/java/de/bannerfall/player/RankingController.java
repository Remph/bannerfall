package de.bannerfall.player;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/ranking} and {@code GET /api/players/{id}} (04-api, World &amp; player). */
@RestController
class RankingController {

    private final RankingService ranking;

    RankingController(RankingService ranking) {
        this.ranking = ranking;
    }

    @GetMapping("/api/ranking")
    RankingPageDto ranking(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ranking.ranking(page, size);
    }

    @GetMapping("/api/players/{id}")
    PlayerProfileDto profile(@PathVariable long id) {
        return ranking.profile(id);
    }
}
