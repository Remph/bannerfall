package de.bannerfall.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FakeAccountServer;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.account.AccountLoginMode;
import de.bannerfall.account.AccountServerSettings;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Career reports to the account server (1.5, 01-architecture §4.9b), against {@link
 * FakeAccountServer}. Players are linked by setting their account's issuer and subject directly;
 * {@code AccountServerLoginTest} covers how that happens.
 */
@IntegrationTest
class CareerReporterTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired AccountServerSettings settings;
    @Autowired CareerReporter reporter;
    @Autowired WorldService world;

    private final FakeAccountServer provider = FakeAccountServer.get();

    @BeforeEach
    void careerOn() {
        settings.setMode(AccountLoginMode.LINKING);
        settings.setCareerReports(true);
        // what earlier tests left behind goes out now, so each test sees only its own players
        reporter.report();
        provider.takeCareerReports();
    }

    @AfterEach
    void careerOff() {
        settings.setCareerReports(false);
        settings.setMode(AccountLoginMode.OFF);
        provider.careerStatus(200);
    }

    /** A new player of this world whose account logs in through the account server. */
    private String linkedPlayer(TestPlayer player) {
        String subject = FakeAccountServer.newSubject();
        jdbc.sql(
                        """
                        update account set external_issuer = :issuer, external_subject = :subject,
                            password_hash = null
                        where id = (select account_id from player where id = :player)
                        """)
                .param("issuer", provider.issuer())
                .param("subject", subject)
                .param("player", player.playerId)
                .update();
        return subject;
    }

    /** The report of {@code subject} in what the account server got, or null. */
    private Map<String, Object> reportOf(List<String> bodies, String subject) {
        for (String body : bodies) {
            List<Map<String, Object>> found =
                    JsonPath.read(body, "$.players[?(@.subject == '" + subject + "')]");
            if (!found.isEmpty()) {
                return found.getFirst();
            }
        }
        return null;
    }

    @Test
    void aLinkedPlayerIsReportedOnceAndAgainWhenSomethingChanged() throws Exception {
        TestPlayer linked = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(linked);
        TestPlayer local = TestPlayer.register(mockMvc);
        jdbc.sql(
                        """
                        insert into player_achievement (player_id, achievement, level, earned_at)
                        values (?, 'FIRST_WORDS', 1, ?)
                        """)
                .params(linked.playerId, Timestamp.from(Instant.parse("2026-10-01T12:00:00Z")))
                .update();

        assertThat(reporter.report()).isPositive();
        List<String> bodies = provider.takeCareerReports();
        Map<String, Object> report = reportOf(bodies, subject);
        assertThat(report).isNotNull();
        String name =
                jdbc.sql("select name from player where id = ?")
                        .param(linked.playerId)
                        .query(String.class)
                        .single();
        assertThat(report).containsEntry("name", name).containsEntry("deleted", false);
        assertThat(report.get("rank")).isNotNull();
        assertThat(report.get("result")).isNull();
        assertThat(JsonPath.<List<String>>read(report, "$.achievements[*].key"))
                .containsExactly("FIRST_WORDS");
        assertThat(JsonPath.<String>read(bodies.getFirst(), "$.world.phase")).isEqualTo("OPEN");
        assertThat(JsonPath.<String>read(bodies.getFirst(), "$.world.startedAt"))
                .isEqualTo(world.startedAt().toString());
        // a player with his own password here isn't the account server's business
        String localName =
                jdbc.sql("select name from player where id = ?")
                        .param(local.playerId)
                        .query(String.class)
                        .single();
        assertThat(String.join("", bodies)).doesNotContain("\"" + localName + "\"");

        // nothing new: nothing sent
        reporter.report();
        assertThat(reportOf(provider.takeCareerReports(), subject)).isNull();

        jdbc.sql("update player set points = points + 25 where id = ?")
                .param(linked.playerId)
                .update();
        reporter.report();
        Map<String, Object> again = reportOf(provider.takeCareerReports(), subject);
        assertThat(again).isNotNull();
        assertThat((Integer) again.get("points")).isEqualTo((Integer) report.get("points") + 25);
    }

    @Test
    void theReportCarriesTheBattleTotals() throws Exception {
        TestPlayer linked = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(linked);
        reporter.report();
        Map<String, Object> before = reportOf(provider.takeCareerReports(), subject);
        assertThat(JsonPath.<Integer>read(before, "$.stats.unitsKilled")).isZero();

        jdbc.sql(
                        """
                        insert into player_stat (player_id, stat, value)
                        values (:p, 'ATTACKS_WON', 3), (:p, 'DEFENCES_WON', 2),
                               (:p, 'KILLED_ATTACKING', 40), (:p, 'KILLED_DEFENDING', 2),
                               (:p, 'VILLAGES_CONQUERED', 1), (:p, 'LOOT_TAKEN', 900)
                        """)
                .param("p", linked.playerId)
                .update();
        reporter.report();
        Map<String, Object> report = reportOf(provider.takeCareerReports(), subject);
        // a new total is a change: sent again
        assertThat(report).isNotNull();
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.stats"))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "attacksWon", 3,
                                "defencesWon", 2,
                                "unitsKilled", 42,
                                "villagesConquered", 1));
    }

    @Test
    void aFailedRoundIsTheNextRoundsWork() throws Exception {
        TestPlayer linked = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(linked);
        provider.careerStatus(503);

        assertThat(reporter.report()).isZero();

        provider.careerStatus(200);
        reporter.report();
        assertThat(reportOf(provider.takeCareerReports(), subject)).isNotNull();
    }

    @Test
    void aReportTheAccountServerRejectsHoldsBackNoOtherPlayer() throws Exception {
        TestPlayer bad = TestPlayer.register(mockMvc);
        String badSubject = linkedPlayer(bad);
        TestPlayer good = TestPlayer.register(mockMvc);
        String goodSubject = linkedPlayer(good);
        provider.rejectCareerOf(badSubject, true);
        int rejections = provider.careerRejections();
        try {
            // other tests' players may change too (ranks): only these two count here
            reporter.report();
            assertThat(provider.careerRejections()).isGreaterThan(rejections);
            List<String> bodies = provider.takeCareerReports();
            assertThat(reportOf(bodies, goodSubject)).isNotNull();
            assertThat(reportOf(bodies, badSubject)).isNull();

            // unchanged, the rejected report isn't sent again
            rejections = provider.careerRejections();
            reporter.report();
            assertThat(provider.careerRejections()).isEqualTo(rejections);

            // changed, it is
            provider.rejectCareerOf(badSubject, false);
            jdbc.sql("update player set points = points + 1 where id = ?")
                    .param(bad.playerId)
                    .update();
            reporter.report();
            assertThat(reportOf(provider.takeCareerReports(), badSubject)).isNotNull();
        } finally {
            provider.rejectCareerOf(badSubject, false);
        }
    }

    @Test
    void theEndOfTheWorldTellsWhetherHisSideWon() throws Exception {
        TestPlayer winner = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(winner);
        Instant endedAt = Instant.parse("2026-10-10T18:00:00Z");
        try {
            jdbc.sql(
                            """
                            insert into world_result (id, reason, ended_at, winner_kind,
                                winner_ref_id, winner_name)
                            values (1, 'END_DATE', ?, 'PLAYER', ?, 'Winner')
                            """)
                    .params(Timestamp.from(endedAt), winner.playerId)
                    .update();
            jdbc.sql(
                            """
                            insert into world_result_side (rank, kind, ref_id, name, npc, villages,
                                points, keeps)
                            values (1, 'PLAYER', ?, 'Winner', false, 3, 900, 0)
                            """)
                    .param(winner.playerId)
                    .update();
            jdbc.sql("update world_settings set ended_at = ?")
                    .param(Timestamp.from(endedAt))
                    .update();
            world.reload();

            reporter.report();

            Map<String, Object> report = reportOf(provider.takeCareerReports(), subject);
            assertThat(report).isNotNull();
            assertThat(JsonPath.<Boolean>read(report, "$.result.won")).isTrue();
            assertThat(JsonPath.<Integer>read(report, "$.result.place")).isEqualTo(1);
            assertThat(JsonPath.<String>read(report, "$.result.reason")).isEqualTo("END_DATE");
        } finally {
            jdbc.sql("delete from world_result_side").update();
            jdbc.sql("delete from world_result").update();
            jdbc.sql("update world_settings set ended_at = null").update();
            world.reload();
        }
    }

    @Test
    void aMemberSharesHisGuildsResult() throws Exception {
        TestPlayer member = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(member);
        TestPlayer loner = TestPlayer.register(mockMvc);
        String lonerSubject = linkedPlayer(loner);
        Instant endedAt = Instant.parse("2026-10-10T18:00:00Z");
        String suffix = Long.toString(System.nanoTime(), 36);
        long guildId =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .params("Career " + suffix, suffix.substring(suffix.length() - 4))
                        .query(Long.class)
                        .single();
        try {
            jdbc.sql(
                            "insert into guild_member (player_id, guild_id, role, joined_at)"
                                    + " values (?, ?, 'LEADER', now())")
                    .params(member.playerId, guildId)
                    .update();
            jdbc.sql(
                            """
                            insert into world_result (id, reason, ended_at, winner_kind,
                                winner_ref_id, winner_name)
                            values (1, 'DOMINATION', ?, 'GUILD', ?, ?)
                            """)
                    .params(Timestamp.from(endedAt), guildId, "Career " + suffix)
                    .update();
            jdbc.sql(
                            """
                            insert into world_result_side (rank, kind, ref_id, name, npc, villages,
                                points, keeps)
                            values (1, 'GUILD', ?, ?, false, 9, 3000, 2),
                                   (2, 'PLAYER', ?, 'Loner', false, 1, 100, 0)
                            """)
                    .params(guildId, "Career " + suffix, loner.playerId)
                    .update();
            jdbc.sql("update world_settings set ended_at = ?")
                    .param(Timestamp.from(endedAt))
                    .update();
            world.reload();

            reporter.report();

            List<String> bodies = provider.takeCareerReports();
            Map<String, Object> report = reportOf(bodies, subject);
            assertThat(JsonPath.<Boolean>read(report, "$.result.won")).isTrue();
            assertThat(JsonPath.<Integer>read(report, "$.result.place")).isEqualTo(1);
            assertThat(JsonPath.<String>read(report, "$.result.side"))
                    .isEqualTo("Career " + suffix);
            Map<String, Object> lonerReport = reportOf(bodies, lonerSubject);
            assertThat(JsonPath.<Boolean>read(lonerReport, "$.result.won")).isFalse();
            assertThat(JsonPath.<Integer>read(lonerReport, "$.result.place")).isEqualTo(2);
            assertThat(JsonPath.<String>read(bodies.getFirst(), "$.world.phase"))
                    .isEqualTo("ENDED");
        } finally {
            jdbc.sql("delete from world_result_side").update();
            jdbc.sql("delete from world_result").update();
            jdbc.sql("update world_settings set ended_at = null").update();
            jdbc.sql("delete from guild where id = ?").param(guildId).update();
            world.reload();
        }
    }

    @Test
    void aPlayerTheHostDeletedIsReportedWithoutARank() throws Exception {
        TestPlayer linked = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(linked);
        reporter.report();
        provider.takeCareerReports();

        jdbc.sql(
                        "update account set disabled = true"
                                + " where id = (select account_id from player where id = ?)")
                .param(linked.playerId)
                .update();
        reporter.report();

        Map<String, Object> report = reportOf(provider.takeCareerReports(), subject);
        assertThat(report).containsEntry("deleted", true);
        assertThat(report.get("rank")).isNull();
    }

    @Test
    void offByDefault() throws Exception {
        String subject = linkedPlayer(TestPlayer.register(mockMvc));
        settings.setCareerReports(false);

        assertThat(reporter.report()).isZero();
        assertThat(reportOf(provider.takeCareerReports(), subject)).isNull();
    }

    @Test
    void theProfileLinksALinkedPlayerToHisCareer() throws Exception {
        TestPlayer linked = TestPlayer.register(mockMvc);
        String subject = linkedPlayer(linked);
        TestPlayer local = TestPlayer.register(mockMvc);

        local.browser
                .get("/api/players/" + linked.playerId)
                .andExpect(jsonPath("$.careerUrl").value(provider.issuer() + "/career/" + subject));
        local.browser
                .get("/api/players/" + local.playerId)
                .andExpect(jsonPath("$.careerUrl").doesNotExist());

        settings.setCareerReports(false);
        local.browser
                .get("/api/players/" + linked.playerId)
                .andExpect(jsonPath("$.careerUrl").doesNotExist());
    }

    @Test
    void theModeOffSendsNothingEvenWithReportsOn() throws Exception {
        String subject = linkedPlayer(TestPlayer.register(mockMvc));
        settings.setMode(AccountLoginMode.OFF);

        assertThat(reporter.report()).isZero();
        assertThat(reportOf(provider.takeCareerReports(), subject)).isNull();
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(jsonPath("$.careerReports").value(false));

        settings.setMode(AccountLoginMode.LINKING);
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(jsonPath("$.careerReports").value(true));
    }
}
