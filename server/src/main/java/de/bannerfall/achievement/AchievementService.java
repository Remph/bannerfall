package de.bannerfall.achievement;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants achievements (02-game-rules §19). The game code calls it from inside its own transaction
 * at the place where the thing happens, so a rolled-back event grants nothing. All grants are
 * idempotent inserts; an achievement the host revoked ({@code achievement_block}) is skipped by the
 * same statement, and NPC lords, barbarians (null) and deleted players earn nothing. Honour only:
 * nothing here changes any game state.
 *
 * <p>Plain SQL on purpose: the tables are written and counted atomically ({@code on conflict}), and
 * no entity of the game (least of all the versioned {@code Player}) is touched, so granting never
 * causes an optimistic-lock failure of the battle or market it is called from.
 */
@Service
public class AchievementService {

    /** The window of the "once per village" filter is a day of game time (02 §19). */
    private static final Duration DAY = Duration.ofHours(24);

    /** An attack with fewer units than this doesn't count as a win (02 §19). */
    public static final int MIN_UNITS_FOR_WIN = 10;

    /** The Town Hall level that makes a Foreman (02 §19). */
    public static final int FOREMAN_LEVEL = 10;

    private final JdbcClient jdbc;
    private final PlayerRepository players;
    private final AchievementCatalog catalog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AchievementService(
            JdbcClient jdbc,
            PlayerRepository players,
            AchievementCatalog catalog,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.players = players;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    // ---- granting, called by the game code ----

    /** A one-time achievement. Nothing for NPC lords, barbarians (null) or a repeat. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grant(Player player, Achievement achievement) {
        if (!earns(player)) {
            return;
        }
        if (achievement.leveled()) {
            throw new IllegalArgumentException(achievement + " has levels");
        }
        insertLevel(player.getId(), achievement, 1);
    }

    /** Like {@link #grant(Player, Achievement)}, for a player known by id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grant(Long playerId, Achievement achievement) {
        if (playerId != null) {
            grant(players.findById(playerId).orElse(null), achievement);
        }
    }

    /**
     * Adds to the counter of a levelled achievement and grants the levels it reaches.
     *
     * @param delta more than 0
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void count(Player player, Achievement achievement, long delta) {
        if (!earns(player) || delta <= 0) {
            return;
        }
        long playerId = player.getId();
        // One statement: the row is created or raised atomically; a blocked achievement answers
        // nothing and is not counted.
        Long after =
                jdbc.sql(
                                """
                                insert into achievement_counter (player_id, achievement, value)
                                select :player, :key, :delta
                                where not exists (select 1 from achievement_block b
                                                  where b.player_id = :player and b.achievement = :key)
                                on conflict (player_id, achievement)
                                do update set value = achievement_counter.value + :delta
                                returning value
                                """)
                        .param("player", playerId)
                        .param("key", achievement.name())
                        .param("delta", delta)
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        if (after == null) {
            return;
        }
        int before = catalog.levelFor(achievement, after - delta);
        int now = catalog.levelFor(achievement, after);
        for (int level = before + 1; level <= now; level++) {
            insertLevel(playerId, achievement, level);
        }
    }

    /** Like {@link #count(Player, Achievement, long)}, for a player known by id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void count(Long playerId, Achievement achievement, long delta) {
        if (playerId != null) {
            count(players.findById(playerId).orElse(null), achievement, delta);
        }
    }

    /**
     * A levelled achievement that is read from the game's state (points, villages, days): grants
     * every level the value reaches that the player doesn't have yet. A level once earned stays
     * even if the value drops.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reach(Player player, Achievement achievement, long value) {
        if (!earns(player)) {
            return;
        }
        int reached = catalog.levelFor(achievement, value);
        if (reached == 0) {
            return;
        }
        long playerId = player.getId();
        int have = highestLevel(playerId, achievement);
        for (int level = have + 1; level <= reached; level++) {
            insertLevel(playerId, achievement, level);
        }
    }

    /** Growth and Landlord, after the player's points or villages changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkState(Player player) {
        if (!earns(player)) {
            return;
        }
        reach(player, Achievement.GROWTH, player.getPoints());
        reach(player, Achievement.LANDLORD, player.getVillageCount());
    }

    /**
     * The one-time achievements that the game's current state shows: Foreman (a Town Hall of level
     * 10), Scholar (an Academy) and Pioneer (registered before the world opened). They are granted
     * by their events as well; this is for a world that already ran when v1.1 came, where those
     * events lie in the past (02 §19, "start at deploy"). Not on the hot path of the points: login
     * and the achievements page call it. Guilds need no pass, they do not exist before v1.1.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkMilestones(Player player) {
        if (!earns(player)) {
            return;
        }
        long playerId = player.getId();
        boolean[] found =
                jdbc.sql(
                                """
                                select
                                  exists (select 1 from village v
                                          where v.player_id = :player
                                            and v.b_headquarters >= :foreman),
                                  exists (select 1 from village v
                                          where v.player_id = :player and v.b_academy >= 1),
                                  exists (select 1 from world_settings w
                                          where w.initialized_at is not null
                                            and :since < w.initialized_at)
                                """)
                        .param("player", playerId)
                        .param("foreman", FOREMAN_LEVEL)
                        .param("since", java.sql.Timestamp.from(player.getCreatedAt()))
                        .query(
                                (rs, row) ->
                                        new boolean[] {
                                            rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)
                                        })
                        .single();
        if (found[0]) {
            insertLevel(playerId, Achievement.FOREMAN, 1);
        }
        if (found[1]) {
            insertLevel(playerId, Achievement.SCHOLAR, 1);
        }
        if (found[2]) {
            insertLevel(playerId, Achievement.PIONEER, 1);
        }
    }

    /** Veteran: whole days (24 h of real time) since the player registered. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkVeteran(Player player) {
        if (!earns(player)) {
            return;
        }
        reach(player, Achievement.VETERAN, veteranDays(player));
    }

    /**
     * Banner Collector and Full Set (02-game-rules §21.8), read from the player's banners: how many
     * he owns now (stash and equipped) and how many of the nine types he has ever owned (the banner
     * log's lines that gave him a banner, and the banners he holds). The banner code calls it after
     * a banner came to the player; login and the achievements page call it too, which is the
     * back-fill on a world that had banners before M32c.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkBanners(Player player) {
        if (!earns(player)) {
            return;
        }
        long playerId = player.getId();
        // the count is plain SQL, which doesn't flush by itself: a stolen banner's new owner or
        // the three banners a fusing just deleted would still be pending and miscount
        players.flush();
        long[] found =
                jdbc.sql(
                                """
                                select
                                  (select count(*) from banner where owner_id = :player),
                                  (select count(distinct t.type) from (
                                     select type from banner where owner_id = :player
                                     union
                                     select type from banner_event
                                     where to_player_id = :player and type is not null) t)
                                """)
                        .param("player", playerId)
                        .query((rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)})
                        .single();
        reach(player, Achievement.BANNER_COLLECTOR, found[0]);
        if (found[1] >= BannerType.values().length) {
            insertLevel(playerId, Achievement.FULL_SET, 1);
        }
    }

    /** Like {@link #checkBanners(Player)}, for a player known by id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkBanners(Long playerId) {
        if (playerId != null) {
            checkBanners(players.findById(playerId).orElse(null));
        }
    }

    /**
     * A banner taken from another player's (or an NPC lord's) village, by thieves or by conquest
     * (02-game-rules §21.8): Banner Thief counts it once per banner, and not at all if the player
     * had that banner before (taking back your own counts for nothing). A fused banner has a new
     * id, so it counts as a new banner. Then {@link #checkBanners(Player)}.
     *
     * @param takeEventId the banner log line of this take, which is not "had it before"
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bannerTaken(Long playerId, long bannerId, long takeEventId) {
        Player player = playerId == null ? null : players.findById(playerId).orElse(null);
        if (!earns(player)) {
            return;
        }
        boolean hadIt =
                jdbc.sql(
                                """
                                select exists (select 1 from banner_event
                                               where banner_id = :banner and to_player_id = :player
                                                 and id <> :event)
                                """)
                        .param("banner", bannerId)
                        .param("player", playerId)
                        .param("event", takeEventId)
                        .query(Boolean.class)
                        .single();
        if (!hadIt) {
            count(player, Achievement.BANNER_THIEF, 1);
        }
        checkBanners(player);
    }

    /**
     * The "once per village and {@code 24 h ÷ speed}" filter for counted wins ({@code "WIN"}) and
     * counted defences ({@code "DEFENCE"}): true, and the time is remembered, if the attacker has
     * no counted one against this village within the window; false if he has (nothing changes).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean creditBattle(
            long attackerId, long villageId, String kind, Instant at, double speed) {
        long windowMillis = Math.max(1000, Math.round(DAY.toMillis() / speed));
        Instant cutoff = at.minusMillis(windowMillis);
        int changed =
                jdbc.sql(
                                """
                                insert into battle_credit (attacker_id, village_id, kind, counted_at)
                                values (:attacker, :village, :kind, :at)
                                on conflict (attacker_id, village_id, kind)
                                do update set counted_at = excluded.counted_at
                                where battle_credit.counted_at <= :cutoff
                                """)
                        .param("attacker", attackerId)
                        .param("village", villageId)
                        .param("kind", kind)
                        .param("at", java.sql.Timestamp.from(at))
                        .param("cutoff", java.sql.Timestamp.from(cutoff))
                        .update();
        return changed == 1;
    }

    /** At login: the state-based achievements (Veteran needs a visit to be noticed). */
    @Transactional
    public void checkAtLogin(long accountId) {
        Player player = players.findByAccountId(accountId).orElse(null);
        if (earns(player)) {
            checkState(player);
            checkVeteran(player);
            checkMilestones(player);
            checkBanners(player);
        }
    }

    // ---- reading ----

    /**
     * The player's own list: every achievement, earned or not, with progress. The state-based ones
     * (points, villages, days, Town Hall, Academy, Pioneer, banners) are checked first, so opening
     * the page is what grants Veteran.
     */
    @Transactional
    public AchievementsDto mine(long playerId) {
        Player player = players.findById(playerId).orElseThrow();
        checkState(player);
        checkVeteran(player);
        checkMilestones(player);
        checkBanners(player);
        Map<Achievement, int[]> levels = new HashMap<>();
        Map<Achievement, Instant> earnedAt = new HashMap<>();
        jdbc.sql(
                        "select achievement, level, earned_at from player_achievement"
                                + " where player_id = :player")
                .param("player", player.getId())
                .query(
                        (rs, row) -> {
                            Achievement a = achievementOf(rs.getString("achievement"));
                            if (a != null) {
                                int level = rs.getInt("level");
                                int[] best = levels.computeIfAbsent(a, k -> new int[1]);
                                if (level > best[0]) {
                                    best[0] = level;
                                    earnedAt.put(a, rs.getTimestamp("earned_at").toInstant());
                                }
                            }
                            return null;
                        })
                .list();
        Map<Achievement, Long> counters = new HashMap<>();
        jdbc.sql(
                        "select achievement, value from achievement_counter"
                                + " where player_id = :player")
                .param("player", player.getId())
                .query(
                        (rs, row) -> {
                            Achievement a = achievementOf(rs.getString("achievement"));
                            if (a != null) {
                                counters.put(a, rs.getLong("value"));
                            }
                            return null;
                        })
                .list();
        List<AchievementDto> list = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            int level = levels.getOrDefault(a, new int[1])[0];
            Long progress =
                    !a.leveled()
                            ? null
                            : switch (a) {
                                case GROWTH -> (long) player.getPoints();
                                case LANDLORD -> (long) player.getVillageCount();
                                case VETERAN -> veteranDays(player);
                                case BANNER_COLLECTOR -> ownedBanners(player.getId());
                                default -> counters.getOrDefault(a, 0L);
                            };
            list.add(
                    new AchievementDto(
                            a.name(),
                            a.category().name(),
                            a.leveled(),
                            level,
                            earnedAt.get(a),
                            a.leveled() ? catalog.thresholds(a) : List.of(),
                            progress));
        }
        return new AchievementsDto(list);
    }

