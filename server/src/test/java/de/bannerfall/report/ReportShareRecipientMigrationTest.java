package de.bannerfall.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.sql.Types;
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
 * V34's back-fill of {@code report_share_recipient} (1.3.1) on a database that already has copies
 * of reports: migrated to V33 first, then V34 runs. The Spring context of the other tests starts on
 * an empty database, where the back-fill never sees a row.
 */
class ReportShareRecipientMigrationTest {

    private static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

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
    void eachSendersRecipientsWithTheTimeOfTheNewestCopy() {
        migrate("33");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        long ann = player(jdbc, "ann");
        long ben = player(jdbc, "ben");
        long cleo = player(jdbc, "cleo");

        // a report of the game is no copy
        report(jdbc, ann, null, T0);
        // Ann sent Ben two copies: the newer one counts
        report(jdbc, ben, ann, T0.plus(Duration.ofHours(1)));
        report(jdbc, ben, ann, T0.plus(Duration.ofHours(3)));
        report(jdbc, cleo, ann, T0.plus(Duration.ofHours(2)));
        // Ben sent one on to Ann
        report(jdbc, ann, ben, T0.plus(Duration.ofHours(4)));
        // a copy from a player who is gone (the copy keeps a plain id)
        report(jdbc, cleo, 999_999L, T0.plus(Duration.ofHours(5)));

        migrate("34");

        assertThat(
                        jdbc.sql(
                                        """
                                        select sender_id, recipient_id, sent_at
                                        from report_share_recipient
                                        """)
                                .query(
                                        (rs, row) ->
                                                tuple(
                                                        rs.getLong("sender_id"),
                                                        rs.getLong("recipient_id"),
                                                        rs.getTimestamp("sent_at").toInstant()))
                                .list())
                .containsExactlyInAnyOrder(
                        tuple(ann, ben, T0.plus(Duration.ofHours(3))),
                        tuple(ann, cleo, T0.plus(Duration.ofHours(2))),
                        tuple(ben, ann, T0.plus(Duration.ofHours(4))));
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

    /** A report of the player's; a copy if {@code sharedById} is set. */
    private static void report(JdbcClient jdbc, long playerId, Long sharedById, Instant at) {
        jdbc.sql(
                        """
                        insert into report (player_id, kind, happened_at, payload, created_at,
                                            shared_by_id, shared_by_name)
                        values (:player, 'ATTACK', :t, '{}', :t, :sender, :senderName)
                        """)
                .param("player", playerId)
                .param("sender", sharedById, Types.BIGINT)
                .param("senderName", sharedById == null ? null : "someone", Types.VARCHAR)
                .param("t", Timestamp.from(at))
                .update();
    }
}
