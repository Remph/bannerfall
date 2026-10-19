package de.bannerfall.account;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * This world as a client of the account server (1.5, 01-architecture §4.9a). The provider's
 * addresses come from its discovery document ({@code <issuer>/.well-known/openid-configuration}),
 * read at the first login rather than at startup: a world must start and keep its local accounts
 * working while the account server is down. After a failed attempt the next one waits a minute.
 */
@Component
public class AccountServerClient implements ClientRegistrationRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountServerClient.class);

    /**
     * The registration's id, part of the addresses: the login starts at {@code
     * /oauth2/authorization/accounts} and comes back to {@code /login/oauth2/code/accounts}.
     */
    public static final String REGISTRATION_ID = "accounts";

    /** The claim of a logout token (OpenID Connect Back-Channel Logout 1.0). */
    static final String BACKCHANNEL_LOGOUT_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";

    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    private final AccountServerSettings settings;
    private final Clock clock;
    private volatile ClientRegistration registration;
    private volatile JwtDecoder logoutTokenDecoder;
    private Instant failedAt;

    AccountServerClient(AccountServerSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** The account server's registration, or empty when none is set up or it can't be reached. */
    public Optional<ClientRegistration> registration() {
        if (!settings.configured()) {
            return Optional.empty();
        }
        ClientRegistration known = registration;
        if (known != null) {
            return Optional.of(known);
        }
        synchronized (this) {
            if (registration != null) {
                return Optional.of(registration);
            }
            Instant now = clock.instant();
            if (failedAt != null && now.isBefore(failedAt.plus(RETRY_AFTER))) {
                return Optional.empty();
            }
            try {
                registration =
                        ClientRegistrations.fromIssuerLocation(settings.issuer())
                                .registrationId(REGISTRATION_ID)
                                .clientId(settings.clientId())
                                .clientSecret(settings.clientSecret())
                                .scope("openid", "profile")
                                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                                .clientName(settings.name())
                                .build();
                failedAt = null;
                return Optional.of(registration);
            } catch (RuntimeException e) {
                failedAt = now;
                log.warn(
                        "The account server {} can't be reached: {}",
                        settings.issuer(),
                        e.getMessage());
                return Optional.empty();
            }
        }
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return REGISTRATION_ID.equals(registrationId) ? registration().orElse(null) : null;
    }

    /**
     * Checks a logout token the account server sent (signature by its published keys, issuer,
     * audience, age, the back-channel logout event) and returns its subject.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException if it isn't a valid one
     * @throws IllegalStateException if the account server can't be reached
     */
    String subjectOfLogoutToken(String token) {
        Jwt jwt = logoutTokenDecoder().decode(token);
        return jwt.getSubject();
    }

    private JwtDecoder logoutTokenDecoder() {
        JwtDecoder decoder = logoutTokenDecoder;
        if (decoder != null) {
            return decoder;
        }
        ClientRegistration client =
                registration()
                        .orElseThrow(() -> new IllegalStateException("No account server to ask"));
        // The keys are fetched again for a token with an unknown key id, at most every 30 seconds
        // (rateLimited): the endpoint is public, so a stream of made-up tokens must not turn into
        // a stream of requests to the account server. The type is checked below.
        JWKSource<SecurityContext> keys;
        try {
            keys =
                    JWKSourceBuilder.create(
                                    URI.create(client.getProviderDetails().getJwkSetUri()).toURL(),
                                    new DefaultResourceRetriever(5_000, 10_000))
                            .cache(true)
                            .rateLimited(true)
                            .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("The account server's key address is not a URL", e);
        }
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSource(keys).build();
        JwtTypeValidator type = new JwtTypeValidator("logout+jwt", "JWT");
        type.setAllowEmpty(true);
        nimbus.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(settings.issuer()),
                        type,
                        this::logoutTokenChecks));
        logoutTokenDecoder = nimbus;
        return nimbus;
    }

    /** The checks of OpenID Connect Back-Channel Logout §2.6 that the JWT validators don't do. */
    private OAuth2TokenValidatorResult logoutTokenChecks(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        Object events = jwt.getClaims().get("events");
        boolean ok =
                audience != null
                        && audience.contains(settings.clientId())
                        && jwt.getSubject() != null
                        && jwt.getIssuedAt() != null
                        && jwt.getExpiresAt() != null
                        && !jwt.hasClaim("nonce")
                        && events instanceof Map<?, ?> map
                        && map.containsKey(BACKCHANNEL_LOGOUT_EVENT);
        return ok
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_request", "Not a logout token for this world", null));
    }
}