    /**
     * What a player has earned, for his public profile: the highest level of each, in list order.
     */
    @Transactional(readOnly = true)
    public List<EarnedAchievementDto> earned(long playerId) {
        Map<Achievement, EarnedAchievementDto> best = new HashMap<>();
        jdbc.sql(
                        "select achievement, level, earned_at from player_achievement"
                                + " where player_id = :player")
                .param("player", playerId)
                .query(
                        (rs, row) -> {
                            Achievement a = achievementOf(rs.getString("achievement"));
                            int level = rs.getInt("level");
                            EarnedAchievementDto old = best.get(a);
                            if (a != null && (old == null || level > old.level())) {
                                best.put(
                                        a,
                                        new EarnedAchievementDto(
                                                a.name(),
                                                a.category().name(),
                                                a.leveled(),
                                                level,
                                                rs.getTimestamp("earned_at").toInstant()));
                            }
                            return null;
                        })
                .list();
        List<EarnedAchievementDto> list = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (best.containsKey(a)) {
                list.add(best.get(a));
            }
        }
        return list;
    }

    // ---- the host ----

    /**
     * Takes an achievement away for good (02 §19, host): every level and the counter go, and the
     * key is blocked for the player. Revoking again changes nothing and keeps the first reason.
     *
     * @return true if something was removed or the block is new
     */
    @Transactional
    public boolean revoke(long playerId, Achievement achievement, String reason) {
        int blocked =
                jdbc.sql(
                                """
                                insert into achievement_block (player_id, achievement, revoked_at, reason)
                                values (:player, :key, :at, :reason)
                                on conflict do nothing
                                """)
                        .param("player", playerId)
                        .param("key", achievement.name())
                        .param("at", java.sql.Timestamp.from(clock.instant()))
                        .param("reason", reason)
                        .update();
        int levels =
                jdbc.sql(
                                "delete from player_achievement"
                                        + " where player_id = :player and achievement = :key")
                        .param("player", playerId)
                        .param("key", achievement.name())
                        .update();
        jdbc.sql(
                        "delete from achievement_counter"
                                + " where player_id = :player and achievement = :key")
                .param("player", playerId)
                .param("key", achievement.name())
                .update();
        return blocked + levels > 0;
    }

    // ---- helpers ----

    private static boolean earns(Player player) {
        return player != null && player.getId() != null && !player.isNpc();
    }

    private long veteranDays(Player player) {
        Instant since = player.getCreatedAt();
        return since == null ? 0 : Math.max(0, Duration.between(since, clock.instant()).toDays());
    }

    private long ownedBanners(long playerId) {
        return jdbc.sql("select count(*) from banner where owner_id = :player")
                .param("player", playerId)
                .query(Long.class)
                .single();
    }

    private int highestLevel(long playerId, Achievement achievement) {
        return jdbc.sql(
                        "select coalesce(max(level), 0) from player_achievement"
                                + " where player_id = :player and achievement = :key")
                .param("player", playerId)
                .param("key", achievement.name())
                .query(Integer.class)
                .single();
    }

    /** Inserts one level unless it exists or the key is blocked; tells the player if it is new. */
    private void insertLevel(long playerId, Achievement achievement, int level) {
        int inserted =
                jdbc.sql(
                                """
                                insert into player_achievement (player_id, achievement, level, earned_at)
                                select :player, :key, :level, :at
                                where not exists (select 1 from achievement_block b
                                                  where b.player_id = :player and b.achievement = :key)
                                on conflict do nothing
                                """)
                        .param("player", playerId)
                        .param("key", achievement.name())
                        .param("level", level)
                        .param("at", java.sql.Timestamp.from(clock.instant()))
                        .update();
        if (inserted == 1) {
            events.publishEvent(
                    new PlayerNotification(
                            playerId,
                            new GameNotification.AchievementEarned(
                                    achievement.name(), achievement.leveled(), level)));
        }
    }

    private static Achievement achievementOf(String key) {
        try {
            return Achievement.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null; // a key from a newer version that was rolled back: ignore
        }
    }
}
