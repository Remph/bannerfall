package de.bannerfall;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

/**
 * A small OpenID Connect provider for the tests of the account server login (1.5): discovery, its
 * keys, and a token endpoint that checks the client's secret and the PKCE verifier and answers with
 * a signed ID token, and the address for career reports (§4.9b). The browser part (the provider's
 * login page) is skipped: a test takes the redirect the world answered with and asks {@link #code}
 * for the code the provider would send back. Real time, not the test clock: Spring checks the ID
 * token's times against the system clock.
 */
public final class FakeAccountServer {

    public static final String CLIENT_ID = "test-world";
    public static final String CLIENT_SECRET = "test-world-secret";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger subjects = new AtomicInteger();

    private record Grant(String subject, String name, String nonce, String challenge) {}

    private final HttpServer server;
    private final RSAKey key;
    private final Map<String, Grant> codes = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> careerReports = new CopyOnWriteArrayList<>();
    private volatile int careerStatus = 200;

    private FakeAccountServer() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", this::discovery);
        server.createContext("/jwks", this::jwks);
        server.createContext("/token", this::token);
        server.createContext("/realm-api/career", this::career);
        server.start();
    }

    private static FakeAccountServer instance;

    /** The one provider of the test run (the shared Spring context points at it). */
    public static synchronized FakeAccountServer get() {
        if (instance == null) {
            try {
                instance = new FakeAccountServer();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A new account at the provider: its subject. */
    public static String newSubject() {
        return "subject-" + subjects.incrementAndGet() + "-" + UUID.randomUUID();
    }

    /**
     * What the provider sends back after its login: the code for {@code subject} named {@code
     * name}, for the authorization request the world redirected to ({@code location}).
     */
    public String code(String location, String subject, String name) {
        Map<String, String> query = query(URI.create(location).getRawQuery());
        if (!"S256".equals(query.get("code_challenge_method"))) {
            throw new IllegalStateException("No PKCE: " + location);
        }
        String code = UUID.randomUUID().toString();
        codes.put(code, new Grant(subject, name, query.get("nonce"), query.get("code_challenge")));
        return code;
    }

    /** How often the world exchanged a code. */
    public int tokenCalls() {
        return tokenCalls.get();
    }

    /** The career reports the world sent (JSON bodies), oldest first; cleared by the call. */
    public List<String> takeCareerReports() {
        List<String> taken = List.copyOf(careerReports);
        careerReports.removeAll(taken);
        return taken;
    }

    /** What the career address answers from now on (200 by default). */
    public void careerStatus(int status) {
        careerStatus = status;
    }

    /** A logout token (Back-Channel Logout) for {@code subject}, signed with the provider's key. */
    public String logoutToken(String subject) {
        return logoutToken(subject, CLIENT_ID, key);
    }

    /** The same, for another audience. */
    public String logoutTokenFor(String subject, String audience) {
        return logoutToken(subject, audience, key);
    }

    /** The same, for another audience or signed with another key. */
    public String logoutToken(String subject, String audience, RSAKey signingKey) {
        return sign(
                logoutClaims(subject, audience)
                        .expirationTime(Date.from(clock.instant().plus(Duration.ofMinutes(2))))
                        .build(),
                new JOSEObjectType("logout+jwt"),
                signingKey);
    }

    /** One that would never expire. */
    public String logoutTokenWithoutExpiry(String subject) {
        return sign(
                logoutClaims(subject, CLIENT_ID).build(), new JOSEObjectType("logout+jwt"), key);
    }

    private JWTClaimsSet.Builder logoutClaims(String subject, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(clock.instant()))
                .jwtID(UUID.randomUUID().toString())
                .claim(
                        "events",
                        Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
    }

    /** A key the world doesn't know. */
    public static RSAKey strangerKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void discovery(HttpExchange exchange) throws IOException {
        String issuer = issuer();
        Map<String, Object> document = new HashMap<>();
        document.put("issuer", issuer);
        document.put("authorization_endpoint", issuer + "/authorize");
        document.put("token_endpoint", issuer + "/token");
        document.put("jwks_uri", issuer + "/jwks");
        document.put("response_types_supported", List.of("code"));
        document.put("subject_types_supported", List.of("public"));
        document.put("id_token_signing_alg_values_supported", List.of("RS256"));
        document.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic"));
        document.put("scopes_supported", List.of("openid", "profile"));
        document.put("code_challenge_methods_supported", List.of("S256"));
        answer(exchange, 200, JSON.writeValueAsString(document));
    }

    private void jwks(HttpExchange exchange) throws IOException {
        answer(exchange, 200, new JWKSet(key.toPublicJWK()).toString());
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenCalls.incrementAndGet();
        if (!basicAuthOk(exchange)) {
            answer(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Map<String, String> form =
                query(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Grant grant = codes.remove(form.get("code"));
        if (grant == null || !grant.challenge().equals(s256(form.get("code_verifier")))) {
            answer(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        Instant now = clock.instant();
        JWTClaimsSet.Builder claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer())
                        .subject(grant.subject())
                        .audience(CLIENT_ID)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
                        .claim("nonce", grant.nonce())
                        .claim("azp", CLIENT_ID);
        if (grant.name() != null) {
            claims.claim("preferred_username", grant.name());
        }
        String idToken = sign(claims.build(), JOSEObjectType.JWT, key);
        answer(
                exchange,
                200,
                JSON.writeValueAsString(
                        Map.of(
                                "access_token",
                                UUID.randomUUID().toString(),
                                "token_type",
                                "Bearer",
                                "expires_in",
                                300,
                                "scope",
                                "openid profile",
                                "id_token",
                                idToken)));
    }

    private void career(HttpExchange exchange) throws IOException {
        if (!basicAuthOk(exchange)) {
            answer(exchange, 401, "{}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (careerStatus == 200) {
            careerReports.add(body);
        }
        answer(exchange, careerStatus, "{}");
    }

    private static boolean basicAuthOk(HttpExchange exchange) {
        String expected =
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString(
                                        (CLIENT_ID + ":" + CLIENT_SECRET)
                                                .getBytes(StandardCharsets.UTF_8));
        return expected.equals(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private static String sign(JWTClaimsSet claims, JOSEObjectType type, RSAKey signingKey) {
        try {
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(signingKey.getKeyID())
                                    .type(type)
                                    .build(),
                            claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String s256(String verifier) {
        if (verifier == null) {
            return "";
        }
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.put(
                    URLDecoder.decode(name, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
