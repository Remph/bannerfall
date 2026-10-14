package de.bannerfall.security;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.abuse.AbuseService;
import de.bannerfall.abuse.InvariantCheck;
import de.bannerfall.abuse.WriteRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** M14e: write rate limit, suspicion log, bans, automatic lock and the invariant check. */
@IntegrationTest
class AbuseProtectionTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired WriteRateLimiter limiter;
    @Autowired AbuseService abuse;
    @Autowired InvariantCheck invariants;

    private Host host;
    private TestPlayer player;
    private String username;

    @BeforeEach
    void setUp() throws Exception {
        host = new Host(mockMvc);
        player = TestPlayer.register(mockMvc);
        username = Fixture.username(jdbc, player.playerId);
    }

    @AfterEach
    void restore() {
        limiter.setLimits(1000, 100000);
        abuse.configureAutoLock(true, 100000, Duration.ofMinutes(10), Duration.ofHours(1));
    }

    @Test
    void aBurstPassesTheNextWriteIs429AndTheBucketRefills() throws Exception {
        limiter.setLimits(1, 3);
        for (int i = 0; i < 3; i++) {
            player.browser
                    .patch(player.villagePath(), "{\"name\":\"v" + i + "\"}")
                    .andExpect(status().isOk());
        }
        player.browser.get(player.villagePath()).andExpect(status().isOk()); // reads are free
        player.browser
                .patch(player.villagePath(), "{\"name\":\"x\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        clock.advance(Duration.ofSeconds(2));
        player.browser.patch(player.villagePath(), "{\"name\":\"y\"}").andExpect(status().isOk());
        assertThat(Fixture.abuseEvents(jdbc, player.playerId, "RATE_LIMITED")).isEqualTo(1);
    }

    @Test
    void timingErrorsAreNotSuspicious() throws Exception {
        player.cancel(99999999).andExpect(status().isNotFound());
        player.cancelMovement(99999999).andExpect(status().isNotFound());
        assertThat(
                        jdbc.sql(
                                        "select count(*) from abuse_event e join player p on"
                                                + " p.account_id = e.account_id where p.id = ?")
                                .param(player.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void anotherSiteOpeningBadApiLinksIsNotHeldAgainstThePlayer() throws Exception {
        // another site navigates the victim's browser to bad API links (his Lax cookie comes along)
        mockMvc.perform(
                        get("/api/villages/abc")
                                .session(player.browser.session())
                                .header("Sec-Fetch-Site", "cross-site")
                                .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isBadRequest());
        assertThat(Fixture.abuseEvents(jdbc, player.playerId, "INVALID_INPUT")).isZero();

        // the same from the game's own page counts
        mockMvc.perform(
                        get("/api/villages/abc")
                                .session(player.browser.session())
                                .header("Sec-Fetch-Site", "same-origin")
                                .header("Sec-Fetch-Mode", "cors"))
                .andExpect(status().isBadRequest());
        assertThat(Fixture.abuseEvents(jdbc, player.playerId, "INVALID_INPUT")).isEqualTo(1);
    }

    @Test
    void suspiciousListsTheAccountWithCounts() throws Exception {
        player.browser
                .post(
                        player.villagePath() + "/recruit-orders",
                        "{\"unit\":\"SPEARMAN\",\"count\":-1}")
                .andExpect(status().isBadRequest());
        String list = body(host.get("/api/admin/suspicious").andExpect(status().isOk()));
        assertThat(
                        JsonPath.<java.util.List<String>>read(
                                list, "$[?(@.name == '" + username + "')].name"))
                .hasSize(1);
        assertThat(
                        JsonPath.<java.util.List<Integer>>read(
                                list, "$[?(@.name == '" + username + "')].counts.INVALID_INPUT"))
                .containsExactly(1);
    }

    @Test
    void aBannedPlayerIsLockedOutAndUnbanLetsHimBack() throws Exception {
        Browser other = Browser.open(mockMvc);
        other.login(username, Browser.PASSWORD).andExpect(status().isOk());
        String until = clock.instant().plus(Duration.ofDays(1)).toString();
        host.post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\"" + until + "\",\"reason\":\"cheating\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banReason").value("cheating"));

        player.browser
                .get("/api/me")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"))
                .andExpect(jsonPath("$.reason").value("cheating"))
                .andExpect(jsonPath("$.bannedUntil").value(until));
        other.get("/api/me").andExpect(status().isForbidden());
        Browser fresh = Browser.open(mockMvc);
        fresh.login(username, Browser.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        fresh.login(username, "wrong password").andExpect(status().isUnauthorized());

        host.post("/api/admin/players/" + player.playerId + "/unban", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannedUntil").doesNotExist());
        fresh.login(username, Browser.PASSWORD).andExpect(status().isOk());
        assertThat(
                        jdbc.sql(
                                        "select count(*) from admin_action where action in"
                                            + " ('BAN_PLAYER', 'UNBAN_PLAYER') and target_id = ?")
                                .param(player.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void aPermanentBanTurnsTheVillagesBarbarianAndATimedBanDoesNot() throws Exception {
        String until = clock.instant().plus(Duration.ofDays(1)).toString();
        host.post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\"" + until + "\",\"reason\":\"x\"}")
                .andExpect(status().isOk());
        assertThat(villagesOf(player.playerId)).isEqualTo(1);

        host.post("/api/admin/players/" + player.playerId + "/ban", "{\"reason\":\"for good\"}")
                .andExpect(status().isOk());
        assertThat(villagesOf(player.playerId)).isZero();
    }

    @Test
    void aBanDateBeyondTheYear9999IsPermanentNotAnError() throws Exception {
        host.post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\"+999999999-12-31T23:59:59Z\",\"reason\":\"x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannedUntil").value("9999-12-31T00:00:00Z"));
        assertThat(villagesOf(player.playerId)).isZero();
    }

    private long villagesOf(long playerId) {
        return jdbc.sql("select count(*) from village where player_id = ?")
                .param(playerId)
                .query(Long.class)
                .single();
    }

    @Test
    void aTimedBanEndsOnItsOwnAndAdminsCantBeBanned() throws Exception {
        host.post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\"" + clock.instant().plusSeconds(60) + "\",\"reason\":\"x\"}")
                .andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(61));
        Browser fresh = Browser.open(mockMvc);
        fresh.login(username, Browser.PASSWORD).andExpect(status().isOk());

        host.post(
                        "/api/admin/players/" + player.playerId + "/ban",
                        "{\"until\":\"" + clock.instant().minusSeconds(5) + "\",\"reason\":\"x\"}")
                .andExpect(status().isBadRequest());
        jdbc.sql("update account set role = 'ADMIN' where username = ?").param(username).update();
        host.post("/api/admin/players/" + player.playerId + "/ban", "{\"reason\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_BAN_ADMIN"));
        jdbc.sql("update account set role = 'PLAYER' where username = ?").param(username).update();
    }

    @Test
    void tooManySuspiciousRequestsLockTheAccountForAWhile() throws Exception {
        abuse.configureAutoLock(true, 5, Duration.ofMinutes(10), Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            player.browser.post(
                    player.villagePath() + "/recruit-orders",
                    "{\"unit\":\"SPEARMAN\",\"count\":-1}");
        }
        player.browser
                .get("/api/me")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
        assertThat(
                        jdbc.sql(
                                        "select count(*) from admin_action where action ="
                                                + " 'AUTO_LOCK' and target_id = ?")
                                .param(player.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        clock.advance(Duration.ofMinutes(61));
        Browser fresh = Browser.open(mockMvc);
        fresh.login(username, Browser.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theInvariantCheckFindsBrokenRows() throws Exception {
        jdbc.sql("update village set wood = -5, b_farm = 99, loyalty = 140 where id = ?")
                .param(player.villageId)
                .update();
        jdbc.sql(
                        "update unit_stack set u_axeman = -1 where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(player.villageId, player.villageId)
                .update();
        jdbc.sql("update village set clay = 1e9 where id = ?").param(player.villageId).update();
        var report = invariants.run();
        var kinds = report.problems().stream().map(p -> p.kind()).toList();
        assertThat(kinds)
                .contains(
                        "NEGATIVE_RESOURCES",
                        "LEVEL_OUT_OF_RANGE",
                        "LOYALTY_OUT_OF_RANGE",
                        "NEGATIVE_UNITS",
                        "RESOURCES_ABOVE_CAPACITY");

        jdbc.sql(
                        "update village set wood = 100, clay = 100, b_farm = 1, loyalty = 100"
                                + " where id = ?")
                .param(player.villageId)
                .update();
        jdbc.sql("update unit_stack set u_axeman = 0 where home_village_id = ?")
                .param(player.villageId)
                .update();
        assertThat(
                        invariants.run().problems().stream()
                                .filter(
                                        p ->
                                                p.examples().contains("village " + player.villageId)
                                                        || p.examples()
                                                                .contains(
                                                                        "stack "
                                                                                + jdbc.sql(
                                                                                                "select"
                                                                                                    + " id from"
                                                                                                    + " unit_stack"
                                                                                                    + " where"
                                                                                                    + " home_village_id"
                                                                                                    + " = ?")
                                                                                        .param(
                                                                                                player.villageId)
                                                                                        .query(
                                                                                                Long
                                                                                                        .class)
                                                                                        .single())))
                .isEmpty();
        host.post("/api/admin/invariants", "").andExpect(status().isOk());
    }
}
