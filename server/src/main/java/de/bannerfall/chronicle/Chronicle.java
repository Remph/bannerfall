package de.bannerfall.chronicle;

import de.bannerfall.world.WorldPhase;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the world chronicle as it happens (2.1 I1, 02-game-rules §32): the moments it may tell
 * ({@code chronicle_event}) and who owned which village after each day ({@code chronicle_frame}).
 * Every write joins the caller's transaction, so a moment is kept exactly when what it tells is.
 * Depends on nothing of the game but the world settings, so every feature can call it.
 */
@Component
public class Chronicle {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final Clock clock;

    Chronicle(JdbcClient jdbc, WorldService worldService, Clock clock) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.clock = clock;
    }

    /**
     * Keeps a moment that happened at {@code gameAt} (game time: it decides the day; the row keeps
     * the real time as history).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ChronicleKind kind, Instant gameAt, ChronicleFacts facts) {
        jdbc.sql(
                        """
                        insert into chronicle_event (at, day, kind, data)
                        values (:at, :day, :kind, cast(:data as jsonb))
                        """)
                .param("at", Timestamp.from(clock.instant()))
                .param("day", dayOf(gameAt))
                .param("kind", kind.name())
                .param("data", JSON.writeValueAsString(facts))
                .update();
    }

    /** {@link #record} at the current game time. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordNow(ChronicleKind kind, ChronicleFacts facts) {
        record(kind, worldService.gameTime(clock.instant()), facts);
    }

    /**
     * The game day {@code gameAt} falls on: 1 for the first day ÷ speed after the opening, 0 before
     * it (or in the pre-game). The opening moves with the world's clock (FrozenTime), so the count
     * leaves out the time the world was asleep or ended.
     */
    public int dayOf(Instant gameAt) {
        Instant opened = worldService.initializedAt();
        if (opened == null || gameAt.isBefore(opened)) {
            return 0;
        }
        return (int) (Duration.between(opened, gameAt).toMillis() / dayMillis()) + 1;
    }

    /** Whole days ÷ speed since the opening at {@code gameAt}: the frame due then. */
    int daysDone(Instant gameAt) {
        return Math.max(0, dayOf(gameAt) - 1);
    }

    private long dayMillis() {
        return Math.max(
                1, Math.round(Duration.ofDays(1).toMillis() / worldService.settings().speed()));
    }

    /**
     * The frame of the day that is due (the check every few minutes): written once per day, by the
     * first check after it began, as the world is then. Nothing while the world isn't running.
     *
     * @return the day written, if one was
     */
    @Transactional
    public Optional<Integer> dailyFrame() {
        if (worldService.phase() != WorldPhase.OPEN
                || worldService.asleepSince() != null
                || worldService.endedAt() != null
                || worldService.initializedAt() == null) {
            return Optional.empty();
        }
        int day = daysDone(worldService.gameTime(clock.instant()));
        return frame(day, false) ? Optional.of(day) : Optional.empty();
    }

    /** The last frame, at the end of the world (written over an earlier one of that day). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void endFrame(Instant endedAt) {
        frame(dayOf(endedAt), true);
    }

    /** Writes the frame of {@code day} from the villages as they are now. */
    private boolean frame(int day, boolean replace) {
        String conflict =
                replace
                        ? """
                        do update set at = excluded.at, owners = excluded.owners,
                            players = excluded.players
                        """
                        : "do nothing";
        return jdbc.sql(
                                """
                                insert into chronicle_frame (day, at, owners, players)
                                select :day, :at,
                                """
                                        + ChronicleFrames.OWNERS
                                        + ", "
                                        + ChronicleFrames.PLAYERS
                                        + " on conflict (day) "
                                        + conflict)
                        .param("day", day)
                        .param("at", Timestamp.from(clock.instant()))
                        .update()
                > 0;
    }

    /** A player with his guild now; null for no id or a player that is gone. */
    public ChroniclePlayer player(Long playerId) {
        if (playerId == null) {
            return null;
        }
        return jdbc.sql(
                        """
                        select p.id, p.name, p.npc, g.id as guild_id, g.tag
                        from player p
                        left join guild_member m on m.player_id = p.id
                        left join guild g on g.id = m.guild_id
                        where p.id = :id
                        """)
                .param("id", playerId)
                .query(
                        (rs, row) ->
                                new ChroniclePlayer(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getBoolean("npc"),
                                        rs.getObject("guild_id", Long.class),
                                        rs.getString("tag")))
                .optional()
                .orElse(null);
    }

    /** A guild as it is now; null if it is gone. */
    public ChronicleSide guild(long guildId) {
        return jdbc.sql("select id, name, tag from guild where id = :id")
                .param("id", guildId)
                .query(
                        (rs, row) ->
                                ChronicleSide.guild(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getString("tag")))
                .optional()
                .orElse(null);
    }

    /** The guild a player is in now; null without one. */
    public ChronicleSide guildOf(long playerId) {
        return jdbc.sql(
                        """
                        select g.id, g.name, g.tag from guild g
                        join guild_member m on m.guild_id = g.id where m.player_id = :player
                        """)
                .param("player", playerId)
                .query(
                        (rs, row) ->
                                ChronicleSide.guild(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getString("tag")))
                .optional()
                .orElse(null);
    }

    /** Whether a human is a member of the guild (the chronicle tells only the humans' guilds). */
    public boolean hasHumans(long guildId) {
        return jdbc.sql(
                        """
                        select exists (select 1 from guild_member m join player p on p.id = m.player_id
                                       where m.guild_id = :guild and not p.npc)
                        """)
                .param("guild", guildId)
                .query(Boolean.class)
                .single();
    }

    /** A village by its id as it is now; null if it is gone. */
    public ChronicleVillage village(long villageId) {
        return jdbc.sql("select id, name, x, y from village where id = :id")
                .param("id", villageId)
                .query(
                        (rs, row) ->
                                new ChronicleVillage(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getInt("x"),
                                        rs.getInt("y")))
                .optional()
                .orElse(null);
    }
}
