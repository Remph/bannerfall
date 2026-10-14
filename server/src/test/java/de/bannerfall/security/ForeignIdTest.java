package de.bannerfall.security;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M14e: a second player uses the first one's ids on every endpoint that takes an id. Every answer
 * is the same 404 as for an id that doesn't exist (no hint that it exists), nothing of the first
 * player changes, and each try lands in the suspicion log as {@code FOREIGN_ID}.
 */
@IntegrationTest
class ForeignIdTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;

    private TestPlayer alice;
    private TestPlayer mallory;
    private long aliceOrder;
    private long aliceRecruit;
    private long aliceDismiss;
    private long aliceResearch;
    private long aliceFarm;
    private long aliceMovement;
    private long aliceStackAtCarol;
    private long aliceHomeStack;
    private long aliceReport;
    private long aliceOffer;
    private Coordinates carolAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        alice = TestPlayer.register(mockMvc);
        mallory = TestPlayer.register(mockMvc);
        TestPlayer carol = TestPlayer.register(mockMvc);
        carolAt = Fixture.coordinates(carol);
        Fixture.richVillage(jdbc, alice.villageId);
        Fixture.richVillage(jdbc, mallory.villageId);
        Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 100);
        Fixture.setHomeUnits(jdbc, mallory.villageId, "u_spearman", 100);

        aliceOrder = alice.enqueueOk(BuildingType.WOODCUTTER);
        aliceRecruit = alice.recruitOk(UnitType.SPEARMAN, 5);
        aliceDismiss = alice.dismissOk(UnitType.SPEARMAN, 5);
        aliceResearch =
                jdbc.sql(
                                """
                                insert into research_order (player_id, village_id, unit, level,
                                    start_at, end_at, cost_wood, cost_clay, cost_iron, created_at)
                                values (?, ?, 'SPEARMAN', 2, now(), now() + interval '1 day', 0, 0,
                                    0, now())
                                returning id
                                """)
                        .params(alice.playerId, alice.villageId)
                        .query(Long.class)
                        .single();
        aliceFarm =
                number(
                                body(
                                        alice.browser
                                                .post(
                                                        alice.villagePath() + "/farm-targets",
                                                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":5}}"
                                                                .formatted(
                                                                        carolAt.x(), carolAt.y()))
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        aliceMovement =
                number(
                                body(
                                        alice.command("SUPPORT", carolAt, "{\"SPEARMAN\":10}")
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        aliceStackAtCarol = Fixture.supportStack(jdbc, alice.villageId, carol.villageId, 5);
        aliceHomeStack =
                jdbc.sql(
                                "select id from unit_stack where home_village_id = ?"
                                        + " and location_village_id = ?")
                        .params(alice.villageId, alice.villageId)
                        .query(Long.class)
                        .single();
        aliceReport = Fixture.report(jdbc, alice.playerId, clock.instant());
        aliceOffer =
                number(
                                body(
                                        alice.browser
                                                .post(
                                                        alice.villagePath() + "/offers",
                                                        """
                                                        {"offerResource":"WOOD","offerAmount":1000,
                                                         "wantResource":"CLAY","wantAmount":1000,
                                                         "count":1}\
                                                        """)
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void foreignIdsAreNotFoundChangeNothingAndAreRecorded() throws Exception {
        String before = Fixture.fingerprint(jdbc, alice.villageId, alice.playerId);
        String a = "/api/villages/" + alice.villageId;
        String m = mallory.villagePath();

        notFound(get(a), "VILLAGE_NOT_FOUND");
        notFound(mallory.browser.patch(a, "{\"name\":\"pwned\"}"), "VILLAGE_NOT_FOUND");
        notFound(get(a + "/buildings"), "VILLAGE_NOT_FOUND");
        notFound(post(a + "/build-orders", "{\"building\":\"WOODCUTTER\"}"), "VILLAGE_NOT_FOUND");
        notFound(delete(a + "/build-orders/" + aliceOrder), "VILLAGE_NOT_FOUND");
        notFound(delete(m + "/build-orders/" + aliceOrder), "BUILD_ORDER_NOT_FOUND");
        notFound(
                post(a + "/build-orders/" + aliceOrder + "/move", "{\"position\":0}"),
                "VILLAGE_NOT_FOUND");
        notFound(
                post(m + "/build-orders/" + aliceOrder + "/move", "{\"position\":0}"),
                "BUILD_ORDER_NOT_FOUND");
        notFound(get(a + "/recruitment"), "VILLAGE_NOT_FOUND");
        notFound(
                post(a + "/recruit-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}"),
                "VILLAGE_NOT_FOUND");
        notFound(
                post(
                        a + "/recruit-orders/all",
                        "{\"orders\":[{\"unit\":\"SPEARMAN\",\"count\":1}]}"),
                "VILLAGE_NOT_FOUND");
        notFound(delete(m + "/recruit-orders/" + aliceRecruit), "RECRUIT_ORDER_NOT_FOUND");
        notFound(
                post(a + "/dismiss-orders", "{\"unit\":\"SPEARMAN\",\"count\":1}"),
                "VILLAGE_NOT_FOUND");
        notFound(delete(a + "/dismiss-orders/" + aliceDismiss), "VILLAGE_NOT_FOUND");
        notFound(delete(m + "/dismiss-orders/" + aliceDismiss), "DISMISS_ORDER_NOT_FOUND");
        notFound(get(a + "/troops"), "VILLAGE_NOT_FOUND");
        String command =
                "{\"kind\":\"ATTACK\",\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":1}}"
                        .formatted(carolAt.x(), carolAt.y());
        notFound(post(a + "/commands/preview", command), "VILLAGE_NOT_FOUND");
        notFound(post(a + "/commands", command), "VILLAGE_NOT_FOUND");
        notFound(post("/api/movements/" + aliceMovement + "/cancel", ""), "MOVEMENT_NOT_FOUND");
        notFound(post("/api/stacks/" + aliceStackAtCarol + "/return", units()), "STACK_NOT_FOUND");
        notFound(post("/api/stacks/" + aliceHomeStack + "/return", units()), "STACK_NOT_FOUND");
        notFound(get("/api/reports/" + aliceReport), "REPORT_NOT_FOUND");
        notFound(delete("/api/reports/" + aliceReport), "REPORT_NOT_FOUND");
        notFound(get(a + "/market"), "VILLAGE_NOT_FOUND");
        notFound(
                post(
                        a + "/transports",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":100,\"clay\":0,\"iron\":0}"
                                .formatted(carolAt.x(), carolAt.y())),
                "VILLAGE_NOT_FOUND");
        notFound(get("/api/offers?fromVillageId=" + alice.villageId), "VILLAGE_NOT_FOUND");
        notFound(
                post(
                        a + "/offers",
                        """
                        {"offerResource":"WOOD","offerAmount":1000,"wantResource":"CLAY",
                         "wantAmount":1000,"count":1}\
                        """),
                "VILLAGE_NOT_FOUND");
        notFound(delete("/api/offers/" + aliceOffer), "OFFER_NOT_FOUND");
        // accepting with someone else's village (the offer itself is open to everyone)
        notFound(
                post(
                        "/api/offers/" + aliceOffer + "/accept",
                        "{\"villageId\":" + alice.villageId + ",\"count\":1}"),
                "VILLAGE_NOT_FOUND");

        assertThat(Fixture.fingerprint(jdbc, alice.villageId, alice.playerId)).isEqualTo(before);
        // all of them but the accept (its village id is in the body, not in the path)
        assertThat(Fixture.abuseEvents(jdbc, mallory.playerId, "FOREIGN_ID")).isEqualTo(28);
    }

    /** Own method: the suspicion log records at most a burst of events at once. */
    @Test
    void foreignFarmListsAreNotFoundChangeNothingAndAreRecorded() throws Exception {
        String before = Fixture.fingerprint(jdbc, alice.villageId, alice.playerId);
        String a = "/api/villages/" + alice.villageId;
        String m = mallory.villagePath();

        String farm = "{\"units\":{\"SPEARMAN\":1}}";
        notFound(get(a + "/farm-targets"), "VILLAGE_NOT_FOUND");
        notFound(
                post(
                        a + "/farm-targets",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":1}}"
                                .formatted(carolAt.x(), carolAt.y())),
                "VILLAGE_NOT_FOUND");
        notFound(put(a + "/farm-targets/" + aliceFarm, farm), "VILLAGE_NOT_FOUND");
        notFound(put(m + "/farm-targets/" + aliceFarm, farm), "FARM_TARGET_NOT_FOUND");
        notFound(delete(a + "/farm-targets/" + aliceFarm), "VILLAGE_NOT_FOUND");
        notFound(delete(m + "/farm-targets/" + aliceFarm), "FARM_TARGET_NOT_FOUND");
        notFound(post(a + "/farm-targets/" + aliceFarm + "/send", ""), "VILLAGE_NOT_FOUND");
        notFound(post(m + "/farm-targets/" + aliceFarm + "/send", ""), "FARM_TARGET_NOT_FOUND");
        notFound(post(a + "/farm-targets/send-all", ""), "VILLAGE_NOT_FOUND");

        assertThat(Fixture.fingerprint(jdbc, alice.villageId, alice.playerId)).isEqualTo(before);
        assertThat(Fixture.abuseEvents(jdbc, mallory.playerId, "FOREIGN_ID")).isEqualTo(9);
    }

    /** Its own test: one account records at most 30 events at once (AbuseService.RECORD_BURST). */
    @Test
    void foreignResearchIsNotFoundAndRecorded() throws Exception {
        String before = Fixture.fingerprint(jdbc, alice.villageId, alice.playerId);
        String a = "/api/villages/" + alice.villageId;
        String m = mallory.villagePath();

        notFound(get(a + "/smithy"), "VILLAGE_NOT_FOUND");
        notFound(post(a + "/research-orders", "{\"unit\":\"SPEARMAN\"}"), "VILLAGE_NOT_FOUND");
        notFound(delete(a + "/research-orders/" + aliceResearch), "VILLAGE_NOT_FOUND");
        notFound(delete(m + "/research-orders/" + aliceResearch), "RESEARCH_ORDER_NOT_FOUND");

        assertThat(Fixture.fingerprint(jdbc, alice.villageId, alice.playerId)).isEqualTo(before);
        assertThat(Fixture.abuseEvents(jdbc, mallory.playerId, "FOREIGN_ID")).isEqualTo(4);
    }

    /** Its own test, like the research one (the burst limit). */
    @Test
    void foreignBannersAreNotFoundAndRecorded() throws Exception {
        long banner =
                jdbc.sql(
                                """
                                insert into banner (owner_id, type, tier, origin, created_at)
                                values (?, 'WAR', 1, 'GRANTED', now()) returning id
                                """)
                        .param(alice.playerId)
                        .query(Long.class)
                        .single();
        String a = "/api/villages/" + alice.villageId;

        notFound(put(a + "/banner/" + banner, ""), "VILLAGE_NOT_FOUND");
        notFound(delete(a + "/banner"), "VILLAGE_NOT_FOUND");
        notFound(put(mallory.villagePath() + "/banner/" + banner, ""), "BANNER_NOT_FOUND");

        assertThat(
                        jdbc.sql(
                                        "select owner_id || ':' ||"
                                                + " coalesce(equipped_village_id::text, '-') from"
                                                + " banner where id = ?")
                                .param(banner)
                                .query(String.class)
                                .single())
                .isEqualTo(alice.playerId + ":-");
        assertThat(Fixture.abuseEvents(jdbc, mallory.playerId, "FOREIGN_ID")).isEqualTo(3);
    }

    @Test
    void idsThatDontExistAreTheSameAnswerAndNotRecorded() throws Exception {
        notFound(get("/api/villages/999999999"), "VILLAGE_NOT_FOUND");
        notFound(
                delete(mallory.villagePath() + "/build-orders/999999999"), "BUILD_ORDER_NOT_FOUND");
        notFound(
                delete(mallory.villagePath() + "/farm-targets/999999999"), "FARM_TARGET_NOT_FOUND");
        notFound(post("/api/movements/999999999/cancel", ""), "MOVEMENT_NOT_FOUND");
        notFound(post("/api/stacks/999999999/return", units()), "STACK_NOT_FOUND");
        notFound(get("/api/reports/999999999"), "REPORT_NOT_FOUND");
        notFound(delete("/api/offers/999999999"), "OFFER_NOT_FOUND");
        // and an order of her own that has just finished is gone, not foreign
        long own = mallory.enqueueOk(BuildingType.WOODCUTTER);
        jdbc.sql("delete from build_order where id = ?").param(own).update();
        notFound(delete(mallory.villagePath() + "/build-orders/" + own), "BUILD_ORDER_NOT_FOUND");

        assertThat(Fixture.abuseEvents(jdbc, mallory.playerId, "FOREIGN_ID")).isZero();
    }

    @Test
    void playersReachNeitherTheAdminApiNorTheCheats() throws Exception {
        mallory.browser
                .get("/api/admin/status")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mallory.browser
                .post("/api/admin/players/" + mallory.playerId + "/role", "{\"role\":\"ADMIN\"}")
                .andExpect(status().isForbidden());
        mallory.browser
                .post(
                        "/api/admin/cheats/villages/" + mallory.villageId + "/resources",
                        "{\"wood\":1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isForbidden());
        // the dev cheats exist only with the dev profile
        mallory.browser
                .post(
                        "/api/dev/villages/" + mallory.villageId + "/resources",
                        "{\"wood\":100000,\"clay\":1,\"iron\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mallory.browser.get("/api/dev/fail").andExpect(status().isNotFound());
    }

    private static String units() {
        return "{\"units\":{\"SPEARMAN\":1}}";
    }

    private ResultActions get(String path) throws Exception {
        return mallory.browser.get(path);
    }

    private ResultActions post(String path, String json) throws Exception {
        return mallory.browser.post(path, json);
    }

    private ResultActions put(String path, String json) throws Exception {
        return mallory.browser.put(path, json);
    }

    private ResultActions delete(String path) throws Exception {
        return mallory.browser.delete(path);
    }

    private static void notFound(ResultActions actions, String code) throws Exception {
        actions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value("Not found"));
    }
}
