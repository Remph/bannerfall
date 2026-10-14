package de.bannerfall.village;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The last loyalty a player has seen of a village that is not his (02-game-rules §10): the battle
 * writes it, the map reads it ({@code loyalty_sighting}). Plain SQL, one single-statement upsert.
 */
@Component
public class LoyaltySightings {

    private final JdbcClient jdbc;

    LoyaltySightings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Remembers {@code loyalty} (rounded down) as what {@code playerId} saw of the village at
     * {@code seenAt}. An older sighting never replaces a newer one.
     */
    public void record(long playerId, long villageId, int loyalty, Instant seenAt) {
        jdbc.sql(
                        """
                        insert into loyalty_sighting (player_id, village_id, loyalty, seen_at)
                        values (:player, :village, :loyalty, :seenAt)
                        on conflict (player_id, village_id) do update
                           set loyalty = excluded.loyalty, seen_at = excluded.seen_at
                         where loyalty_sighting.seen_at <= excluded.seen_at
                        """)
                .param("player", playerId)
                .param("village", villageId)
                .param("loyalty", loyalty)
                .param("seenAt", Timestamp.from(seenAt))
                .update();
    }
}
