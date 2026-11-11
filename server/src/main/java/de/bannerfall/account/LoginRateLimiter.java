package de.bannerfall.account;

import de.bannerfall.abuse.ClientNetwork;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory brute-force protection: after {@value #MAX_FAILURES} failed logins for a
 * username from one address (each within {@link #BLOCK_DURATION} of the previous one), that
 * username is blocked for that address until {@link #BLOCK_DURATION} after the last failure. A
 * restart forgets everything.
 *
 * <p>Per name and address, not per name alone (code review 1.2, D1): usernames are public in the
 * ranking, so a script could otherwise keep every player locked out of his own account. Spreading
 * guesses over many addresses is held back by the per-address limit ({@code AuthAddressLimiter}).
 * An IPv6 address counts by its /64 network, the way that whole network is one client (security
 * review of 1.7).
 *
 * <p>On top of that the account has a budget of its own, over all addresses (2.0, F4): {@value
 * #ACCOUNT_MAX_FAILURES} failed logins within {@link #ACCOUNT_WINDOW} (counted from the first) stop
 * the logins of that name until the window is over. Without it, guesses from many networks (each
 * with its own five) were limited only by the address limits. The price: someone who knows a name
 * can keep that player from <em>logging in anew</em> for half an hour, and one address is enough
 * (five wrong passwords a minute stay under the address limits: twenty take four minutes); open
 * sessions go on, and the host's password reset lifts the block ({@link #forget}). A name that does
 * not exist counts the same, so the answer tells nothing about the name.
 */
@Component
class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(1);

    static final int ACCOUNT_MAX_FAILURES = 20;
    static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(30);

    /** Clean up when the map gets this big (someone trying lots of random usernames). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Failures(int count, Instant last) {}

    /** Failures of one name over all addresses; the window starts with the first. */
    private record AccountFailures(int count, Instant first) {}

    private record Key(String usernameKey, String address) {}

    private final Clock clock;
    private final Map<Key, Failures> failuresByKey = new ConcurrentHashMap<>();
    private final Map<String, AccountFailures> failuresByAccount = new ConcurrentHashMap<>();

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** An IPv6 address counts by its /64 network ({@link ClientNetwork}). */
    private static Key key(String usernameKey, String address) {
        return new Key(usernameKey, ClientNetwork.of(address));
    }

    boolean isBlocked(String usernameKey, String address) {
        Failures failures = failuresByKey.get(key(usernameKey, address));
        return failures != null && failures.count() >= MAX_FAILURES && !isExpired(failures);
    }

    /**
     * Counts an attempt as failed before the password is checked, and {@link #recordSuccess} takes
     * it back. Checking first and counting after the slow password hash let a burst of parallel
     * requests all pass the check, hundreds of guesses instead of {@value #MAX_FAILURES} (code
     * review 1.2).
     *
     * @return false, and nothing counted for the name, if the username is blocked for this address
     *     or the name used up its budget over all addresses ({@link #isLocked})
     */
    boolean tryAttempt(String usernameKey, String address) {
        Instant now = clock.instant();
        boolean[] allowed = {true};
        failuresByKey.compute(
                key(usernameKey, address),
                (key, old) -> {
                    if (old == null || isExpired(old)) {
                        return new Failures(1, now);
                    }
                    if (old.count() >= MAX_FAILURES) {
                        allowed[0] = false;
                        return old;
                    }
                    return new Failures(old.count() + 1, now);
                });
        if (allowed[0]) {
            // only an attempt the address was allowed to make counts for the name: a blocked
            // script must not use up the budget of the player it hammers
            failuresByAccount.compute(
                    usernameKey,
                    (name, old) -> {
                        if (old == null || isOver(old)) {
                            return new AccountFailures(1, now);
                        }
                        if (old.count() >= ACCOUNT_MAX_FAILURES) {
                            allowed[0] = false;
                            return old;
                        }
                        return new AccountFailures(old.count() + 1, old.first());
                    });
        }
        if (failuresByKey.size() > CLEANUP_THRESHOLD
                || failuresByAccount.size() > CLEANUP_THRESHOLD) {
            forgetExpired();
        }
        return allowed[0];
    }

    /** True while the name has used up its failures over all addresses (2.0, F4). */
    boolean isLocked(String usernameKey) {
        AccountFailures failures = failuresByAccount.get(usernameKey);
        return failures != null && failures.count() >= ACCOUNT_MAX_FAILURES && !isOver(failures);
    }

    void recordSuccess(String usernameKey, String address) {
        failuresByKey.remove(key(usernameKey, address));
        // whoever knew the password is the owner: the name's failures start over
        failuresByAccount.remove(usernameKey);
    }

    /**
     * Every minute: drops the failures whose block has run out, so a client's address stays in
     * memory only for a few minutes (the privacy page, {@code /privacy}).
     */
    @Scheduled(fixedDelay = 60_000)
    void forgetExpired() {
        failuresByKey.values().removeIf(this::isExpired);
        failuresByAccount.values().removeIf(this::isOver);
    }

    /** How many name and address pairs are held (tests). */
    int entries() {
        return failuresByKey.size();
    }

    /** How many names are held (tests). */
    int accountsHeld() {
        return failuresByAccount.size();
    }

    /** Lifts every block of the username (the host set a new password). */
    void forget(String usernameKey) {
        failuresByKey.keySet().removeIf(key -> key.usernameKey().equals(usernameKey));
        failuresByAccount.remove(usernameKey);
    }

    private boolean isOver(AccountFailures failures) {
        return !clock.instant().isBefore(failures.first().plus(ACCOUNT_WINDOW));
    }

    private boolean isExpired(Failures failures) {
        return !clock.instant().isBefore(failures.last().plus(BLOCK_DURATION));
    }
}
