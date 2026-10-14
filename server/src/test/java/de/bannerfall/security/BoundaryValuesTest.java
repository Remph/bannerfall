package de.bannerfall.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M14e: numbers and texts the web client never sends, on the player's own village. Out of range,
 * negative, huge, fractional, overflowing, unknown enum values, broken and oversized bodies: always
 * a 4xx (never a 500), and nothing changes. The 400s land in the suspicion log.
 */
@IntegrationTest
class BoundaryValuesTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;

    private TestPlayer player;
    private String village;
    private Coordinates targetAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
        TestPlayer target = TestPlayer.register(mockMvc);
        targetAt = Fixture.coordinates(target);
        Fixture.richVillage(jdbc, player.villageId);
        Fixture.setHomeUnits(jdbc, player.villageId, "u_spearman", 100);
        Fixture.endProtection(jdbc, target.playerId, clock.instant().minusSeconds(1));
        village = player.villagePath();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void dismissCountsOutsideTheLimitsAreRefused() throws Exception {
        String before = fingerprint();
        for (String count : new String[] {"0", "-1", "30001", "2147483647", "2147483648", "1.5"}) {
            invalid(dismiss("\"SPEARMAN\"", count));
        }
        invalid(dismiss("\"DRAGON\"", "1"));
        invalid(dismiss("null", "1"));
        invalid(dismiss("\"SPEARMAN\"", "null"));
        invalid(player.browser.post(village + "/dismiss-orders", "{\"unit\":\"SPEARMAN\"}"));
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void farmTemplatesOutsideTheLimitsAreRefused() throws Exception {
        long entry = farmOk("{\"SPEARMAN\":5}");
        String before = fingerprint();
        String target = "{\"x\":%d,\"y\":%d}".formatted(targetAt.x(), targetAt.y());
        String list = village + "/farm-targets";
        String one = list + "/" + entry;
        for (String count : new String[] {"-1", "30001", "2147483647", "2147483648", "1.5"}) {
            String units = "{\"SPEARMAN\":" + count + "}";
            invalid(player.browser.put(one, "{\"units\":" + units + "}"));
            invalid(
                    player.browser.post(
                            list, "{\"target\":" + target + ",\"units\":" + units + "}"));
        }
        for (String units :
                new String[] {
                    "{}",
                    "{\"SPEARMAN\":0}",
                    "{\"NOBLE\":1}",
                    "{\"SPEARMAN\":1,\"NOBLE\":1}",
                    "{\"DRAGON\":1}",
                    "{\"SPEARMAN\":null}",
                    "null",
                    "[]",
                    "\"SPEARMAN\""
                }) {
            invalid(player.browser.put(one, "{\"units\":" + units + "}"));
            invalid(
                    player.browser.post(
                            list, "{\"target\":" + target + ",\"units\":" + units + "}"));
        }
        invalid(player.browser.put(one, "{}"));
        invalid(player.browser.post(list, "{\"units\":{\"SPEARMAN\":1}}"));
        invalid(player.browser.post(list, "{\"target\":null,\"units\":{\"SPEARMAN\":1}}"));
        invalid(
                player.browser.post(
                        list, "{\"target\":{\"x\":\"a\",\"y\":1},\"units\":{\"SPEARMAN\":1}}"));
        // a field outside the map is just a field with no village; the list is not for probing
        player.browser
                .post(list, "{\"target\":{\"x\":-5,\"y\":99999999999},\"units\":{\"SPEARMAN\":1}}")
                .andExpect(status().is4xxClientError());
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void aFarmListHoldsFiftyTargets() throws Exception {
        List<Fixture.Spot> spots = Fixture.barbarianSpots(jdbc, 51);
        try {
            Fixture.fillFarmList(jdbc, player.villageId, spots.subList(0, 50));
            String before = fingerprint();
            Coordinates last = spots.getLast().at();
            String add =
                    "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":1}}"
                            .formatted(last.x(), last.y());

            player.browser
                    .post(village + "/farm-targets", add)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FARM_LIST_FULL"));
            assertThat(fingerprint()).isEqualTo(before);

            // room again after one is taken off
            long first =
                    jdbc.sql("select min(id) from farm_target where village_id = ?")
                            .param(player.villageId)
                            .query(Long.class)
                            .single();
            player.browser
                    .delete(village + "/farm-targets/" + first)
                    .andExpect(status().isNoContent());
            player.browser.post(village + "/farm-targets", add).andExpect(status().isCreated());
        } finally {
            Fixture.removeSpots(jdbc, spots);
        }
    }

    @Test
    void recruitCountsOutsideTheLimitsAreRefused() throws Exception {
        String before = fingerprint();
        for (String count : new String[] {"0", "-1", "30001", "2147483647", "2147483648", "1.5"}) {
            invalid(recruit("\"SPEARMAN\"", count));
        }
        invalid(recruit("\"DRAGON\"", "1"));
        invalid(recruit("null", "1"));
        invalid(recruit("\"SPEARMAN\"", "null"));
        invalid(player.browser.post(village + "/recruit-orders", "{\"unit\":\"SPEARMAN\"}"));
        assertThat(fingerprint()).isEqualTo(before);
        assertThat(
                        Fixture.abuseEvents(jdbc, player.playerId, "INVALID_INPUT")
                                + Fixture.abuseEvents(jdbc, player.playerId, "MALFORMED_BODY"))
                .isEqualTo(10);
    }

    @Test
    void recruitAllListsOutsideTheLimitsAreRefused() throws Exception {
        String before = fingerprint();
        String spear = "{\"unit\":\"SPEARMAN\",\"count\":1}";
        invalid(recruitAll("[]"));
        invalid(recruitAll("null"));
        invalid(recruitAll("[null]"));
        invalid(recruitAll("[" + spear + "," + spear + "]")); // the same unit twice
        invalid(recruitAll("[{\"unit\":\"SPEARMAN\",\"count\":30001}]"));
        invalid(recruitAll("[{\"unit\":\"SPEARMAN\",\"count\":0}]"));
        invalid(recruitAll("[{\"unit\":\"DRAGON\",\"count\":1}]"));
        invalid(player.browser.post(village + "/recruit-orders/all", "{}"));
        // more entries than unit types
        String ten = String.join(",", Collections.nCopies(10, spear));
        invalid(recruitAll("[" + ten + "]"));
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void buildOrdersTakeOnlyKnownBuildingsAndRealPositions() throws Exception {
        long order = player.enqueueOk(BuildingType.WOODCUTTER);
        String before = fingerprint();
        invalid(player.browser.post(village + "/build-orders", "{\"building\":\"CASTLE\"}"));
        invalid(player.browser.post(village + "/build-orders", "{\"building\":null}"));
        invalid(player.browser.post(village + "/build-orders", "{}"));
        invalid(player.move(order, -1));
        invalid(
                player.browser.post(
                        village + "/build-orders/" + order + "/move", "{\"position\":\"x\"}"));
        player.move(order, Integer.MAX_VALUE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_MOVABLE"));
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void commandsTakeOnlyRealUnitCounts() throws Exception {
        String before = fingerprint();
        invalid(command("ATTACK", "{\"SPEARMAN\":-1}"));
        invalid(command("ATTACK", "{\"DRAGON\":1}"));
        invalid(command("ATTACK", "{\"SPEARMAN\":null}"));
        invalid(command("ATTACK", "{\"SPEARMAN\":1.5}"));
        invalid(command("RETURN", "{\"SPEARMAN\":1}"));
        invalid(command("ATTACK", "[1,2]"));
        // sums far beyond the int range: more than at home, nothing overflows
        String huge = "{\"SPEARMAN\":2147483647,\"AXEMAN\":2147483647,\"NOBLE\":2147483647}";
        command("ATTACK", huge)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        player.browser
                .post(village + "/commands/preview", commandJson("ATTACK", huge, targetAt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0]").value("NOT_ENOUGH_UNITS"));
        // a target far off the map
        player.browser
                .post(
                        village + "/commands",
                        commandJson(
                                "ATTACK",
                                "{\"SPEARMAN\":1}",
                                new Coordinates(Integer.MAX_VALUE, Integer.MIN_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        invalid(
                player.browser.post(
                        village + "/commands",
                        "{\"kind\":\"ATTACK\",\"target\":{\"x\":1},\"units\":{\"SPEARMAN\":1},"
                                + "\"catapultTarget\":\"MOON\"}"));
        // withdrawing from the own home stack is no support
        long homeStack =
                jdbc.sql(
                                "select id from unit_stack where home_village_id = ?"
                                        + " and location_village_id = ?")
                        .params(player.villageId, player.villageId)
                        .query(Long.class)
                        .single();
        invalid(player.returnStack(homeStack, "{\"SPEARMAN\":-5}"));
        player.returnStack(homeStack, "{\"SPEARMAN\":5}").andExpect(status().isNotFound());
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void marketAmountsOutsideTheLimitsAreRefused() throws Exception {
        String before = fingerprint();
        invalid(transport(-1, 0, 0));
        invalid(transport(1_000_001, 0, 0));
        transport(0, 0, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_TRANSPORT"));
        invalid(offer("WOOD", 0, "CLAY", 1000, 1));
        invalid(offer("WOOD", 1000, "CLAY", 1000, 0));
        invalid(offer("WOOD", 1000, "CLAY", 1000, 1001));
        invalid(offer("GOLD", 1000, "CLAY", 1000, 1));
        offer("WOOD", 1000, "WOOD", 1000, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_RESOURCE"));
        // the most an offer may ask for: count × amount stays an int, nothing to pay that much
        offer("WOOD", 1_000_000, "CLAY", 1_000_000, 1000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_MERCHANTS"));
        invalid(player.browser.post("/api/offers/1/accept", "{\"villageId\":1,\"count\":0}"));
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void villageNamesAreShortAndPrintable() throws Exception {
        String before = fingerprint();
        for (String name : new String[] {"", "   ", "x".repeat(33), "a\\u0000b", "line\\nbreak"}) {
            invalid(player.browser.patch(village, "{\"name\":\"" + name + "\"}"));
        }
        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void queryParametersStayInTheirRange() throws Exception {
        invalid(player.browser.get("/api/map?x=-1&y=0&w=20&h=20"));
        invalid(player.browser.get("/api/map?x=0&y=0&w=61&h=20"));
        invalid(player.browser.get("/api/map?x=0&y=0&w=0&h=20"));
        player.browser
                .get("/api/map?x=2147483647&y=2147483647&w=60&h=60")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        invalid(player.browser.get("/api/reports?page=-1"));
        invalid(player.browser.get("/api/reports?size=101"));
        invalid(player.browser.get("/api/reports?page=10001"));
        invalid(player.browser.get("/api/ranking?page=-1"));
        player.browser
                .get("/api/ranking?page=2147483647&size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty());
        invalid(player.browser.get("/api/chat/messages?before=0"));
        invalid(player.browser.get("/api/messages/with/abc"));
        invalid(player.browser.get("/api/messages/with/99999999999999999999"));
        invalid(player.browser.get("/api/messages/with/" + player.playerId + "?before=0"));
        invalid(player.browser.put("/api/blocks/abc", ""));
        invalid(player.browser.get("/api/villages/abc"));
        invalid(player.browser.get("/api/villages/99999999999999999999"));
        invalid(player.browser.get("/api/offers?fromVillageId=x"));
    }

    @Test
    void privateMessagesRefuseBadTextAndBodies() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        String path = "/api/messages/with/" + other.playerId;
        invalid(player.browser.post(path, "{\"text\":"));
        invalid(player.browser.post(path, ""));
        invalid(player.browser.post(path, "{}"));
        invalid(player.browser.post(path, "{\"text\":null}"));
        invalid(player.browser.post(path, "{\"text\":\"\"}"));
        invalid(player.browser.post(path, "{\"text\":\"" + "x".repeat(501) + "\"}"));
        invalid(player.browser.post(path, "{\"text\":\"bell\\u0007\"}"));
        // nothing was stored or delivered
        other.browser
                .get("/api/messages/unread")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void brokenAndOversizedBodiesAreRefused() throws Exception {
        String before = fingerprint();
        invalid(player.browser.post(village + "/build-orders", "{\"building\":"));
        invalid(player.browser.post(village + "/build-orders", ""));
        invalid(player.browser.post(village + "/build-orders", "\"WOODCUTTER\""));
        // unknown fields are ignored: the order goes through like without them
        player.browser
                .post(village + "/build-orders", "{\"building\":\"WOODCUTTER\",\"free\":true}")
                .andExpect(status().isCreated());
        String afterOne = fingerprint();
        assertThat(afterOne).isNotEqualTo(before);

        String padding = " ".repeat(70 * 1024);
        player.browser
                .post(village + "/build-orders", "{\"building\":\"WOODCUTTER\"" + padding + "}")
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("BODY_TOO_LARGE"));
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        village + "/build-orders")
                                .session(player.browser.session())
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "XSRF-TOKEN", player.browser.csrfToken()))
                                .header("X-XSRF-TOKEN", player.browser.csrfToken())
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("building=WOODCUTTER"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(fingerprint()).isEqualTo(afterOne);
    }

    // ---- helpers ----

    private String fingerprint() {
        return Fixture.fingerprint(jdbc, player.villageId, player.playerId);
    }

    /** Puts the target on the village's farm list; the id of the entry. */
    private long farmOk(String units) throws Exception {
        String answer =
                TestPlayer.body(
                        player.browser
                                .post(
                                        village + "/farm-targets",
                                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":%s}"
                                                .formatted(targetAt.x(), targetAt.y(), units))
                                .andExpect(status().isCreated()));
        return TestPlayer.number(answer, "$.id").longValue();
    }

    private ResultActions recruitAll(String orders) throws Exception {
        return player.browser.post(village + "/recruit-orders/all", "{\"orders\":" + orders + "}");
    }

    private ResultActions recruit(String unit, String count) throws Exception {
        return player.browser.post(
                village + "/recruit-orders", "{\"unit\":" + unit + ",\"count\":" + count + "}");
    }

    private ResultActions dismiss(String unit, String count) throws Exception {
        return player.browser.post(
                village + "/dismiss-orders", "{\"unit\":" + unit + ",\"count\":" + count + "}");
    }

    private ResultActions command(String kind, String units) throws Exception {
        return player.browser.post(village + "/commands", commandJson(kind, units, targetAt));
    }

    private static String commandJson(String kind, String units, Coordinates at) {
        return "{\"kind\":\"%s\",\"target\":{\"x\":%d,\"y\":%d},\"units\":%s}"
                .formatted(kind, at.x(), at.y(), units);
    }

    private ResultActions transport(int wood, int clay, int iron) throws Exception {
        return player.browser.post(
                village + "/transports",
                "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":%d,\"clay\":%d,\"iron\":%d}"
                        .formatted(targetAt.x(), targetAt.y(), wood, clay, iron));
    }

    private ResultActions offer(String give, int amount, String want, int wantAmount, int count)
            throws Exception {
        return player.browser.post(
                village + "/offers",
                """
                {"offerResource":"%s","offerAmount":%d,"wantResource":"%s","wantAmount":%d,\
                "count":%d}\
                """
                        .formatted(give, amount, want, wantAmount, count));
    }

    private static void invalid(ResultActions actions) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
}
