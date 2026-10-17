package de.bannerfall.config;

import de.bannerfall.abuse.AbuseKind;
import de.bannerfall.abuse.AbuseService;
import de.bannerfall.abuse.AccountGuardFilter;
import de.bannerfall.abuse.AuthAddressLimiter;
import de.bannerfall.abuse.WriteRateLimiter;
import de.bannerfall.account.AccountStates;
import de.bannerfall.admin.AdminAccess;
import de.bannerfall.admin.AdminToken;
import de.bannerfall.admin.AdminTokenFilter;
import de.bannerfall.admin.AdminTokenLimiter;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.event.StartupGate;
import de.bannerfall.event.StartupGateFilter;
import de.bannerfall.world.PregameGateFilter;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
 * </ul>
 */
@Configuration
class SecurityConfig {

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
            Clock clock)
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
                                        .ignoringRequestMatchers(AdminTokenFilter::isTokenRequest))
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
        return http.build();
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
