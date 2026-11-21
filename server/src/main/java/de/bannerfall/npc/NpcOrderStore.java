package de.bannerfall.npc;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.message.LordLetter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The orders of humans to their allied lords (2.1 S3, 12-solo-and-allies §8), table {@code
 * npc_order}: one row per lord, a new order replaces the old one.
 */
@Component
public class NpcOrderStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final TypeReference<Map<UnitType, Integer>> ARMY = new TypeReference<>() {};

    private final JdbcClient jdbc;

    NpcOrderStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One order.
     *
     * @param lordId the ally it is given to
     * @param playerId the human who gave it
     * @param answer the kind of the lord's last letter about it; null before it answered
     * @param sourceVillageId the lord's village that carries it out; null before
     * @param departAt a planned march leaves then (game time); null otherwise
     * @param arriveAt its troops or merchants arrive then (game time); null otherwise
     * @param army the units a planned march takes, or those it sent; null otherwise
     * @param scoutTries scoutings the lord sent for it
     */
    public record Order(
            long lordId,
            long playerId,
            NpcOrderKind kind,
            long targetVillageId,
            boolean landTogether,
            NpcOrderState state,
            LordLetter.Kind answer,
            Instant givenAt,
            Instant answeredAt,
            Long sourceVillageId,
            Instant departAt,
            Instant arriveAt,
            Map<UnitType, Integer> army,
            BuildingType catapultTarget,
            int scoutTries) {

        /** A new order, open until the lord's next round. */
        public static Order given(
                long lordId,
                long playerId,
                NpcOrderKind kind,
                long targetVillageId,
                boolean landTogether,
                Instant now) {
            return new Order(
                    lordId,
                    playerId,
                    kind,
                    targetVillageId,
                    landTogether,
                    NpcOrderState.OPEN,
                    null,
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0);
        }

        /** The lord answered with {@code letter} and the order is now in {@code next}. */
        Order answered(NpcOrderState next, LordLetter.Kind letter, Instant now) {
            return new Order(
                    lordId,
                    playerId,
                    kind,
                    targetVillageId,
                    landTogether,
                    next,
                    letter,
                    givenAt,
                    now,
                    sourceVillageId,
                    departAt,
                    arriveAt,
                    army,
                    catapultTarget,
                    scoutTries);
        }

        /** What the lord sends or plans to send, from where and when. */
        Order carried(
                Long source,
                Instant depart,
                Instant arrive,
                Map<UnitType, Integer> units,
                BuildingType catapults) {
            return new Order(
                    lordId,
                    playerId,
                    kind,
                    targetVillageId,
                    landTogether,
                    state,
                    answer,
                    givenAt,
                    answeredAt,
                    source,
                    depart,
                    arrive,
                    units,
                    catapults,
                    scoutTries);
        }

        /** One more scouting sent for it. */
        Order scouting() {
            return new Order(
                    lordId,
                    playerId,
                    kind,
                    targetVillageId,
                    landTogether,
                    NpcOrderState.SCOUTING,
                    answer,
                    givenAt,
                    answeredAt,
                    sourceVillageId,
                    departAt,
                    arriveAt,
                    army,
                    catapultTarget,
                    scoutTries + 1);
        }
    }

    public Optional<Order> find(long lordId) {
        return jdbc.sql("select * from npc_order where lord_id = :lord")
                .param("lord", lordId)
                .query(NpcOrderStore::row)
                .optional();
    }

    /**
     * The lord's order, its row locked until the transaction ends (the planned march and the lord's
     * answer).
     */
    public Optional<Order> lock(long lordId) {
        return jdbc.sql("select * from npc_order where lord_id = :lord for update")
                .param("lord", lordId)
                .query(NpcOrderStore::row)
                .optional();
    }

    /**
     * Locks the lord's order and tells whether it is still the one the lord read as {@code seen}:
     * the human can replace or take it back while the lord weighs it, and the lord's answer must
     * not overwrite the new order (or bring a taken back one back).
     */
    boolean lockUnchanged(Order seen) {
        return lock(seen.lordId())
                .filter(
                        now ->
                                now.playerId() == seen.playerId()
                                        && now.kind() == seen.kind()
                                        && now.targetVillageId() == seen.targetVillageId()
                                        && now.givenAt().equals(seen.givenAt())
                                        && now.state() == seen.state()
                                        && now.scoutTries() == seen.scoutTries())
                .isPresent();
    }

    /** The orders the human gave, by lord id. */
    public List<Order> ofPlayer(long playerId) {
        return jdbc.sql("select * from npc_order where player_id = :player order by lord_id")
                .param("player", playerId)
                .query(NpcOrderStore::row)
                .list();
    }

    /** Writes the order, replacing the lord's old one. */
    public void save(Order order) {
        jdbc.sql(
                        """
                        insert into npc_order (lord_id, player_id, kind, target_village_id,
                            land_together, state, answer, given_at, answered_at, source_village_id,
                            depart_at, arrive_at, army, catapult_target, scout_tries)
                        values (:lord, :player, :kind, :target, :land, :state, :answer, :given,
                            :answered, :source, :depart, :arrive, cast(:army as jsonb), :catapult,
                            :tries)
                        on conflict (lord_id) do update set player_id = excluded.player_id,
                            kind = excluded.kind, target_village_id = excluded.target_village_id,
                            land_together = excluded.land_together, state = excluded.state,
                            answer = excluded.answer, given_at = excluded.given_at,
                            answered_at = excluded.answered_at,
                            source_village_id = excluded.source_village_id,
                            depart_at = excluded.depart_at, arrive_at = excluded.arrive_at,
                            army = excluded.army, catapult_target = excluded.catapult_target,
                            scout_tries = excluded.scout_tries
                        """)
                .param("lord", order.lordId())
                .param("player", order.playerId())
                .param("kind", order.kind().name())
                .param("target", order.targetVillageId())
                .param("land", order.landTogether())
                .param("state", order.state().name())
                .param("answer", order.answer() == null ? null : order.answer().name())
                .param("given", timestamp(order.givenAt()))
                .param("answered", timestamp(order.answeredAt()))
                .param("source", order.sourceVillageId())
                .param("depart", timestamp(order.departAt()))
                .param("arrive", timestamp(order.arriveAt()))
                .param("army", order.army() == null ? null : JSON.writeValueAsString(order.army()))
                .param(
                        "catapult",
                        order.catapultTarget() == null ? null : order.catapultTarget().name())
                .param("tries", order.scoutTries())
                .update();
    }

    public boolean delete(long lordId) {
        return jdbc.sql("delete from npc_order where lord_id = :lord")
                        .param("lord", lordId)
                        .update()
                > 0;
    }

    private static Order row(ResultSet rs, int n) throws SQLException {
        String answer = rs.getString("answer");
        String army = rs.getString("army");
        String catapult = rs.getString("catapult_target");
        return new Order(
                rs.getLong("lord_id"),
                rs.getLong("player_id"),
                NpcOrderKind.valueOf(rs.getString("kind")),
                rs.getLong("target_village_id"),
                rs.getBoolean("land_together"),
                NpcOrderState.valueOf(rs.getString("state")),
                answer == null ? null : LordLetter.Kind.valueOf(answer),
                rs.getTimestamp("given_at").toInstant(),
                instant(rs.getTimestamp("answered_at")),
                rs.getObject("source_village_id", Long.class),
                instant(rs.getTimestamp("depart_at")),
                instant(rs.getTimestamp("arrive_at")),
                army == null ? null : armyOf(army),
                catapult == null ? null : BuildingType.valueOf(catapult),
                rs.getInt("scout_tries"));
    }

    private static Map<UnitType, Integer> armyOf(String json) {
        Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
        army.putAll(JSON.readValue(json, ARMY));
        return army;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
