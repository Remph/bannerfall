package de.bannerfall.admin;

import de.bannerfall.abuse.AbuseService;
import de.bannerfall.abuse.InvariantCheck;
import de.bannerfall.admin.AdminStatusDto.AbuseStatusDto;
import de.bannerfall.admin.AdminStatusDto.EventQueueDto;
import de.bannerfall.admin.AdminStatusDto.PushStatusDto;
import de.bannerfall.common.AppVersion;
import de.bannerfall.push.PushSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** The status screen of the host console (08-host-console §2). */
@Service
class AdminStatusService {

    private static final Logger log = LoggerFactory.getLogger(AdminStatusService.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final String version;
    private final boolean cheatsEnabled;
    private final Instant startedAt;
    private final AbuseService abuseService;
    private final InvariantCheck invariants;
    private final PushSettings pushSettings;
    private final DebugLogging debugLogging;

    AdminStatusService(
            JdbcClient jdbc,
            Clock clock,
            AppVersion appVersion,
            @Value("${bannerfall.cheats.enabled:false}") boolean cheatsEnabled,
            AbuseService abuseService,
            InvariantCheck invariants,
            PushSettings pushSettings,
            DebugLogging debugLogging) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.version = appVersion.get();
        this.cheatsEnabled = cheatsEnabled;
        this.startedAt = clock.instant();
        this.abuseService = abuseService;
        this.invariants = invariants;
        this.pushSettings = pushSettings;
        this.debugLogging = debugLogging;
    }

    /** No transaction: a database that is down must still give an answer (with zeros). */
    AdminStatusDto status() {
        Instant now = clock.instant();
        long uptime = Math.max(0, Duration.between(startedAt, now).toSeconds());
        try {
            jdbc.sql("select 1").query(Integer.class).single();
            return new AdminStatusDto(
                    version,
                    now,
                    uptime,
                    true,
                    count(
                            "select count(*) from player p join account a on a.id = p.account_id"
                                    + " where not a.disabled"),
                    count("select count(*) from village"),
                    count("select count(*) from village where player_id is null"),
                    count("select count(*) from player where npc and village_count > 0"),
                    events(now),
                    cheatsEnabled,
                    abuse(now),
                    new PushStatusDto(
                            pushSettings.enabled(),
                            count("select count(*) from push_subscription"),
                            count("select count(distinct player_id) from push_subscription")),
                    debugLogging.enabled());
        } catch (DataAccessException e) {
            log.warn("Status: the database doesn't answer: {}", e.getMessage());
            return new AdminStatusDto(
                    version,
                    now,
                    uptime,
                    false,
                    0,
                    0,
                    0,
                    0,
                    new EventQueueDto(0, null, 0, 0),
                    cheatsEnabled,
                    new AbuseStatusDto(0, 0, 0, invariants.last()),
                    new PushStatusDto(pushSettings.enabled(), 0, 0),
                    debugLogging.enabled());
        }
    }

    private AbuseStatusDto abuse(Instant now) {
        Timestamp dayAgo = Timestamp.from(now.minus(Duration.ofDays(1)));
        return new AbuseStatusDto(
                jdbc.sql("select count(*) from account where banned_until > ?")
                        .param(Timestamp.from(now))
                        .query(Long.class)
                        .single(),
                jdbc.sql("select count(distinct account_id) from abuse_event where at >= ?")
                        .param(dayAgo)
                        .query(Long.class)
                        .single(),
                abuseService.autoLocksLastDay(),
                invariants.last());
    }

    private EventQueueDto events(Instant now) {
        return jdbc.sql(
                        """
                        select count(*) filter (where status = 'PENDING') as pending,
                               min(due_at) filter (where status = 'PENDING') as next_due,
                               count(*) filter (where status = 'FAILED') as failed
                        from game_event where status in ('PENDING', 'FAILED')
                        """)
                .query(
                        (rs, row) -> {
                            Timestamp next = rs.getTimestamp("next_due");
                            Instant nextDue = next == null ? null : next.toInstant();
                            long lag =
                                    nextDue == null || !nextDue.isBefore(now)
                                            ? 0
                                            : Duration.between(nextDue, now).toSeconds();
                            return new EventQueueDto(
                                    rs.getLong("pending"), nextDue, lag, rs.getLong("failed"));
                        })
                .single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
