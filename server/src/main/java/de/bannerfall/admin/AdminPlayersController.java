package de.bannerfall.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The host's player operations (04-api *Admin*, 08-host-console §2). */
@RestController
@RequestMapping("/api/admin/players")
class AdminPlayersController {

    private final AdminPlayerService adminPlayers;

    AdminPlayersController(AdminPlayerService adminPlayers) {
        this.adminPlayers = adminPlayers;
    }

    @GetMapping
    List<AdminPlayerDto> players() {
        return adminPlayers.list();
    }

    @PostMapping("/{id}/role")
    AdminPlayerDto changeRole(@PathVariable long id, @Valid @RequestBody RoleChangeRequest body) {
        return adminPlayers.changeRole(id, body.role());
    }

    /** The body may be missing: the server then generates the password. */
    @PostMapping("/{id}/reset-password")
    PasswordResetDto resetPassword(
            @PathVariable long id,
            @Valid @RequestBody(required = false) PasswordResetRequest body) {
        return adminPlayers.resetPassword(id, body == null ? null : body.password());
    }

    /** M14e: {@code until} null = permanent. */
    @PostMapping("/{id}/ban")
    AdminPlayerDto ban(@PathVariable long id, @Valid @RequestBody BanRequest body) {
        return adminPlayers.ban(id, body.until(), body.reason());
    }

    @PostMapping("/{id}/unban")
    AdminPlayerDto unban(@PathVariable long id) {
        return adminPlayers.unban(id);
    }

    /** M16: takes an achievement away for good; the body may be missing. */
    @PostMapping("/{id}/achievements/{key}/revoke")
    AchievementRevokedDto revokeAchievement(
            @PathVariable long id,
            @PathVariable String key,
            @Valid @RequestBody(required = false) AchievementRevokeRequest body) {
        return adminPlayers.revokeAchievement(id, key, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/delete")
    DeletedPlayerDto deletePlayer(@PathVariable long id) {
        return new DeletedPlayerDto(id, adminPlayers.delete(id, AdminActor.currentAccountId()));
    }
}
