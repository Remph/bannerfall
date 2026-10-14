package de.bannerfall.account;

import de.bannerfall.achievement.AchievementService;
import de.bannerfall.village.VillageSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AuthController {

    private final AccountService accountService;
    private final LoginService loginService;
    private final AchievementService achievements;

    AuthController(
            AccountService accountService,
            LoginService loginService,
            AchievementService achievements) {
        this.accountService = accountService;
        this.loginService = loginService;
        this.achievements = achievements;
    }

    /** Makes sure the XSRF-TOKEN cookie exists. The client calls it before its first POST. */
    @GetMapping("/auth/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(HttpServletRequest request) {
        // Spring Security creates the token lazily; reading it writes the cookie.
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    MeDto register(
            @Valid @RequestBody RegisterRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Account account = accountService.register(body);
        loginService.loginWithoutPassword(account, request, response);
        return accountService.me(account.getId());
    }

    @PostMapping("/auth/login")
    MeDto login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AccountPrincipal principal = loginService.login(body, request, response);
        achievements.checkAtLogin(principal.accountId());
        return accountService.me(principal.accountId());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        loginService.logout(request, response);
    }

    @GetMapping("/me")
    MeDto me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accountService.me(principal.accountId());
    }

    /** A player who lost all his villages starts again (only while {@code needsRespawn}). */
    @PostMapping("/me/respawn")
    @ResponseStatus(HttpStatus.CREATED)
    VillageSummaryDto respawn(@AuthenticationPrincipal AccountPrincipal principal) {
        return accountService.respawn(principal.accountId());
    }

    /** The player changes his own password (M14d): {@code 204}, {@code 409 WRONG_PASSWORD}. */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            @AuthenticationPrincipal AccountPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        Account account =
                accountService.changePassword(
                        principal.accountId(),
                        body.currentPassword(),
                        body.newPassword(),
                        request.getRemoteAddr());
        // The change ended every session of the account (M14e); this one goes on.
        loginService.renewSession(account, request, response);
    }
}
