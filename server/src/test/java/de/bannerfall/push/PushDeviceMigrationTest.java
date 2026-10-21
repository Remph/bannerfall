package de.bannerfall.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V49 (1.5.2) on a world that has push devices: of a player's devices on one push service only the
 * newest stays (the browser's current address), other services and other players keep theirs, and
 * the players' switches stay.
 */
class PushDeviceMigrationTest {

    private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");
    private static final String MOZILLA = "https://updates.push.services.mozilla.com/wpush/v2/";
    private static final String GOOGLE = "https://fcm.googleapis.com/fcm/send/";

    private static PostgreSQLContainer postgres;
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
        postgres.start();
        dataSource =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    @Test
    void onlyTheNewestDeviceOfAPushServiceStaysAndSwitchesStay() {
        migrate("48");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long ann = player(jdbc, "ann");
        long bob = player(jdbc, "bob");
        // ann's phone made a new address at every start; her desktop Chrome kept its one
        device(jdbc, ann, MOZILLA + "a1");
        device(jdbc, ann, GOOGLE + "a-desktop");
        device(jdbc, ann, MOZILLA + "a2");
        device(jdbc, ann, MOZILLA + "a3");
        device(jdbc, bob, MOZILLA + "b1");
        jdbc.sql(
                        """
                        insert into push_preference (player_id, kind, enabled)
                        values (:player, 'BUILDS_DONE', true)
                        """)
                .param("player", ann)
                .update();

        migrate("49");

        assertThat(
                        jdbc.sql("select endpoint from push_subscription order by id")
                                .query(String.class)
                                .list())
                .containsExactly(GOOGLE + "a-desktop", MOZILLA + "a3", MOZILLA + "b1");
        assertThat(
                        jdbc.sql("select enabled from push_preference where player_id = :player")
                                .param("player", ann)
                                .query(Boolean.class)
                                .list())
                .containsExactly(true);
    }

    private static long player(JdbcClient jdbc, String name) {
        return jdbc.sql(
                        """
                        insert into player (name, npc, protected_until, created_at)
                        values (:name, false, :t, :t) returning id
                        """)
                .param("name", name)
                .param("t", Timestamp.from(T0))
                .query(Long.class)
                .single();
    }

    private static void device(JdbcClient jdbc, long player, String endpoint) {
        jdbc.sql(
                        """
                        insert into push_subscription
                            (player_id, endpoint, p256dh, auth, language, created_at)
                        values (:player, :endpoint, 'p', 'a', 'de', :t)
                        """)
                .param("player", player)
                .param("endpoint", endpoint)
                .param("t", Timestamp.from(T0))
                .update();
    }

    private static void migrate(String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
    }
}
