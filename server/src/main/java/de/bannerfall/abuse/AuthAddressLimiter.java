package de.bannerfall.abuse;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Login and registration attempts per client address (code review 1.2, D2; 01-architecture §4.9):
 * {@code bannerfall.abuse.login-limit.per-minute} logins (default 10) and {@code
 * …register-limit.per-minute} registrations (default 3), each as a token bucket with that many as
 * its burst. Every attempt counts, right or wrong: each costs a slow password hash, and a
 * registration also places barbarian villages. The per-name block of {@code LoginRateLimiter} still
 * applies on top.
 *
 * <p>The address is the real client's: {@code server.forward-headers-strategy: native} takes it
 * from Caddy's {@code X-Forwarded-For}. Players behind one router share it, which these numbers
 * allow for.
 */
@Component
public class AuthAddressLimiter {

    private final TokenBuckets<String> logins;
    private final TokenBuckets<String> registrations;

    AuthAddressLimiter(
            Clock clock,
            @Value("${bannerfall.abuse.login-limit.per-minute:10}") int loginsPerMinute,
            @Value("${bannerfall.abuse.register-limit.per-minute:3}") int registrationsPerMinute) {
        this.logins = new TokenBuckets<>(clock, loginsPerMinute / 60.0, loginsPerMinute);
        this.registrations =
                new TokenBuckets<>(clock, registrationsPerMinute / 60.0, registrationsPerMinute);
    }

    /** False if this address has used up its logins for now. */
    public boolean tryLogin(String address) {
        return logins.tryTake(address);
    }

    /** False if this address has used up its registrations for now. */
    public boolean tryRegister(String address) {
        return registrations.tryTake(address);
    }

    /** Other numbers for a test; the buckets start full again. */
    public void setLimits(int loginsPerMinute, int registrationsPerMinute) {
        logins.setLimits(loginsPerMinute / 60.0, loginsPerMinute);
        registrations.setLimits(registrationsPerMinute / 60.0, registrationsPerMinute);
    }
}
