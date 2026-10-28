package de.bannerfall.abuse;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Login and registration attempts per client address (code review 1.2, D2; 01-architecture §4.9):
 * {@code bannerfall.abuse.login-limit.per-minute} logins (default 10) and {@code
 * …register-limit.per-minute} registrations (default 3), each as a token bucket with that many as
 * its burst. Every attempt counts, right or wrong: each costs a slow password hash, and a
 * registration also places barbarian villages. The per-name block of {@code LoginRateLimiter} still
 * applies on top. The login through the account server (1.5: its start, its way back, and the
 * account server's logout tokens) has its own bucket of {@code …external-limit.per-minute} (default
 * 20): each of them makes a session or asks the account server.
 *
 * <p>The address is the real client's: {@code server.forward-headers-strategy: native} takes it
 * from Caddy's {@code X-Forwarded-For}. Players behind one router share it, which these numbers
 * allow for. An IPv6 address counts by its /64 network ({@link ClientNetwork}): per full address a
 * client with an IPv6 connection would have no limit at all (security review of 1.7).
 */
@Component
public class AuthAddressLimiter {

    private final TokenBuckets<String> logins;
    private final TokenBuckets<String> registrations;
    private final TokenBuckets<String> external;

    AuthAddressLimiter(
            Clock clock,
            @Value("${bannerfall.abuse.login-limit.per-minute:10}") int loginsPerMinute,
            @Value("${bannerfall.abuse.register-limit.per-minute:3}") int registrationsPerMinute,
            @Value("${bannerfall.abuse.external-limit.per-minute:20}") int externalPerMinute) {
        this.logins = new TokenBuckets<>(clock, loginsPerMinute / 60.0, loginsPerMinute);
        this.registrations =
                new TokenBuckets<>(clock, registrationsPerMinute / 60.0, registrationsPerMinute);
        this.external = new TokenBuckets<>(clock, externalPerMinute / 60.0, externalPerMinute);
    }

    /** False if this address has used up its logins for now. */
    public boolean tryLogin(String address) {
        return logins.tryTake(ClientNetwork.of(address));
    }

    /** False if this address has used up its registrations for now. */
    public boolean tryRegister(String address) {
        return registrations.tryTake(ClientNetwork.of(address));
    }

    /** False if this address has used up its steps of the login through the account server. */
    public boolean tryExternal(String address) {
        return external.tryTake(ClientNetwork.of(address));
    }

    /**
     * Every minute: drops the addresses whose buckets are full again (no attempt for up to a
     * minute), so they stay in memory only for a few minutes (the privacy page, {@code /privacy}).
     */
    @Scheduled(fixedDelay = 60_000)
    public void forgetOldAddresses() {
        logins.forgetFull();
        registrations.forgetFull();
        external.forgetFull();
    }

    /** How many addresses are held (tests). */
    int addressesHeld() {
        return logins.size() + registrations.size() + external.size();
    }

    /** Other numbers for a test; the buckets start full again. */
    public void setLimits(int loginsPerMinute, int registrationsPerMinute) {
        logins.setLimits(loginsPerMinute / 60.0, loginsPerMinute);
        registrations.setLimits(registrationsPerMinute / 60.0, registrationsPerMinute);
    }

    /** Another number for the login through the account server, for a test. */
    public void setExternalLimit(int perMinute) {
        external.setLimits(perMinute / 60.0, perMinute);
    }
}
