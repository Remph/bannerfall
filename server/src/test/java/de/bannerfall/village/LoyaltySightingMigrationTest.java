package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.report.BattleOutcome;
import de.bannerfall.report.BattleReport;
import de.bannerfall.report.ConquestReport;
import de.bannerfall.report.ReportPayload;
import de.bannerfall.report.ReportPayloadConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V23's back-fill (02-game-rules §10, M21) on a database that already has reports: the schema is
 * migrated to V22 first, reports are stored the way the running game stored them (through {@link
 * ReportPayloadConverter}), then V23 runs. The Spring context of the other tests starts on an empty
 * database, where the back-fill's SELECT never sees a row.
 */
class LoyaltySightingMigrationTest {

    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

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
    void existingAttackReportsWithSurvivingNoblesBecomeTheLastSightings() {
        migrate("22");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long human = player(jdbc, "human", false);
        long other = player(jdbc, "other", false);
        long npc = player(jdbc, "npc", true);
        long owner = player(jdbc, "owner", false);
        long hit = village(jdbc, owner, 1, 1);
        long twice = village(jdbc, owner, 2, 1);
        long fell = village(jdbc, owner, 3, 1);
        long noNobles = village(jdbc, owner, 4, 1);
        long barbarian = village(jdbc, null, 5, 1);
        long tie = village(jdbc, owner, 6, 1);

        // one attack with nobles: the loyalty after it
        report(
                jdbc,
                human,
                "ATTACK",
                T0,
                battle(hit, new BattleReport.LoyaltyReport(100, 77, false)));
        // two: the newer one counts, whatever the row order
        report(jdbc, human, "ATTACK", T0.plusSeconds(600), battle(twice, loyalty(40, 15)));
        report(jdbc, human, "ATTACK", T0, battle(twice, loyalty(100, 80)));
        // a barbarian village (no owner) counts as well
        report(jdbc, human, "ATTACK", T0, battle(barbarian, loyalty(100, 65)));
        // same time: the later report wins
        report(jdbc, human, "ATTACK", T0, battle(tie, loyalty(100, 60)));
        report(jdbc, human, "ATTACK", T0, battle(tie, loyalty(60, 31)));
        // the village fell (the conquest is the player's own now), no nobles survived, the
        // defender's copy, a scouting-only attack, a conquest report, an NPC lord's attack and
        // the other player's report: no sighting for these
        report(jdbc, human, "ATTACK", T0, battle(fell, loyalty(30, 0, true)));
        report(jdbc, human, "ATTACK", T0, battle(noNobles, null));
        report(jdbc, human, "DEFENSE", T0, battle(hit, loyalty(100, 50)));
        report(jdbc, human, "SCOUT", T0, battle(noNobles, null));
        report(jdbc, human, "CONQUEST", T0, new ConquestReport(ref(fell), ref(fell), "x"));
        report(jdbc, npc, "ATTACK", T0, battle(hit, loyalty(100, 48)));
        report(jdbc, other, "ATTACK", T0.plusSeconds(60), battle(hit, loyalty(77, 52)));

        migrate("23");

        assertThat(sightings(jdbc))
                .containsExactlyInAnyOrder(
                        row(human, hit, 77, T0),
                        row(human, twice, 15, T0.plusSeconds(600)),
                        row(human, barbarian, 65, T0),
                        row(human, tie, 31, T0),
                        row(other, hit, 52, T0.plusSeconds(60)));
    }

    private static void migrate(String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
    }

    private static BattleReport.LoyaltyReport loyalty(int before, int after) {
        return loyalty(before, after, false);
    }

    private static BattleReport.LoyaltyReport loyalty(int before, int after, boolean conquered) {
        return new BattleReport.LoyaltyReport(before, after, conquered);
    }

    private static BattleReport battle(long defenderVillage, BattleReport.LoyaltyReport loyalty) {
        return new BattleReport(
                ref(0),
                ref(defenderVillage),
                BattleOutcome.ATTACKER_WON,
                0.0,
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                ResourceAmounts.ZERO,
                loyalty);
    }

    private static VillageRefDto ref(long villageId) {
        return new VillageRefDto(villageId, "v" + villageId, 1, 1, null, null);
    }

    private static long player(JdbcClient jdbc, String name, boolean npc) {
        return jdbc.sql(
                        """
                        insert into player (name, npc, protected_until, created_at)
                        values (:name, :npc, :t, :t) returning id
                        """)
                .param("name", name)
                .param("npc", npc)
                .param("t", java.sql.Timestamp.from(T0))
                .query(Long.class)
                .single();
    }

    private static long village(JdbcClient jdbc, Long playerId, int x, int y) {
        return jdbc.sql(
                        """
                        insert into village (player_id, x, y, wood, clay, iron,
                                             resources_updated_at, loyalty_updated_at, created_at)
                        values (:player, :x, :y, 0, 0, 0, :t, :t, :t) returning id
                        """)
                .param("player", playerId)
                .param("x", x)
                .param("y", y)
                .param("t", java.sql.Timestamp.from(T0))
                .query(Long.class)
                .single();
    }

    private static void report(
            JdbcClient jdbc, long playerId, String kind, Instant happenedAt, ReportPayload p) {
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at)
                        values (:player, :kind, :at, cast(:payload as jsonb), :at)
                        """)
                .param("player", playerId)
                .param("kind", kind)
                .param("at", java.sql.Timestamp.from(happenedAt))
                .param("payload", new ReportPayloadConverter().convertToDatabaseColumn(p))
                .update();
    }

    private record Sighting(long playerId, long villageId, int loyalty, Instant seenAt) {}

    private static Sighting row(long playerId, long villageId, int loyalty, Instant seenAt) {
        return new Sighting(playerId, villageId, loyalty, seenAt);
    }

    private static List<Sighting> sightings(JdbcClient jdbc) {
        return jdbc.sql("select player_id, village_id, loyalty, seen_at from loyalty_sighting")
                .query(
                        (rs, i) ->
                                new Sighting(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getInt(3),
                                        rs.getTimestamp(4).toInstant()))
                .list();
    }
}
