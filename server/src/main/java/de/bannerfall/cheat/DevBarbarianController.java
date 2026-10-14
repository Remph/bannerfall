package de.bannerfall.cheat;

import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.village.VillageRefDto;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev cheat {@code POST /api/dev/barbarians} (04-api): a barbarian village on the given field. Like
 * the other cheats it only exists with the profile {@code dev}.
 */
@RestController
@Profile("dev")
class DevBarbarianController {

    private final BarbarianService barbarians;

    DevBarbarianController(BarbarianService barbarians) {
        this.barbarians = barbarians;
    }

    @PostMapping("/api/dev/barbarians")
    @ResponseStatus(HttpStatus.CREATED)
    VillageRefDto spawnBarbarian(@Valid @RequestBody SpawnBarbarianRequest body) {
        return VillageRefDto.of(barbarians.spawnAt(new Coordinates(body.x(), body.y())));
    }
}
