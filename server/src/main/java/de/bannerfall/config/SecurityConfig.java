package de.bannerfall.config;

import de.bannerfall.abuse.AbuseKind;
import de.bannerfall.abuse.AbuseService;
import de.bannerfall.abuse.AccountGuardFilter;
import de.bannerfall.abuse.AuthAddressLimiter;
import de.bannerfall.abuse.WriteRateLimiter;
import de.bannerfall.account.AccountServerClient;
import de.bannerfall.account.AccountServerSettings;
import de.bannerfall.account.AccountStates;
import de.bannerfall.account.ExternalLoginProblem;
import de.bannerfall.account.ExternalLoginService;
import de.bannerfall.admin.AdminAccess;
import de.bannerfall.admin.AdminToken;
import de.bannerfall.admin.AdminTokenFilter;
import de.bannerfall.admin.AdminTokenLimiter;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.event.StartupGate;
import de.bannerfall.event.StartupGateFilter;
import de.bannerfall.world.PregameGateFilter;
import de.bannerfall.world.WorldService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Session cookie login for the React app (docs/plan/01-architecture.md section 4.9).
 *
 * <ul>
 *   <li>Login, register and logout are normal endpoints in {@code AuthController}; no form login,
 *       no HTTP basic, no redirects.
 *   <li>CSRF: token in the readable cookie {@code XSRF-TOKEN}, the client sends it back as header
 *       {@code X-XSRF-TOKEN} (Spring Security's SPA setup).
 *   <li>Errors are JSON (ProblemDetail with {@code code}), like the rest of the API.
 *   <li>{@code /api/admin/**}: the host token ({@code AdminTokenFilter}, 08-host-console §4) or an
 *       account that is ADMIN at the moment of the request ({@code AdminAccess}).
 *   <li>Every request of a logged-in account passes {@code AccountGuardFilter} first (M14e): ended
 *       sessions (ban, deletion, password), the write rate limit, the body size.
 *   <li>With an account server (1.5, 01-architecture §4.9a): Spring's OAuth2 login at {@code
 *       /oauth2/authorization/accounts} and {@code /login/oauth2/code/accounts}, with PKCE; {@code
 *       ExternalLoginService} turns a checked login into this world's session.
 * </ul>
 */
@Configuration
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokens,
            AdminToken adminToken,
            AdminTokenLimiter adminTokenLimiter,
            AdminAccess adminAccess,
            AccountStates accountStates,
            WriteRateLimiter writeRateLimiter,
            AuthAddressLimiter authAddressLimiter,
            AbuseService abuse,
            WorldService worldService,
            StartupGate startupGate,
            CorsConfigurationSource corsConfigurationSource,
            Clock clock,
            AccountServerSettings accountServer,
            AccountServerClient accountServerClient,
            ExternalLoginService externalLogins)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/ping",
                                                "/api/auth/**",
                                                "/api/world/status",
                                                "/api/world/operator",
                                                "/actuator/health/**",
                                                "/error")
                                        .permitAll()
                                        // springdoc is switched off in prod (application-prod.yml)
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll()
                                        // the host token, or an account that is ADMIN now
                                        .requestMatchers("/api/admin/**")
                                        .access(
                                                (authentication, context) ->
                                                        new AuthorizationDecision(
                                                                adminAccess.isAdmin(
                                                                        authentication.get())))
                                        .requestMatchers("/api/**", "/ws/**")
                                        .authenticated()
                                        // everything else is the static web app (M14b)
                                        .anyRequest()
                                        .permitAll())
                // spa(): cookie token repository + a request handler that accepts the raw cookie
                // value in the header. Our own repository bean so LoginService can renew tokens.
                // Requests with the host token carry no cookie, so there is nothing to forge.
                .csrf(
                        csrf ->
                                csrf.spa()
                                        .csrfTokenRepository(csrfTokens)
                                        .ignoringRequestMatchers(AdminTokenFilter::isTokenRequest)
                                        // server to server, with a signed token instead
                                        .ignoringRequestMatchers(
                                                request ->
                                                        "/api/auth/backchannel-logout"
                                                                .equals(RequestPaths.of(request))))
                .addFilterBefore(
                        new AdminTokenFilter(adminToken, adminTokenLimiter), CsrfFilter.class)
                // Right after the session's login is loaded, before CSRF and authorization.
                .addFilterAfter(
                        new AccountGuardFilter(
                                accountStates, writeRateLimiter, authAddressLimiter, abuse, clock),
                        SecurityContextHolderFilter.class)
                // The pre-game has no map: game requests are refused (M14g). After the
                // authorization,
                // so a visitor without login still gets 401.
                .addFilterAfter(new PregameGateFilter(worldService), AuthorizationFilter.class)
                // After a restart, game requests wait until the worker has caught up (code
                // review 1.2, D3).
                .addFilterAfter(new StartupGateFilter(startupGate), PregameGateFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (request, response, e) ->
                                                        ProblemResponses.write(
                                                                response,
                                                                HttpStatus.UNAUTHORIZED,
                                                                ErrorCode.UNAUTHENTICATED))
                                        .accessDeniedHandler(
                                                (request, response, e) -> {
                                                    boolean csrf = e instanceof CsrfException;
                                                    if (csrf) {
                                                        abuse.suspect(
                                                                request,
                                                                AbuseKind.CSRF_FAILED,
                                                                e.getMessage());
                                                    }
                                                    ProblemResponses.write(
                                                            response,
                                                            HttpStatus.FORBIDDEN,
                                                            csrf
                                                                    ? ErrorCode.CSRF_TOKEN_INVALID
                                                                    : ErrorCode.FORBIDDEN);
                                                }))
                // Logout is POST /api/auth/logout in AuthController.
                .logout(AbstractHttpConfigurer::disable)
                // Don't create sessions for anonymous requests to remember "where to go after
                // login".
                .requestCache(AbstractHttpConfigurer::disable);
        if (accountServer.configured()) {
            accountServerLogin(http, accountServer, accountServerClient, externalLogins);
        }
        return http.build();
    }

    /**
     * The login through the account server (1.5). Spring checks the answer (state, PKCE, nonce, the
     * signed ID token); the session it would store is replaced by ours, and nothing of the
     * provider's tokens is kept: the world only needs to know who logged in.
     */
    private static void accountServerLogin(
            HttpSecurity http,
            AccountServerSettings accountServer,
            AccountServerClient client,
            ExternalLoginService externalLogins)
            throws Exception {
        DefaultOAuth2AuthorizationRequestResolver requests =
                new DefaultOAuth2AuthorizationRequestResolver(
                        client,
                        OAuth2AuthorizationRequestRedirectFilter
                                .DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        requests.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        // The name comes from the ID token (preferred_username): no second call to the provider.
        OidcUserService users = new OidcUserService();
        users.setRetrieveUserInfo(request -> false);
        http.oauth2Login(
                        login ->
                                login
                                        // the web app's page: no generated login page
                                        .loginPage("/login")
                                        .clientRegistrationRepository(client)
                                        .authorizedClientRepository(new NoAuthorizedClients())
                                        .withObjectPostProcessor(
                                                // Not the session: ExternalLoginService stores
                                                // the world's own login there. (The configurer's
                                                // securityContextRepository() doesn't reach the
                                                // filter.)
                                                new ObjectPostProcessor<
                                                        OAuth2LoginAuthenticationFilter>() {
                                                    @Override
                                                    public <
                                                                    O extends
                                                                            OAuth2LoginAuthenticationFilter>
                                                            O postProcess(O filter) {
                                                        filter.setSecurityContextRepository(
                                                                new RequestAttributeSecurityContextRepository());
                                                        return filter;
                                                    }
                                                })
                                        .authorizationEndpoint(
                                                endpoint ->
                                                        endpoint.authorizationRequestResolver(
                                                                requests))
                                        .userInfoEndpoint(info -> info.oidcUserService(users))
                                        .successHandler(
                                                (request, response, authentication) -> {
                                                    String next =
                                                            authentication.getPrincipal()
                                                                            instanceof OidcUser user
                                                                    ? externalLogins.loggedIn(
                                                                            accountServer.issuer(),
                                                                            user.getSubject(),
                                                                            user
                                                                                    .getPreferredUsername(),
                                                                            request,
                                                                            response)
                                                                    : externalLogins.failed(
                                                                            request,
                                                                            ExternalLoginProblem
                                                                                    .FAILED);
                                                    response.sendRedirect(next);
                                                })
                                        .failureHandler(
                                                (request, response, e) -> {
                                                    log.info(
                                                            "Login through the account server"
                                                                    + " failed: {}",
                                                            loginFailure(e));
                                                    response.sendRedirect(
                                                            externalLogins.failed(
                                                                    request,
                                                                    ExternalLoginProblem.FAILED));
                                                }))
                .addFilterBefore(
                        new AccountServerGateFilter(accountServer, client, externalLogins),
                        OAuth2AuthorizationRequestRedirectFilter.class);
    }

    /**
     * What to log of a failed login through the account server: the error code, or the exception's
     * kind. Not the message: an error answer's code and description come from the callback's
     * address, which anyone can call, and would let them write lines of their own into the log.
     */
    static String loginFailure(AuthenticationException e) {
        String code =
                e instanceof OAuth2AuthenticationException oauth
                        ? oauth.getError().getErrorCode()
                        : e.getClass().getSimpleName();
        String safe = code == null ? "" : code.replaceAll("[^A-Za-z0-9_.-]", "?");
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    /**
     * The provider's access token isn't needed after the login: none is kept (Spring's default
     * would keep one per login in memory).
     */
    private static final class NoAuthorizedClients implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                String clientRegistrationId, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient authorizedClient,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(
                String clientRegistrationId,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Spring's default (bcrypt) rejects passwords longer than 72 bytes, but we allow up to 128
        // characters. PBKDF2 has no such limit. The {id} prefix in the stored hash keeps the door
        // open for switching algorithms later.
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(
                id, Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
