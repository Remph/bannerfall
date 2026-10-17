package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportPayloadConverter;
import java.sql.Timestamp;
import java.time.Duration;
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
 * V33's back-fill of {@code village.owner_since} (v1.3) on a database that already has villages and
 * conquest reports: migrated to V32 first, then V33 runs. The Spring context of the other tests
 * starts on an empty database, where the back-fill never sees a row.
 */
class OwnerSinceMigrationTest {

    private static final Instant T0 = Instant.parse("2026-10-12T12:00:00Z");

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
    void ownersCountFromTheLatestConquestElseFromTheFounding() {
        migrate("32");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long ann = player(jdbc, "ann");
        long ben = player(jdbc, "ben");
        long founded = village(jdbc, ann, 1, T0);
        long barbarianTaken = village(jdbc, ann, 2, T0.minus(Duration.ofDays(1)));
        long takenFromBen = village(jdbc, ann, 3, T0);
        long takenTwice = village(jdbc, ann, 4, T0);
        long reportsDeleted = village(jdbc, ann, 5, T0.minus(Duration.ofDays(1)));
        long barbarian = village(jdbc, null, 6, T0);

        // the conqueror's report
        conquest(jdbc, ann, "CONQUEST", barbarianTaken, T0.plus(Duration.ofHours(1)));
        // only the previous owner kept his report
        conquest(jdbc, ben, "VILLAGE_LOST", takenFromBen, T0.plus(Duration.ofHours(2)));
        // Ben took it first, Ann later: the later conquest counts
        conquest(jdbc, ben, "CONQUEST", takenTwice, T0.plus(Duration.ofHours(1)));
        conquest(jdbc, ann, "CONQUEST", takenTwice, T0.plus(Duration.ofHours(3)));
        conquest(jdbc, ben, "VILLAGE_LOST", takenTwice, T0.plus(Duration.ofHours(3)));
        // a barbarian village's conquest report doesn't give it an owner
        conquest(jdbc, ann, "CONQUEST", barbarian, T0.plus(Duration.ofHours(4)));

        migrate("33");

        assertThat(ownerSince(jdbc, founded)).isEqualTo(T0);
        assertThat(ownerSince(jdbc, barbarianTaken)).isEqualTo(T0.plus(Duration.ofHours(1)));
        assertThat(ownerSince(jdbc, takenFromBen)).isEqualTo(T0.plus(Duration.ofHours(2)));
        assertThat(ownerSince(jdbc, takenTwice)).isEqualTo(T0.plus(Duration.ofHours(3)));
        assertThat(ownerSince(jdbc, reportsDeleted)).isEqualTo(T0.minus(Duration.ofDays(1)));
        assertThat(ownerSince(jdbc, barbarian)).isNull();
        assertThat(
                        jdbc.sql("select village_name_language from world_settings")
                                .query(String.class)
                                .list())
                .containsOnly("EN");
    }

    private static void migrate(String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
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

    private static long village(JdbcClient jdbc, Long playerId, int x, Instant createdAt) {
        return jdbc.sql(
                        """
                        insert into village (player_id, x, y, wood, clay, iron,
                                             resources_updated_at, loyalty_updated_at, created_at)
                        values (:player, :x, 1, 0, 0, 0, :t, :t, :t) returning id
                        """)
                .param("player", playerId)
                .param("x", x)
                .param("t", Timestamp.from(createdAt))
                .query(Long.class)
                .single();
    }

    private static void conquest(
            JdbcClient jdbc, long playerId, String kind, long villageId, Instant at) {
        VillageRefDto village = new VillageRefDto(villageId, "v" + villageId, 1, 1, null, null);
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at)
                        values (:player, :kind, :at, cast(:payload as jsonb), :at)
                        """)
                .param("player", playerId)
                .param("kind", kind)
                .param("at", Timestamp.from(at))
                .param(
                        "payload",
                        new ReportPayloadConverter()
                                .convertToDatabaseColumn(
                                        new ConquestReport(village, village, "v" + villageId)))
                .update();
    }

    private static Instant ownerSince(JdbcClient jdbc, long villageId) {
        Timestamp at =
                jdbc.sql("select owner_since from village where id = ?")
                        .param(villageId)
                        .query(Timestamp.class)
                        .list()
                        .getFirst();
        return at == null ? null : at.toInstant();
    }
}
