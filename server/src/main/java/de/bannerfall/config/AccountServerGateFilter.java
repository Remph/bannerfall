package de.bannerfall.config;

import de.bannerfall.account.AccountLoginMode;
import de.bannerfall.account.AccountServerClient;
import de.bannerfall.account.AccountServerSettings;
import de.bannerfall.account.ExternalLoginProblem;
import de.bannerfall.account.ExternalLoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In front of Spring's {@code /oauth2/authorization/accounts} (1.5): while the mode is {@code OFF}
 * or the account server can't be reached, the browser goes back to the login page with the problem
 * {@code UNAVAILABLE} instead of to Spring's error page.
 */
class AccountServerGateFilter extends OncePerRequestFilter {

    private static final String START =
            "/oauth2/authorization/" + AccountServerClient.REGISTRATION_ID;

    private final AccountServerSettings settings;
    private final AccountServerClient client;
    private final ExternalLoginService externalLogins;

    AccountServerGateFilter(
            AccountServerSettings settings,
            AccountServerClient client,
            ExternalLoginService externalLogins) {
        this.settings = settings;
        this.client = client;
        this.externalLogins = externalLogins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !START.equals(RequestPaths.of(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (settings.mode() == AccountLoginMode.OFF || client.registration().isEmpty()) {
            response.sendRedirect(externalLogins.failed(request, ExternalLoginProblem.UNAVAILABLE));
            return;
        }
        chain.doFilter(request, response);
    }
}
