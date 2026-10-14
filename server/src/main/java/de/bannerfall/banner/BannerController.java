package de.bannerfall.banner;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The banners of the logged-in player (02-game-rules §21, 04-api). Only his own: a foreign or
 * unknown village is 404 {@code VILLAGE_NOT_FOUND}, a foreign banner 404 {@code BANNER_NOT_FOUND}.
 * A village's banner is nobody else's business here (§21.7: only the owner and a scout see it).
 */
@RestController
@RequestMapping("/api")
class BannerController {

    private final BannerService bannerService;
    private final PlayerService playerService;

    BannerController(BannerService bannerService, PlayerService playerService) {
        this.bannerService = bannerService;
        this.playerService = playerService;
    }

    @GetMapping("/banners")
    BannersDto banners(@AuthenticationPrincipal AccountPrincipal me) {
        return bannerService.overview(playerService.idOf(me.accountId()));
    }

    /** Equips (or swaps in) a banner of the stash; answers with the new overview. */
    @PutMapping("/villages/{villageId}/banner/{bannerId}")
    BannersDto equip(
            @PathVariable long villageId,
            @PathVariable long bannerId,
            @AuthenticationPrincipal AccountPrincipal me) {
        return bannerService.equip(villageId, playerService.idOf(me.accountId()), bannerId);
    }

    /** Fuses three stash banners of one tier into one of the next (M32c); the new overview. */
    @PostMapping("/banners/fuse")
    BannersDto fuse(
            @Valid @RequestBody FuseBannersRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return bannerService.fuse(
                playerService.idOf(me.accountId()), body.bannerIds(), body.type());
    }

    @DeleteMapping("/villages/{villageId}/banner")
    BannersDto unequip(@PathVariable long villageId, @AuthenticationPrincipal AccountPrincipal me) {
        return bannerService.unequip(villageId, playerService.idOf(me.accountId()));
    }
}
