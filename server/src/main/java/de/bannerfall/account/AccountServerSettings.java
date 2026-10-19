package de.bannerfall.account;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The account server this world's players may log in through (1.5, 01-architecture §4.9a): an
 * OpenID Connect provider, with this world registered there as a client. Environment variables
 * {@code BANNERFALL_ACCOUNT_SERVER_ISSUER}, {@code _CLIENT_ID}, {@code _CLIENT_SECRET}, {@code
 * _NAME} (the button's label, default the issuer's host), {@code _MODE} ({@link AccountLoginMode})
 * and {@code _CAREER} (whether the world reports its linked players' careers to the account server,
 * 01-architecture §4.9b; only Bannerfall's own account server takes them). Without an issuer the
 * world has only its own accounts.
 */
@Component
public class AccountServerSettings {

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String name;
    private volatile AccountLoginMode mode;
    private volatile boolean career;

    AccountServerSettings(
            @Value("${bannerfall.account-server.issuer:}") String issuer,
            @Value("${bannerfall.account-server.client-id:}") String clientId,
            @Value("${bannerfall.account-server.client-secret:}") String clientSecret,
            @Value("${bannerfall.account-server.name:}") String name,
            @Value("${bannerfall.account-server.mode:OFF}") AccountLoginMode mode,
            @Value("${bannerfall.account-server.career:false}") boolean career) {
        // exactly as the provider names itself (its discovery document and its tokens)
        this.issuer = issuer.strip();
        this.clientId = clientId.strip();
        this.clientSecret = clientSecret.strip();
        this.mode = mode;
        this.career = career;
        if ((mode != AccountLoginMode.OFF || career) && this.issuer.isEmpty()) {
            throw new IllegalStateException(
                    "bannerfall.account-server.mode is "
                            + mode
                            + (career ? " with career reports" : "")
                            + " but no issuer is set (BANNERFALL_ACCOUNT_SERVER_ISSUER)");
        }
        if (!this.issuer.isEmpty()) {
            if (this.clientId.isEmpty() || this.clientSecret.isEmpty()) {
                throw new IllegalStateException(
                        "An account server needs BANNERFALL_ACCOUNT_SERVER_CLIENT_ID and"
                                + " BANNERFALL_ACCOUNT_SERVER_CLIENT_SECRET");
            }
            URI uri = URI.create(this.issuer);
            if (!"https".equals(uri.getScheme()) && !isLocal(uri)) {
                throw new IllegalStateException(
                        "The account server's issuer must be an https address: " + this.issuer);
            }
        }
        this.name = name.isBlank() ? hostOf(this.issuer) : name.strip();
    }

    /** An account server is set up (whatever the mode). */
    public boolean configured() {
        return !issuer.isEmpty();
    }

    public AccountLoginMode mode() {
        return mode;
    }

    /** Players may log in or register with a name and password of this world. */
    public boolean localLoginAllowed() {
        return mode != AccountLoginMode.REQUIRED;
    }

    public String issuer() {
        return issuer;
    }

    public String clientId() {
        return clientId;
    }

    /** Only for talking to the account server (the login, the career reports). */
    public String clientSecret() {
        return clientSecret;
    }

    /** What the login button says it logs in with, e.g. {@code example.com}. */
    public String name() {
        return name;
    }

    /**
     * The world reports its linked players' careers to the account server (§4.9b). Never in mode
     * {@code OFF}, which keeps everything as it was without an account server.
     */
    public boolean careerReports() {
        return career && mode != AccountLoginMode.OFF;
    }

    /** Career reports on or off, for tests. */
    public void setCareerReports(boolean career) {
        if (career && !configured()) {
            throw new IllegalStateException("No account server is set up");
        }
        this.career = career;
    }

    /** Another mode, for tests (the test world has an account server in mode OFF). */
    public void setMode(AccountLoginMode mode) {
        if (mode != AccountLoginMode.OFF && !configured()) {
            throw new IllegalStateException("No account server is set up");
        }
        this.mode = mode;
    }

    private static boolean isLocal(URI uri) {
        return "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    }

    private static String hostOf(String issuer) {
        return issuer.isEmpty() ? "" : URI.create(issuer).getHost();
    }
}
