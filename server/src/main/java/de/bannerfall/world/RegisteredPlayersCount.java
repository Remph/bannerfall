package de.bannerfall.world;

import de.bannerfall.player.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The number of registered players for the public {@code GET /api/world/status} (M19). That path
 * needs no login, so anybody (and every visitor of the landing page, once a minute) can call it as
 * often as they like: the count is taken from the database at most once per {@link #MAX_AGE} and
 * the answers in between come from memory, so the load on the database does not depend on how often
 * the endpoint is asked. The numbers behind the login ({@code GET /api/world}) stay exact.
 */
@Component
class RegisteredPlayersCount {

    static final Duration MAX_AGE = Duration.ofSeconds(10);

    private final PlayerRepository players;
    private final Clock clock;

    private long value;
    private Instant countedAt;

    RegisteredPlayersCount(PlayerRepository players, Clock clock) {
        this.players = players;
        this.clock = clock;
    }

    /** Synchronized on purpose: while one request counts, the others wait for it, not repeat it. */
    synchronized long get() {
        Instant now = clock.instant();
        // A clock that went backwards (tests) counts as expired too.
        if (countedAt == null
                || now.isBefore(countedAt)
                || !now.isBefore(countedAt.plus(MAX_AGE))) {
            value = players.countRegisteredHumans();
            countedAt = now;
        }
        return value;
    }
}
