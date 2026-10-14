package de.bannerfall.security;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;

import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Database shortcuts for the hostile tests: they set up state the API would need long for. */
final class Fixture {

    private Fixture() {}

    /** Plenty of resources and the buildings every endpoint needs. */
    static void richVillage(JdbcClient jdbc, long villageId) {
        jdbc.sql(
                        """
                        update village set wood = 50000, clay = 50000, iron = 50000,
                               b_warehouse = 20, b_farm = 20, b_barracks = 5, b_market = 5
                        where id = ?
                        """)
                .param(villageId)
                .update();
    }

    static void setResources(JdbcClient jdbc, long villageId, int each) {
        jdbc.sql("update village set wood = ?, clay = ?, iron = ? where id = ?")
                .params(each, each, each, villageId)
                .update();
    }

    static void setHomeUnits(JdbcClient jdbc, long villageId, String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, villageId, villageId)
                .update();
    }

    /** A support stack of {@code home}'s spearmen standing in {@code location}. */
    static long supportStack(JdbcClient jdbc, long home, long location, int spearmen) {
        return jdbc.sql(
                        """
                        insert into unit_stack (home_village_id, location_village_id, u_spearman)
                        values (?, ?, ?) returning id
                        """)
                .params(home, location, spearmen)
                .query(Long.class)
                .single();
    }

    /** A report of the player (its payload is never read in these tests). */
    static long report(JdbcClient jdbc, long playerId, Instant at) {
        return jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at)
                        values (?, 'SUPPORT_ARRIVED', ?,
                                '{"type":"SUPPORT","from":null,"to":null,"units":{}}'::jsonb, ?)
                        returning id
                        """)
                .params(playerId, Timestamp.from(at), Timestamp.from(at))
                .query(Long.class)
                .single();
    }

    static void endProtection(JdbcClient jdbc, long playerId, Instant at) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(at), playerId)
                .update();
    }

    /** A barbarian village the test put on the map. */
    record Spot(long id, Coordinates at) {}

    /**
     * {@code count} barbarian villages on free fields of a row near the bottom of the test map (far
     * from the spawn ring and the corners; other tests place barbarians at random, so a field may
     * be taken and is skipped). The test deletes them again with {@link #removeSpots}.
     */
    static List<Spot> barbarianSpots(JdbcClient jdbc, int count) {
        int y = TestWorldConfiguration.MAP_SIZE - 12;
        List<Spot> spots = new ArrayList<>();
        try {
            for (int x = 20; spots.size() < count && x < TestWorldConfiguration.MAP_SIZE; x++) {
                int field = x;
                jdbc.sql(
                                """
                                insert into village (player_id, name, x, y, wood, clay, iron,
                                                     resources_updated_at, loyalty_updated_at,
                                                     points, created_at)
                                values (null, null, :x, :y, 0, 0, 0, now(), now(), 0, now())
                                on conflict (x, y) do nothing
                                returning id
                                """)
                        .param("x", field)
                        .param("y", y)
                        .query(Long.class)
                        .optional()
                        .ifPresent(id -> spots.add(new Spot(id, new Coordinates(field, y))));
            }
        } catch (RuntimeException e) {
            removeSpots(jdbc, spots);
            throw e;
        }
        if (spots.size() < count) {
            removeSpots(jdbc, spots);
            throw new IllegalStateException("No room for " + count + " villages in the row");
        }
        return spots;
    }

    /** Deletes the spots (their farm list entries go with them). */
    static void removeSpots(JdbcClient jdbc, List<Spot> spots) {
        for (Spot spot : spots) {
            jdbc.sql("delete from village where id = ?").param(spot.id()).update();
        }
    }

    /** Puts the spots on the village's farm list with 1 spearman each, straight in the table. */
    static void fillFarmList(JdbcClient jdbc, long villageId, List<Spot> spots) {
        for (Spot spot : spots) {
            jdbc.sql(
                            """
                            insert into farm_target (village_id, target_village_id, u_spearman,
                                u_swordsman, u_axeman, u_scout, u_light_cavalry, u_heavy_cavalry,
                                u_ram, u_catapult, u_noble, created_at)
                            values (?, ?, 1, 0, 0, 0, 0, 0, 0, 0, 0, now())
                            """)
                    .params(villageId, spot.id())
                    .update();
        }
    }

    static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    static long accountId(JdbcClient jdbc, long playerId) {
        return jdbc.sql("select account_id from player where id = ?")
                .param(playerId)
                .query(Long.class)
                .single();
    }

    static String username(JdbcClient jdbc, long playerId) {
        return jdbc.sql("select name from player where id = ?")
                .param(playerId)
                .query(String.class)
                .single();
    }

    /** How many suspicion events of this kind the player's account has. */
    static long abuseEvents(JdbcClient jdbc, long playerId, String kind) {
        return jdbc.sql(
                        """
                        select count(*) from abuse_event e join player p on p.account_id = e.account_id
                        where p.id = ? and e.kind = ?
                        """)
                .params(playerId, kind)
                .query(Long.class)
                .single();
    }

    /**
     * Everything of the village and its player that a request could change, as one text: equal
     * before and after = nothing changed.
     */
    static String fingerprint(JdbcClient jdbc, long villageId, long playerId) {
        return jdbc.sql(
                        """
                        select concat_ws(' | ',
                            (select row(v.*)::text from village v where v.id = :v),
                            (select row(p.*)::text from player p where p.id = :p),
                            (select string_agg(row(s.*)::text, ',' order by s.id) from unit_stack s
                                where s.home_village_id = :v or s.location_village_id = :v),
                            (select string_agg(row(b.*)::text, ',' order by b.id) from build_order b
                                where b.village_id = :v),
                            (select string_agg(row(r.*)::text, ',' order by r.id) from recruit_order r
                                where r.village_id = :v),
                            (select string_agg(row(d.*)::text, ',' order by d.id) from dismiss_order d
                                where d.village_id = :v),
                            (select string_agg(row(f.*)::text, ',' order by f.id) from farm_target f
                                where f.village_id = :v),
                            (select string_agg(row(m.*)::text, ',' order by m.id) from movement m
                                where m.home_village_id = :v or m.target_village_id = :v),
                            (select string_agg(row(o.*)::text, ',' order by o.id) from trade_offer o
                                where o.village_id = :v),
                            (select string_agg(row(t.*)::text, ',' order by t.id)
                                from trade_transport t
                                where t.source_village_id = :v or t.target_village_id = :v),
                            (select string_agg(row(r.*)::text, ',' order by r.id) from report r
                                where r.player_id = :p))
                        """)
                .param("v", villageId)
                .param("p", playerId)
                .query(String.class)
                .single();
    }
}
