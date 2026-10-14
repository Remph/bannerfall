package de.bannerfall.admin;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The host's banner operations (M32a, 08-host-console): grant, list and revoke, and what happens to
 * the banners of a player who is deleted or banned for good (02-game-rules §21.4.1).
 */
@IntegrationTest
class AdminBannerFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;

    private Browser admin;
    private TestPlayer friend;

    @BeforeEach
    void setUp() throws Exception {
        admin = Browser.open(mockMvc);
        String name = Browser.uniqueUsername();
        admin.register(name);
        jdbc.sql("update account set role = 'ADMIN' where username = ?").param(name).update();
        admin.login(name, Browser.PASSWORD).andExpect(status().isOk());
        friend = TestPlayer.register(mockMvc);
    }

    @Test
    void aBannerIsGrantedListedAndShownInThePlayersStash() throws Exception {
        String granted =
                body(
                        admin.post(path(), "{\"type\":\"WAR\",\"tier\":2,\"reason\":\"prize\"}")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.type").value("WAR"))
                                .andExpect(jsonPath("$.tier").value(2))
                                .andExpect(jsonPath("$.villageId").doesNotExist()));
        long id = JsonPath.<Number>read(granted, "$.id").longValue();

        admin.get(path())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        friend.browser
                .get("/api/banners")
                .andExpect(jsonPath("$.banners[0].id").value(id))
                .andExpect(jsonPath("$.banners[0].type").value("WAR"));
        assertThat(audit("GRANT_BANNER", friend.playerId)).isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "select kind || ':' || detail from banner_event where"
                                                + " banner_id = ?")
                                .param(id)
                                .query(String.class)
                                .single())
                .isEqualTo("GRANTED:prize");
    }

    @Test
    void aTypeOrTierOutsideTheRulebookIsRefused() throws Exception {
        admin.post(path(), "{\"type\":\"WAR\",\"tier\":4}").andExpect(status().isBadRequest());
        admin.post(path(), "{\"type\":\"WAR\",\"tier\":0}").andExpect(status().isBadRequest());
        admin.post(path(), "{\"type\":\"LUCK\",\"tier\":1}").andExpect(status().isBadRequest());
        admin.post(path(), "{\"tier\":1}").andExpect(status().isBadRequest());

        assertThat(banners(friend)).isZero();
    }

    @Test
    void anUnknownPlayerIs404() throws Exception {
        admin.post("/api/admin/players/99999999/banners", "{\"type\":\"WAR\",\"tier\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        admin.get("/api/admin/players/99999999/banners").andExpect(status().isNotFound());
    }

    @Test
    void aPlayerCannotUseTheAdminEndpoints() throws Exception {
        friend.browser
                .post(path(), "{\"type\":\"WAR\",\"tier\":1}")
                .andExpect(status().isForbidden());
        friend.browser.get(path()).andExpect(status().isForbidden());
        friend.browser.post("/api/admin/banners/1/revoke", "").andExpect(status().isForbidden());
    }

    @Test
    void revokingTakesTheBannerAwayAndAFlyingOneFreesItsVillageAtOnce() throws Exception {
        jdbc.sql(
                        "update village set b_banner_hall = 3, wood = 500, resources_updated_at = ?"
                                + " where id = ?")
                .params(java.sql.Timestamp.from(clock.instant()), friend.villageId)
                .update();
        long id =
                JsonPath.<Number>read(
                                body(admin.post(path(), "{\"type\":\"HARVEST\",\"tier\":3}")),
                                "$.id")
                        .longValue();
        friend.browser
                .put("/api/villages/" + friend.villageId + "/banner/" + id, "")
                .andExpect(status().isOk());
        double base =
                JsonPath.<Number>read(body(friend.village()), "$.production.wood").doubleValue()
                        / 1.15;

        clock.advance(Duration.ofHours(1));
        admin.post("/api/admin/banners/" + id + "/revoke", "{\"reason\":\"oops\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("HARVEST"));
        clock.advance(Duration.ofHours(1));

        // one hour with the banner, one without: the village was caught up before it went
        friend.village()
                .andExpect(jsonPath("$.banner").doesNotExist())
                .andExpect(jsonPath("$.production.wood", closeTo(base, 1e-6)))
                .andExpect(jsonPath("$.resources.wood", closeTo(500 + base * 1.15 + base, 1e-3)));
        assertThat(banners(friend)).isZero();
        assertThat(audit("REVOKE_BANNER", id)).isEqualTo(1);
        assertThat(
                        jdbc.sql("select kind from banner_event where banner_id = ? order by id")
                                .param(id)
                                .query(String.class)
                                .list())
                .containsExactly("GRANTED", "EQUIPPED", "DESTROYED");
        admin.post("/api/admin/banners/" + id + "/revoke", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"));
    }

    @Test
    void deletingAPlayerDestroysHisBanners() throws Exception {
        admin.post(path(), "{\"type\":\"WAR\",\"tier\":1}").andExpect(status().isCreated());
        admin.post(path(), "{\"type\":\"OATH\",\"tier\":3}").andExpect(status().isCreated());
        assertThat(banners(friend)).isEqualTo(2);

        admin.post("/api/admin/players/" + friend.playerId + "/delete", "")
                .andExpect(status().isOk());

        assertThat(banners(friend)).isZero();
        assertThat(
                        jdbc.sql(
                                        "select count(*) from banner_event where kind = 'DESTROYED'"
                                                + " and from_player_id = ?")
                                .param(friend.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void aPermanentBanDestroysTheBannersButATimedBanKeepsThem() throws Exception {
        admin.post(path(), "{\"type\":\"WAR\",\"tier\":1}").andExpect(status().isCreated());
        String until = clock.instant().plus(Duration.ofDays(1)).toString();

        admin.post(
                        "/api/admin/players/" + friend.playerId + "/ban",
                        "{\"until\":\"" + until + "\",\"reason\":\"cooling off\"}")
                .andExpect(status().isOk());
        assertThat(banners(friend)).isEqualTo(1);

        admin.post("/api/admin/players/" + friend.playerId + "/ban", "{\"reason\":\"for good\"}")
                .andExpect(status().isOk());
        assertThat(banners(friend)).isZero();
    }

    private String path() {
        return "/api/admin/players/" + friend.playerId + "/banners";
    }

    private long banners(TestPlayer player) {
        return jdbc.sql("select count(*) from banner where owner_id = ?")
                .param(player.playerId)
                .query(Long.class)
                .single();
    }

    private long audit(String action, long targetId) {
        return jdbc.sql("select count(*) from admin_action where action = ? and target_id = ?")
                .params(action, targetId)
                .query(Long.class)
                .single();
    }
}
