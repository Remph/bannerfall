package de.bannerfall.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Wrong host tokens are limited like logins (08 §4): after {@value #MAX_FAILURES} wrong tokens from
 * one address, each within {@link #BLOCK_DURATION} of the previous one, every token request from
 * that address is refused with 429 until {@link #BLOCK_DURATION} after the last failure – the right
 * token too, so guessing doesn't pay.
 *
 * <p>Counted per remote address (M14d review): with one counter for everyone, anybody on the
 * internet could lock the host out of his own CLI for good with five bad requests a minute. Behind
 * the reverse proxy all outside requests share Caddy's address, while the CLI calls {@code
 * localhost:8080} inside the container, so it keeps working while the outside door is blocked. In
 * memory; a restart forgets it.
 */
@Component
public class AdminTokenLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(1);

    /** Above this many addresses, the expired ones are dropped at the next failure. */
    private static final int CLEANUP_SIZE = 1000;

    private final Clock clock;
    private final Map<String, Failures> failures = new HashMap<>();

    AdminTokenLimiter(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean isBlocked(String address) {
        Failures entry = failures.get(address);
        return entry != null && entry.count >= MAX_FAILURES && !expired(entry);
    }

    synchronized void recordFailure(String address) {
        if (failures.size() > CLEANUP_SIZE) {
            failures.values().removeIf(this::expired);
        }
        Failures entry = failures.get(address);
        int count = entry == null || expired(entry) ? 1 : entry.count + 1;
        failures.put(address, new Failures(count, clock.instant()));
    }

    synchronized void recordSuccess(String address) {
        failures.remove(address);
    }

    /** Forgets every address (tests). */
    synchronized void clear() {
        failures.clear();
    }

    private boolean expired(Failures entry) {
        return !clock.instant().isBefore(entry.last.plus(BLOCK_DURATION));
    }

    private record Failures(int count, Instant last) {}
}
