package de.bannerfall.village;

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
 * V26_1 (M31 follow-up) on a database that already ran V26 with the statue: the column goes, and a
 * statue still in a build queue is deleted with its completion event (the building type no longer
 * exists, loading such an order would fail). The Spring context of the other tests starts on an
 * empty database, where the clean-up never sees a row.
 */
class StatueCleanupMigrationTest {

    private static final Instant T0 = Instant.parse("2026-10-13T12:00:00Z");

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
    void theStatueColumnAndQueuedStatuesAreGoneAndOtherOrdersStay() {
        Flyway.configure().dataSource(dataSource).target("26").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long village = village(jdbc);
        jdbc.sql("update village set b_statue = 1 where id = :id").param("id", village).update();
        long statue = buildOrder(jdbc, village, "STATUE");
        long wall = buildOrder(jdbc, village, "WALL");
        event(jdbc, statue);
        event(jdbc, wall);

        // the clean-up must also run in front of a version 27 and later, so migrate to the end
        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(
                        jdbc.sql(
                                        "select count(*) from information_schema.columns where"
                                                + " table_name = 'village' and column_name ="
                                                + " 'b_statue'")
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(jdbc.sql("select building from build_order").query(String.class).list())
                .containsExactly("WALL");
        assertThat(jdbc.sql("select ref_id from game_event").query(Long.class).list())
                .containsExactly(wall);
    }

    private static long village(JdbcClient jdbc) {
        return jdbc.sql(
                        """
                        insert into village (x, y, wood, clay, iron,
                                             resources_updated_at, loyalty_updated_at, created_at)
                        values (1, 1, 0, 0, 0, :t, :t, :t) returning id
                        """)
                .param("t", Timestamp.from(T0))
                .query(Long.class)
                .single();
    }

    private static long buildOrder(JdbcClient jdbc, long village, String building) {
        return jdbc.sql(
                        """
                        insert into build_order (village_id, building, target_level, duration_seconds,
                                                 start_at, end_at, cost_wood, cost_clay, cost_iron,
                                                 cost_pop, created_at)
                        values (:village, :building, 1, 60, :t, :t, 0, 0, 0, 0, :t) returning id
                        """)
                .param("village", village)
                .param("building", building)
                .param("t", Timestamp.from(T0))
                .query(Long.class)
                .single();
    }

    private static void event(JdbcClient jdbc, long orderId) {
        jdbc.sql(
                        """
                        insert into game_event (kind, ref_id, due_at, status, created_at)
                        values ('BUILD_COMPLETE', :ref, :t, 'PENDING', :t)
                        """)
                .param("ref", orderId)
                .param("t", Timestamp.from(T0))
                .update();
    }
}
