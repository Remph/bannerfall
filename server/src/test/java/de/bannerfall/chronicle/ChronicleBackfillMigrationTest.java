package de.bannerfall.chronicle;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportPayloadConverter;
import de.bannerfall.village.VillageRefDto;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

/**
 * V73's back-fill of the conquests a world kept in its reports before the chronicle (2.1 I1): on a
 * database migrated to V72 with conquest reports, then V73 runs. The Spring context of the other
 * tests starts on an empty database, where the back-fill never sees a row.
 */
class ChronicleBackfillMigrationTest {

    private static final Instant OPENED = Instant.parse("2026-11-01T12:00:00Z");

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
    void theConquerorsReportsBecomeConquestsWithTheirGameDay() {
        migrate("72");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("update world_settings set initialized_at = :t, speed = 2")
                .param("t", Timestamp.from(OPENED))
                .update();
        long ann = player(jdbc, "Ann", false);
        long lord = player(jdbc, "Sir Godwin", true);
        long fromLord = village(jdbc, ann, 1);
        long fromBarbarians = village(jdbc, ann, 2);

        // day 3 at speed 2: 1 day 2 hours after the opening is the third half day
        Instant taken = OPENED.plus(Duration.ofHours(26));
        report(jdbc, ann, "CONQUEST", ref(fromLord, "Eichenhain", lord, "Sir Godwin"), taken, null);
        // the lord's own report of the loss and a copy Ann shared are not conquests of their own
        report(
                jdbc,
                lord,
                "VILLAGE_LOST",
                ref(fromLord, "Eichenhain", lord, "Sir Godwin"),
                taken,
                null);
        report(jdbc, lord, "CONQUEST", ref(fromLord, "Eichenhain", lord, "Sir Godwin"), taken, ann);
        // before the opening: day 0
        report(
                jdbc,
                ann,
                "CONQUEST",
                ref(fromBarbarians, null, null, null),
                OPENED.minus(Duration.ofHours(1)),
                null);

        migrate("73");

        List<JsonNode> rows =
                jdbc.sql("select day, kind, data::text as data from chronicle_event order by at")
                        .query(
                                (rs, row) -> {
                                    JsonNode data = Chronicle.JSON.readTree(rs.getString("data"));
                                    assertThat(rs.getString("kind")).isEqualTo("CONQUEST");
                                    JsonNode row1 =
                                            Chronicle.JSON
                                                    .createObjectNode()
                                                    .put("day", rs.getInt("day"))
                                                    .set("data", data);
                                    return row1;
                                })
                        .list();
        assertThat(rows).hasSize(2);
        JsonNode barbarian = rows.get(0);
        assertThat(barbarian.get("day").asInt()).isZero();
        assertThat(barbarian.get("data").get("from").isNull()).isTrue();
        assertThat(barbarian.get("data").get("to").get("id").asLong()).isEqualTo(ann);

        JsonNode conquest = rows.get(1);
        assertThat(conquest.get("day").asInt()).isEqualTo(3);
        JsonNode data = conquest.get("data");
        // the name the village was taken under, as the live chronicle writes it
        assertThat(data.get("village").get("name").asString()).isEqualTo("Eichenhain");
        assertThat(data.get("village").get("id").asLong()).isEqualTo(fromLord);
        assertThat(data.get("from").get("id").asLong()).isEqualTo(lord);
        assertThat(data.get("from").get("npc").asBoolean()).isTrue();
        assertThat(data.get("to").get("name").asString()).isEqualTo("Ann");
        assertThat(data.get("keep").asBoolean()).isFalse();
        // it reads back as the facts the chronicle tells
        ChronicleFacts facts = Chronicle.JSON.treeToValue(data, ChronicleFacts.class);
        assertThat(facts.from().npc()).isTrue();
    }

    private static void migrate(String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
    }

    private static long player(JdbcClient jdbc, String name, boolean npc) {
        return jdbc.sql(
                        """
                        insert into player (name, npc, protected_until, created_at)
                        values (:name, :npc, :t, :t) returning id
                        """)
                .param("name", name)
                .param("npc", npc)
                .param("t", Timestamp.from(OPENED))
                .query(Long.class)
                .single();
    }

    private static long village(JdbcClient jdbc, long playerId, int x) {
        return jdbc.sql(
                        """
                        insert into village (player_id, x, y, wood, clay, iron,
                                             resources_updated_at, loyalty_updated_at, created_at)
                        values (:player, :x, 1, 0, 0, 0, :t, :t, :t) returning id
                        """)
                .param("player", playerId)
                .param("x", x)
                .param("t", Timestamp.from(OPENED))
                .query(Long.class)
                .single();
    }

    private static VillageRefDto ref(long villageId, String name, Long ownerId, String ownerName) {
        return new VillageRefDto(villageId, name, 1, 1, ownerId, ownerName);
    }

    private static void report(
            JdbcClient jdbc,
            long playerId,
            String kind,
            VillageRefDto village,
            Instant at,
            Long sharedBy) {
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at,
                                            shared_by_id)
                        values (:player, :kind, :at, cast(:payload as jsonb), :at, :shared)
                        """)
                .param("player", playerId)
                .param("kind", kind)
                .param("at", Timestamp.from(at))
                .param("shared", sharedBy)
                .param(
                        "payload",
                        new ReportPayloadConverter()
                                .convertToDatabaseColumn(
                                        new ConquestReport(village, village, "Ann's village")))
                .update();
    }
}
