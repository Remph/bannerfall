package de.bannerfall.security;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M14e: requests that come too late or with an old key. A request after the logout, a stale CSRF
 * token, a cancel replayed after its window, and the sessions that a password reset or change, or
 * the deletion of the player, must end.
 */
@IntegrationTest
class SessionTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;

    private TestPlayer player;
    private String username;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
        username = Fixture.username(jdbc, player.playerId);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void aRequestAfterTheLogoutIsAnonymous() throws Exception {
        var oldSession = player.browser.session();
        player.browser.logout().andExpect(status().isNoContent());

        player.browser
                .get("/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        // the old session id is dead too
        assertThat(oldSession.isInvalid()).isTrue();
    }

    @Test
    void aStaleCsrfTokenIsRefusedAndRecorded() throws Exception {
        String stale = player.browser.csrfToken();
        // a new login hands out a new token
        player.browser.login(username, Browser.PASSWORD).andExpect(status().isOk());
        String fresh = player.browser.csrfToken();
        assertThat(fresh).isNotEqualTo(stale);

        mockMvc.perform(
                        post(player.villagePath() + "/build-orders")
                                .session(player.browser.session())
                                .cookie(new Cookie("XSRF-TOKEN", fresh))
                                .header("X-XSRF-TOKEN", stale)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"building\":\"WOODCUTTER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        player.browser
                .postWithoutCsrfHeader(player.villagePath() + "/build-orders", "{}")
                .andExpect(status().isForbidden());

        assertThat(Fixture.abuseEvents(jdbc, player.playerId, "CSRF_FAILED")).isEqualTo(2);
        assertThat(
                        jdbc.sql("select count(*) from build_order where village_id = ?")
                                .param(player.villageId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void aCancelReplayedAfterItsWindowIsRefused() throws Exception {
        TestPlayer target = TestPlayer.register(mockMvc);
        Coordinates at = Fixture.coordinates(target);
        Fixture.setHomeUnits(jdbc, player.villageId, "u_spearman", 20);
        long first = sendSupport(at);
        long second = sendSupport(at);

        player.cancelMovement(first).andExpect(status().isOk());
        // the same cancel again: it is a RETURN now
        player.cancelMovement(first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_OVER"));
        // after the window (600 s in the test world)
        clock.advance(Duration.ofSeconds(601));
        player.cancelMovement(second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_OVER"));
        assertThat(
                        jdbc.sql("select kind from movement where id = ?")
                                .param(second)
                                .query(String.class)
                                .single())
                .isEqualTo("SUPPORT");
    }

    @Test
    void aPasswordResetByTheHostEndsEverySessionOfTheAccount() throws Exception {
        Browser phone = Browser.open(mockMvc);
        phone.login(username, Browser.PASSWORD).andExpect(status().isOk());

        String reset =
                body(
                        new Host(mockMvc)
                                .post(
                                        "/api/admin/players/" + player.playerId + "/reset-password",
                                        "")
                                .andExpect(status().isOk()));
        String password = JsonPath.read(reset, "$.generatedPassword");

        player.browser.get("/api/me").andExpect(status().isUnauthorized());
        phone.get("/api/me").andExpect(status().isUnauthorized());
        // the friend logs in with the new one
        phone.login(username, password).andExpect(status().isOk());
        phone.get("/api/me").andExpect(status().isOk());
    }

    @Test
    void changingTheOwnPasswordEndsTheOtherSessionsButNotThisOne() throws Exception {
        Browser stolen = Browser.open(mockMvc);
        stolen.login(username, Browser.PASSWORD).andExpect(status().isOk());
        String oldSessionId = player.browser.session().getId();

        player.browser
                .post(
                        "/api/me/password",
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"a new secret\"}"
                                .formatted(Browser.PASSWORD))
                .andExpect(status().isNoContent());

        player.browser.get("/api/me").andExpect(status().isOk());
        assertThat(player.browser.session().getId()).isNotEqualTo(oldSessionId);
        player.enqueue(de.bannerfall.gamedata.BuildingType.WOODCUTTER)
                .andExpect(status().isCreated());
        stolen.get("/api/me").andExpect(status().isUnauthorized());
        stolen.login(username, Browser.PASSWORD).andExpect(status().isUnauthorized());
    }

    private long sendSupport(Coordinates at) throws Exception {
        return number(
                        body(
                                player.command("SUPPORT", at, "{\"SPEARMAN\":10}")
                                        .andExpect(status().isCreated())),
                        "$.id")
                .longValue();
    }
}
