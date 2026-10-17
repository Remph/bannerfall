package de.bannerfall.victory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the host takes the end of a world back (v1.3, 02-game-rules §22.4), the world picks up
 * exactly where it stopped: every game clock moves on by the time it stood frozen. Attacks keep the
 * travel time they had left, builds, recruits, dismissals and researches their remaining time, the
 * pending events (NPC rounds, barbarian growth, the victory check) their distance from now;
 * villages get no resources and no loyalty for the frozen time; beginner protection and the
 * cool-downs (banner chances, battle credits) keep what they had left.
 *
 * <p>Moving every row by the same amount keeps each one's distance from "now" what it was from the
 * end, so rows long in the past stay in the past. History (reports, messages, chat, when something
 * was created) keeps its real time.
 */
@Component
class FrozenTime {

    /** Each statement moves the game clocks of one table; {@code :secs} is the frozen time. */
    private static final List<String> SHIFTS =
            List.of(
                    "update game_event set due_at = due_at + make_interval(secs => :secs)"
                            + " where status = 'PENDING'",
                    "update build_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update recruit_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update dismiss_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update research_order set start_at = start_at + make_interval(secs => :secs),"
                            + " end_at = end_at + make_interval(secs => :secs)",
                    "update movement set depart_at = depart_at + make_interval(secs => :secs),"
                            + " arrive_at = arrive_at + make_interval(secs => :secs)",
                    "update trade_transport"
                            + " set depart_at = depart_at + make_interval(secs => :secs),"
                            + " arrive_at = arrive_at + make_interval(secs => :secs),"
                            + " return_at = return_at + make_interval(secs => :secs)",
                    // never past now: a village caught up after the end must not stand still
                    "update village set resources_updated_at ="
                            + " least(resources_updated_at + make_interval(secs => :secs), :now),"
                            + " loyalty_updated_at ="
                            + " least(loyalty_updated_at + make_interval(secs => :secs), :now)",
                    "update player set protected_until ="
                            + " protected_until + make_interval(secs => :secs)",
                    "update banner_roll set rolled_at = rolled_at + make_interval(secs => :secs)",
                    "update battle_credit set counted_at = counted_at + make_interval(secs =>"
                            + " :secs)",
                    "update loyalty_sighting set seen_at = seen_at + make_interval(secs => :secs)");

    private final JdbcClient jdbc;

    FrozenTime(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Moves the game clocks on by the time from {@code endedAt} to {@code now}. */
    @Transactional(propagation = Propagation.MANDATORY)
    void resume(Instant endedAt, Instant now) {
        Duration frozen = Duration.between(endedAt, now);
        if (frozen.isNegative() || frozen.isZero()) {
            return;
        }
        double secs = frozen.toNanos() / 1e9;
        for (String sql : SHIFTS) {
            jdbc.sql(sql).param("secs", secs).param("now", Timestamp.from(now)).update();
        }
    }
}
