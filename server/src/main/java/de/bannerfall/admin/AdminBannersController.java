package de.bannerfall.admin;

import de.bannerfall.banner.BannerDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The host's banner operations (M32, 04-api *Admin*, 02-game-rules §21.9). */
@RestController
@RequestMapping("/api/admin")
class AdminBannersController {

    private final AdminBannerService adminBanners;

    AdminBannersController(AdminBannerService adminBanners) {
        this.adminBanners = adminBanners;
    }

    @GetMapping("/players/{id}/banners")
    List<BannerDto> banners(@PathVariable long id) {
        return adminBanners.list(id);
    }

    @PostMapping("/players/{id}/banners")
    @ResponseStatus(HttpStatus.CREATED)
    BannerDto grant(@PathVariable long id, @Valid @RequestBody BannerGrantRequest body) {
        return adminBanners.grant(id, body.type(), body.tier(), body.reason());
    }

    /** The body may be missing. */
    @PostMapping("/banners/{bannerId}/revoke")
    BannerDto revoke(
            @PathVariable long bannerId,
            @Valid @RequestBody(required = false) BannerRevokeRequest body) {
        return adminBanners.revoke(bannerId, body == null ? null : body.reason());
    }
}
