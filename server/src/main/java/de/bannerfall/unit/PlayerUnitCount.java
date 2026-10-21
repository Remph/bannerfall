package de.bannerfall.unit;

import de.bannerfall.gamedata.UnitType;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How many units of one type a player owns over all his villages: at home or in a support (stacks),
 * on the way (movements), in training (recruit orders) and waiting to be dismissed. The paladin
 * slots (02-game-rules §5.5) and the noble rank (§5.7) count this way.
 */
@Service
public class PlayerUnitCount {

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    PlayerUnitCount(JdbcClient jdbc, EntityManager entityManager, Clock clock) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Read in one statement: the database runs at READ COMMITTED, so separate sums could each see
     * their own moment and miss a unit that moves between them (from the recruit queue into a
     * stack, from a movement into a stack). A dismissal frees its units by the clock
     * (RecruitRules.finishedBy), not when the village is caught up next.
     */
    @Transactional(readOnly = true)
    public int owned(long playerId, UnitType type) {
        // plain SQL doesn't flush by itself: an order or stack this transaction just changed would
        // still be pending
        entityManager.flush();
        // the column comes from the enum, never from a request
        String column = "u_" + type.name().toLowerCase(Locale.ROOT);
        long owned =
                jdbc.sql(
                                """
                                select
                                  (select coalesce(sum(s.%1$s), 0) from unit_stack s
                                     join village v on v.id = s.home_village_id
                                     where v.player_id = :player)
                                + (select coalesce(sum(m.%1$s), 0) from movement m
                                     join village v on v.id = m.home_village_id
                                     where v.player_id = :player)
                                + (select coalesce(sum(o.count - o.completed_count), 0)
                                     from recruit_order o join village v on v.id = o.village_id
                                     where v.player_id = :player and o.unit = :unit)
                                + (select coalesce(sum(o.count - least(o.count,
                                       greatest(0, floor(extract(epoch from
                                         (cast(:now as timestamptz) - o.start_at))))::bigint
                                       / o.seconds_per_unit)), 0)
                                     from dismiss_order o join village v on v.id = o.village_id
                                     where v.player_id = :player and o.unit = :unit)
                                """
                                        .formatted(column))
                        .param("player", playerId)
                        .param("unit", type.name())
                        .param("now", Timestamp.from(clock.instant()))
                        .query(Long.class)
                        .single();
        return Math.toIntExact(owned);
    }
}
