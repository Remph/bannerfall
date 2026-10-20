package de.bannerfall.npc;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NpcBrainRepository extends JpaRepository<NpcBrain, Long> {

    /**
     * How many attacks a lord sent to one player (09 §6, the caps of all lords together). Boxed
     * types: Hibernate can't build this projection of a native query with {@code long} and {@code
     * int}, and the lord's whole round would fail.
     */
    interface AttacksOnPlayer {
        Long getTarget();

        Integer getAttacks();
    }

    /**
     * The attacks the other lords sent since {@code since}, per target player: read from their
     * memories ({@link NpcMemory#attacksSent()}) in the database, without loading every brain.
     */
    @Query(
            nativeQuery = true,
            value =
                    """
                    select cast(sent ->> 'targetPlayerId' as bigint) as target,
                           cast(count(*) as int) as attacks
                    from npc_brain b
                    cross join lateral jsonb_array_elements(b.memory -> 'attacksSent') sent
                    where b.player_id <> :lord and cast(sent ->> 'at' as timestamptz) > :since
                    group by 1
                    """)
    List<AttacksOnPlayer> attacksSentByOthersSince(long lord, Instant since);
}
