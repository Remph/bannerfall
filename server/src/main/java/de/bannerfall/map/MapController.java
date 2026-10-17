package de.bannerfall.map;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole map is public to logged-in players (the loyalty of other players' villages is not,
 * M21). Invalid parameters are 400 {@code VALIDATION} (Spring's method validation checks the
 * constraint annotations on the parameters).
 */
@RestController
@RequestMapping("/api")
class MapController {

    private final MapQuery mapQuery;
    private final MapMovementsQuery movementsQuery;
    private final PlayerService playerService;

    MapController(
            MapQuery mapQuery, MapMovementsQuery movementsQuery, PlayerService playerService) {
        this.mapQuery = mapQuery;
        this.movementsQuery = movementsQuery;
        this.playerService = playerService;
    }

    /** The villages on the fields {@code x … x+w−1} × {@code y … y+h−1}. */
    @GetMapping("/map")
    List<MapVillageDto> map(
            @RequestParam @Min(0) int x,
            @RequestParam @Min(0) int y,
            @RequestParam @Min(1) @Max(MapQuery.MAX_SIZE) int w,
            @RequestParam @Min(1) @Max(MapQuery.MAX_SIZE) int h,
            @AuthenticationPrincipal AccountPrincipal me) {
        return mapQuery.villagesIn(playerService.idOf(me.accountId()), x, y, w, h);
    }

    /**
     * The villages the player's own movements head to (M26): the markers of the map. Only his own
     * commands and what comes to his own villages, never anything about other players' movements.
     */
    @GetMapping("/map/movements")
    List<MapMovementDto> mapMovements(@AuthenticationPrincipal AccountPrincipal me) {
        return movementsQuery.movementsOf(playerService.idOf(me.accountId()));
    }

    /**
     * The same movements one by one with both ends (v1.3): the lines of the map. The start time of
     * other players' attacks is left out.
     */
    @GetMapping("/map/movement-lines")
    List<MapMovementLineDto> mapMovementLines(@AuthenticationPrincipal AccountPrincipal me) {
        return movementsQuery.linesOf(playerService.idOf(me.accountId()));
    }
}
