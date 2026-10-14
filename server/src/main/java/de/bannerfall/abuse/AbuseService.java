package de.bannerfall.abuse;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.account.AccountService;
import de.bannerfall.admin.AdminActor;
import de.bannerfall.admin.AdminAuditService;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.config.RequestPaths;
import de.bannerfall.player.PlayerService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The suspicion log (M14e, 01-architecture §4.12): records requests the web client never sends in
 * normal play ({@link AbuseKind}), lists them for the host, and locks an account for a while when
 * it collects too many of them in a short time (the automatic lock, {@code
 * bannerfall.abuse.auto-lock.*}).
 *
 * <p>Recording never fails the request it is about: it runs in its own transaction (the request's
 * may just have rolled back), any error is logged and swallowed. A script flooding the server can't
 * flood the table either: per account at most {@value #RECORD_BURST} events at once and one every
 * two seconds after that are written; rate-limit hits at most once a minute.
 *
 * <p>Requests under {@code /api/admin/**} are never recorded: those are the host's own.
 */
@Service
public class AbuseService {

    private static final Logger log = LoggerFactory.getLogger("bannerfall.abuse");

    /** How long events are kept. */
    static final Duration KEEP = Duration.ofDays(30);

    static final int RECORD_BURST = 30;
    private static final Duration RATE_LIMIT_EVENT_EVERY = Duration.ofMinutes(1);
    static final int RECENT_PER_ACCOUNT = 10;
    private static final int MAX_TEXT = 500;
    private static final int MAX_ENDPOINT = 200;

    private final JdbcClient jdbc;
    private final ForeignIds foreignIds;
    private final PlayerService playerService;
    private final AccountService accountService;
    private final AdminAuditService audit;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final TokenBuckets<Long> recordLimit;
    private final Map<Long, Instant> lastRateLimitEvent = new ConcurrentHashMap<>();
    private volatile AutoLock autoLock;

    /**
     * The automatic lock: {@code events} or more within {@code window} → banned for {@code
     * duration}. Admins are never locked; the host decides about anything longer.
     */
    record AutoLock(boolean enabled, int events, Duration window, Duration duration) {}

    AbuseService(
            JdbcClient jdbc,
            ForeignIds foreignIds,
            PlayerService playerService,
            AccountService accountService,
            AdminAuditService audit,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${bannerfall.abuse.auto-lock.enabled:true}") boolean autoLockEnabled,
            @Value("${bannerfall.abuse.auto-lock.events:20}") int autoLockEvents,
            @Value("${bannerfall.abuse.auto-lock.window:10m}") Duration autoLockWindow,
            @Value("${bannerfall.abuse.auto-lock.duration:1h}") Duration autoLockDuration) {
        this.jdbc = jdbc;
        this.foreignIds = foreignIds;
        this.playerService = playerService;
        this.accountService = accountService;
        this.audit = audit;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recordLimit = new TokenBuckets<>(clock, 0.5, RECORD_BURST);
        this.autoLock =
                new AutoLock(autoLockEnabled, autoLockEvents, autoLockWindow, autoLockDuration);
    }

    /**
     * A 404 of the API: recorded as {@link AbuseKind#FOREIGN_ID} if the id exists and belongs to
     * someone else ({@link ForeignIds}).
     */
    public void notFound(HttpServletRequest request, ErrorCode code) {
        Long accountId = currentAccountId();
        if (accountId == null || isAdminPath(request) || !fromTheGame(request)) {
            return;
        }
        guarded(
                () -> {
                    long playerId = playerService.idOf(accountId);
                    if (foreignIds.isForeign(code, request, playerId)) {
                        record(accountId, AbuseKind.FOREIGN_ID, endpoint(request), code.name());
                    }
                });
    }

    /** Something suspicious about the current request of a logged-in player. */
    public void suspect(HttpServletRequest request, AbuseKind kind, String detail) {
        Long accountId = currentAccountId();
        if (accountId != null && !isAdminPath(request) && fromTheGame(request)) {
            suspect(accountId, kind, endpoint(request), detail);
        }
    }

    /**
     * Whether the request came from the game's own pages, as far as the browser says ({@code
     * Sec-Fetch-*}; a browser that sends none counts as yes). The session cookie is SameSite=Lax,
     * so it also comes along when another site opens {@code /api/villages/abc} in a window: twenty
     * of those would have locked a victim out for an hour (code review 1.2). Such requests answer
     * as usual, they just aren't held against the player.
     */
    static boolean fromTheGame(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        String mode = request.getHeader("Sec-Fetch-Mode");
        return (site == null || site.equals("same-origin"))
                && (mode == null || !mode.equals("navigate"));
    }

    /** Something suspicious of the account (a filter that knows it already). */
    public void suspect(long accountId, AbuseKind kind, String endpoint, String detail) {
        if (kind == AbuseKind.RATE_LIMITED && !rateLimitEventDue(accountId)) {
            return;
        }
        guarded(() -> record(accountId, kind, endpoint, detail));
    }

    /** Something suspicious on a STOMP connection, whose user is the player. */
    public void suspectPlayer(long playerId, AbuseKind kind, String endpoint, String detail) {
        guarded(
                () ->
                        jdbc.sql("select account_id from player where id = ?")
                                .param(playerId)
                                .query(Long.class)
                                .optional()
                                .ifPresent(accountId -> record(accountId, kind, endpoint, detail)));
    }

    /**
     * Per account with events in the last {@code days} days, the most recent first: counts per kind
     * and the newest {@value #RECENT_PER_ACCOUNT} events.
     */
    public List<SuspiciousAccountDto> suspicious(int days) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(days));
        Map<Long, List<AbuseEventDto>> recent = new LinkedHashMap<>();
        jdbc.sql(
                        """
                        select id, account_id, kind, endpoint, detail, at from (
                            select e.*, row_number() over (
                                partition by account_id order by at desc, id desc) as n
                            from abuse_event e where at >= :since) ranked
                        where n <= :recent
                        order by at desc, id desc
                        """)
                .param("since", Timestamp.from(since))
                .param("recent", RECENT_PER_ACCOUNT)
                .query(
                        (rs, row) ->
                                recent.computeIfAbsent(
                                                rs.getLong("account_id"), id -> new ArrayList<>())
                                        .add(
                                                new AbuseEventDto(
                                                        rs.getLong("id"),
                                                        AbuseKind.valueOf(rs.getString("kind")),
                                                        rs.getString("endpoint"),
                                                        rs.getString("detail"),
                                                        rs.getTimestamp("at").toInstant())))
                .list();
        Map<Long, Map<AbuseKind, Long>> counts = new LinkedHashMap<>();
        jdbc.sql(
                        """
                        select account_id, kind, count(*) as n from abuse_event
                        where at >= :since group by account_id, kind
                        """)
                .param("since", Timestamp.from(since))
                .query(
                        (rs, row) ->
                                counts.computeIfAbsent(
                                                rs.getLong("account_id"),
                                                id -> new EnumMap<>(AbuseKind.class))
                                        .put(
                                                AbuseKind.valueOf(rs.getString("kind")),
                                                rs.getLong("n")))
                .list();
        List<SuspiciousAccountDto> result = new ArrayList<>();
        // recent is ordered by the newest event, so the accounts come most recent first
        for (var entry : recent.entrySet()) {
            long accountId = entry.getKey();
            Map<AbuseKind, Long> byKind = counts.getOrDefault(accountId, Map.of());
            jdbc.sql(
                            """
                            select p.id, p.name, a.banned_until, a.ban_reason
                            from account a join player p on p.account_id = a.id
                            where a.id = ?
                            """)
                    .param(accountId)
                    .query(
                            (rs, row) -> {
                                boolean banned = isAfter(rs.getTimestamp("banned_until"), now);
                                return new SuspiciousAccountDto(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        byKind,
                                        byKind.values().stream().mapToLong(Long::longValue).sum(),
                                        entry.getValue().getFirst().at(),
                                        banned ? rs.getTimestamp("banned_until").toInstant() : null,
                                        banned ? rs.getString("ban_reason") : null,
                                        entry.getValue());
                            })
                    .optional()
                    .ifPresent(result::add);
        }
        return result;
    }

    /** Accounts locked automatically in the last 24 hours (the status screen). */
    public long autoLocksLastDay() {
        return jdbc.sql("select count(*) from admin_action where action = 'AUTO_LOCK' and at >= ?")
                .param(Timestamp.from(clock.instant().minus(Duration.ofDays(1))))
                .query(Long.class)
                .single();
    }

    /** Every night at 03:40 UTC: events older than {@link #KEEP} go. */
    @Scheduled(cron = "0 40 3 * * *", zone = "UTC")
    public void cleanup() {
        int deleted = deleteOld();
        if (deleted > 0) {
            log.info("Deleted {} old suspicion events", deleted);
        }
    }

    int deleteOld() {
        return jdbc.sql("delete from abuse_event where at < ?")
                .param(Timestamp.from(clock.instant().minus(KEEP)))
                .update();
    }

    /** Other numbers for the automatic lock (tests). */
    public void configureAutoLock(boolean enabled, int events, Duration window, Duration duration) {
        this.autoLock = new AutoLock(enabled, events, window, duration);
    }

    private void record(long accountId, AbuseKind kind, String endpoint, String detail) {
        if (!recordLimit.tryTake(accountId)) {
            return;
        }
        Instant now = clock.instant();
        newTransaction.executeWithoutResult(
                status -> {
                    jdbc.sql(
                                    """
                                    insert into abuse_event (account_id, kind, endpoint, detail, at)
                                    values (?, ?, ?, ?, ?)
                                    """)
                            .params(
                                    accountId,
                                    kind.name(),
                                    clean(endpoint, MAX_ENDPOINT),
                                    clean(detail, MAX_TEXT),
                                    Timestamp.from(now))
                            .update();
                    if (autoLock.enabled()) {
                        lockIfTooMany(accountId, now);
                    }
                });
        log.info("Suspicious: account {} {} {} ({})", accountId, kind, endpoint, detail);
    }

    /** The automatic lock; runs in the transaction of the event that may trigger it. */
    private void lockIfTooMany(long accountId, Instant now) {
        long recent =
                jdbc.sql("select count(*) from abuse_event where account_id = ? and at > ?")
                        .params(accountId, Timestamp.from(now.minus(autoLock.window())))
                        .query(Long.class)
                        .single();
        if (recent < autoLock.events()) {
            return;
        }
        var target =
                jdbc.sql(
                                """
                                select p.id as player_id, a.username, a.role, a.disabled,
                                       a.banned_until
                                from account a join player p on p.account_id = a.id
                                where a.id = ? for update of a
                                """)
                        .param(accountId)
                        .query(
                                (rs, row) ->
                                        new LockTarget(
                                                rs.getLong("player_id"),
                                                rs.getString("username"),
                                                "ADMIN".equals(rs.getString("role"))
                                                        || rs.getBoolean("disabled")
                                                        || isAfter(
                                                                rs.getTimestamp("banned_until"),
                                                                now)))
                        .optional();
        if (target.isEmpty() || target.get().exempt()) {
            return;
        }
        Instant until = now.plus(autoLock.duration());
        String reason =
                "Automatic lock: "
                        + recent
                        + " suspicious requests within "
                        + autoLock.window().toMinutes()
                        + " minutes";
        accountService.ban(accountId, until, reason);
        audit.recordAs(
                AdminActor.AUTO_LOCK,
                "AUTO_LOCK",
                "player",
                target.get().playerId(),
                Map.of("name", target.get().name(), "events", recent, "until", until.toString()));
        log.warn(
                "Account '{}' locked until {}: {} suspicious requests within {}",
                target.get().name(),
                until,
                recent,
                autoLock.window());
    }

    /**
     * @param exempt an admin, deleted, or banned already
     */
    private record LockTarget(long playerId, String name, boolean exempt) {}

    private boolean rateLimitEventDue(long accountId) {
        Instant now = clock.instant();
        boolean[] due = {false};
        lastRateLimitEvent.compute(
                accountId,
                (id, last) -> {
                    if (last == null || !now.isBefore(last.plus(RATE_LIMIT_EVENT_EVERY))) {
                        due[0] = true;
                        return now;
                    }
                    return last;
                });
        if (lastRateLimitEvent.size() > 10_000) {
            lastRateLimitEvent.clear();
        }
        return due[0];
    }

    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Could not record a suspicious request: {}", e.toString());
        }
    }

    private static Long currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.accountId();
        }
        return null;
    }

    private static boolean isAdminPath(HttpServletRequest request) {
        return RequestPaths.of(request).startsWith("/api/admin/");
    }

    static String endpoint(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /** Without control characters, cut to {@code max} characters. */
    private static String clean(String text, int max) {
        String cleaned = text == null ? "" : text.replaceAll("\\p{Cntrl}", " ");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static boolean isAfter(Timestamp timestamp, Instant now) {
        return timestamp != null && timestamp.toInstant().isAfter(now);
    }
}
