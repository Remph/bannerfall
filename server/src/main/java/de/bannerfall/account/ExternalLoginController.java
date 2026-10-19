package de.bannerfall.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The account server's side of the login (1.5, 01-architecture §4.9a, 04-api "Auth"). The login
 * itself runs through Spring Security's addresses {@code /oauth2/authorization/accounts} and {@code
 * /login/oauth2/code/accounts} ({@code ExternalLoginService#loggedIn}).
 */
@RestController
@RequestMapping("/api")
class ExternalLoginController {

    private final AccountServerSettings settings;
    private final ExternalLoginService externalLogins;
    private final AccountService accountService;

    ExternalLoginController(
            AccountServerSettings settings,
            ExternalLoginService externalLogins,
            AccountService accountService) {
        this.settings = settings;
        this.externalLogins = externalLogins;
        this.accountService = accountService;
    }

    /** How players log in here: own accounts, the account server, or both. No login needed. */
    @GetMapping("/auth/options")
    AuthOptionsDto options() {
        AccountLoginMode mode = settings.mode();
        return new AuthOptionsDto(
                mode,
                mode == AccountLoginMode.OFF ? null : settings.name(),
                settings.careerReports());
    }

    /** The pending join and the last problem of a login through the account server. */
    @GetMapping("/auth/external")
    ExternalLoginDto external(HttpServletRequest request) {
        return externalLogins.state(request);
    }

    /** Joins this world after a login at the account server: {@code 201} and the new "me". */
    @PostMapping("/auth/join")
    @ResponseStatus(HttpStatus.CREATED)
    MeDto join(
            @Valid @RequestBody JoinRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Account account = externalLogins.join(body.inviteCode(), request, response);
        return accountService.me(account.getId());
    }

    /**
     * Starts linking the account to the account server, with the account's current password: {@code
     * 204}, then the web app sends the browser to {@code /oauth2/authorization/accounts}.
     */
    @PostMapping("/me/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void link(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LinkRequest body,
            HttpServletRequest request) {
        externalLogins.startLink(principal.accountId(), body.currentPassword(), request);
    }

    /**
     * OpenID Connect Back-Channel Logout: the account server sends a signed logout token when an
     * account's logins must end. {@code 200}, or {@code 400} for a token that doesn't check out.
     * Server to server: no session, no CSRF token.
     */
    @PostMapping(
            path = "/auth/backchannel-logout",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> backchannelLogout(@RequestParam("logout_token") String logoutToken) {
        try {
            externalLogins.backchannelLogout(logoutToken);
        } catch (JwtException | IllegalStateException e) {
            return ResponseEntity.badRequest().header("Cache-Control", "no-store").build();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").build();
    }
}
