package de.bannerfall.victory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VictoryController {

    private final VictoryService victory;

    VictoryController(VictoryService victory) {
        this.victory = victory;
    }

    /** How the world is won and how close the sides are, or how it ended (02-game-rules §22). */
    @GetMapping("/api/world/victory")
    VictoryDto victory() {
        return victory.victory();
    }
}
