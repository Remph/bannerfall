package de.bannerfall.npc;

import de.bannerfall.gamedata.ResourceAmounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The help requests of allied lords (2.1 S4, table {@code npc_help_request}): one open request per
 * lord. Every method runs inside the caller's transaction.
 */
@Component
public class NpcHelpStore {

    private final JdbcClient jdbc;

    NpcHelpStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One request.
     *
     * @param attackId the attack a request for support is about (a movement id); null otherwise
     * @param defenders the defenders a request for support asks for; 0 otherwise
     * @param goal {@code NOBLE} or {@code WALL} for a request for resources; null otherwise
     * @param asked the resources a request for resources asks for; zero otherwise
     * @param delivered what the human's merchants delivered there since the request
     * @param thanksDue answered in time: the lord's next round thanks the human
     * @param thankedAt when the lord last sent a thank-you shipment for it; null if none
     */
    public record Request(
            long id,
            long lordId,
            long playerId,
            NpcHelpKind kind,
            long villageId,
            Long attackId,
            int defenders,
            String goal,
            ResourceAmounts asked,
            ResourceAmounts delivered,
            Instant createdAt,
            Instant deadline,
            NpcHelpState state,
            boolean thanksDue,
            Instant thankedAt,
            boolean supported) {}

    private static final String SELECT =
            """
            select id, lord_id, player_id, kind, village_id, attack_id, defenders, goal, wood, clay,
                   iron, delivered_wood, delivered_clay, delivered_iron, created_at, deadline, state,
                   thanks_due, thanked_at, supported
            from npc_help_request
            """;

    private Request map(ResultSet rs, int n) throws SQLException {
        return new Request(
                rs.getLong("id"),
                rs.getLong("lord_id"),
                rs.getLong("player_id"),
                NpcHelpKind.valueOf(rs.getString("kind")),
                rs.getLong("village_id"),
                rs.getObject("attack_id", Long.class),
                rs.getInt("defenders"),
                rs.getString("goal"),
                new ResourceAmounts(rs.getInt("wood"), rs.getInt("clay"), rs.getInt("iron")),
                new ResourceAmounts(
                        rs.getInt("delivered_wood"),
                        rs.getInt("delivered_clay"),
                        rs.getInt("delivered_iron")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("deadline").toInstant(),
                NpcHelpState.valueOf(rs.getString("state")),
                rs.getBoolean("thanks_due"),
                instant(rs.getTimestamp("thanked_at")),
                rs.getBoolean("supported"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** The lord's open request, if it has one. */
    Optional<Request> openOf(long lordId) {
        return jdbc.sql(SELECT + " where lord_id = :lord and state = 'OPEN'")
                .param("lord", lordId)
                .query(this::map)
                .optional();
    }

    /** The open requests to the human, nearest deadline first. */
    public List<Request> openFor(long playerId) {
        return jdbc.sql(
                        SELECT
                                + " where player_id = :player and state = 'OPEN' order by deadline,"
                                + " id")
                .param("player", playerId)
                .query(this::map)
                .list();
    }

    /** The request by id. */
    Optional<Request> find(long id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query(this::map).optional();
    }

    /** The request by id, locked for this transaction. */
    Optional<Request> lock(long id) {
        return jdbc.sql(SELECT + " where id = :id for update")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    /** The human's open requests at a village, locked (a battle or a delivery there). */
    List<Request> lockOpenAt(long villageId, long playerId) {
        return jdbc.sql(
                        SELECT
                                + " where village_id = :village and player_id = :player and state"
                                + " = 'OPEN' order by id for update")
                .param("village", villageId)
                .param("player", playerId)
                .query(this::map)
                .list();
    }

    /** Requests the human got from all lords since {@code since} (the daily cap). */
    int countSince(long playerId, Instant since) {
        return jdbc.sql(
                        "select count(*) from npc_help_request where player_id = :player and"
                                + " created_at > :since")
                .param("player", playerId)
                .param("since", Timestamp.from(since))
                .query(Integer.class)
                .single();
    }

    /** A new open request; returns its id. */
    long insert(
            long lordId,
            long playerId,
            NpcHelpKind kind,
            long villageId,
            Long attackId,
            int defenders,
            String goal,
            ResourceAmounts asked,
            Instant now,
            Instant deadline) {
        return jdbc.sql(
                        """
                        insert into npc_help_request
                            (lord_id, player_id, kind, village_id, attack_id, defenders, goal,
                             wood, clay, iron, created_at, deadline, state)
                        values (:lord, :player, :kind, :village, :attack, :defenders, :goal,
                                :wood, :clay, :iron, :now, :deadline, 'OPEN')
                        returning id
                        """)
                .param("lord", lordId)
                .param("player", playerId)
                .param("kind", kind.name())
                .param("village", villageId)
                .param("attack", attackId)
                .param("defenders", defenders)
                .param("goal", goal)
                .param("wood", asked.wood())
                .param("clay", asked.clay())
                .param("iron", asked.iron())
                .param("now", Timestamp.from(now))
                .param("deadline", Timestamp.from(deadline))
                .query(Long.class)
                .single();
    }

    /** Ends a request; one answered in time waits for the lord's thanks. */
    void close(long id, NpcHelpState state, Instant now) {
        jdbc.sql(
                        """
                        update npc_help_request set state = :state, closed_at = :now,
                            thanks_due = :thanks
                        where id = :id
                        """)
                .param("state", state.name())
                .param("now", Timestamp.from(now))
                .param("thanks", state == NpcHelpState.ANSWERED)
                .param("id", id)
                .update();
    }

    /** Adds a delivery of the human's merchants. */
    /** A support of the human arrived after the request, by its deadline. */
    void markSupported(long id) {
        jdbc.sql("update npc_help_request set supported = true where id = :id")
                .param("id", id)
                .update();
    }

    void addDelivered(long id, ResourceAmounts amounts) {
        jdbc.sql(
                        """
                        update npc_help_request set delivered_wood = delivered_wood + :wood,
                            delivered_clay = delivered_clay + :clay,
                            delivered_iron = delivered_iron + :iron
                        where id = :id
                        """)
                .param("wood", amounts.wood())
                .param("clay", amounts.clay())
                .param("iron", amounts.iron())
                .param("id", id)
                .update();
    }

    /** The lord thanked; {@code shippedAt} is set when a thank-you shipment went with it. */
    void thanked(long id, Instant shippedAt) {
        jdbc.sql(
                        "update npc_help_request set thanks_due = false, thanked_at = :at where id"
                                + " = :id")
                .param("at", shippedAt == null ? null : Timestamp.from(shippedAt))
                .param("id", id)
                .update();
    }

    /** When the lord last sent the human a thank-you shipment; null if never. */
    Instant lastThanks(long lordId, long playerId) {
        return jdbc.sql(
                        "select max(thanked_at) from npc_help_request where lord_id = :lord and"
                                + " player_id = :player")
                .param("lord", lordId)
                .param("player", playerId)
                .query((rs, n) -> Optional.ofNullable(instant(rs.getTimestamp(1))))
                .single()
                .orElse(null);
    }
}
