package de.bannerfall.cheat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.GameEvent;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.event.GameEventRepository;
import de.bannerfall.event.GameEventStatus;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The dev cheats (04-api). They only exist with the profile {@code dev}, so this test class gets
 * its own application context (and database) with that profile; {@code BuildControllerTest} checks
 * that the paths are 404 without it. The profile also switches the host cheats on ({@code
 * application-dev.yml}), so they are tested here too ({@code HostConsoleTest}: off).
 */
@IntegrationTest
@ActiveProfiles("dev")
class DevCheatControllerTest {

    private static final double EPSILON = 1e-9;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired GameEventRepository events;

    private TestPlayer player;

    @BeforeEach
    void registerPlayer() throws Exception {
        jdbc.sql("delete from game_event").update();
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    private String cheatPath(String cheat) {
        return "/api/dev/villages/" + player.villageId + "/" + cheat;
    }

    @Test
    void resourcesAreAddedWithoutLookingAtTheCapacity() throws Exception {
        player.browser
                .post(cheatPath("resources"), "{\"wood\":5000,\"clay\":10,\"iron\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageCapacity").value(1000))
                .andExpect(jsonPath("$.resources.wood", closeTo(5500, EPSILON)))
                .andExpect(jsonPath("$.resources.clay", closeTo(510, EPSILON)))
                .andExpect(jsonPath("$.resources.iron", closeTo(500, EPSILON)));

        // and they stay, production does not cut the stock back to the capacity
        player.village().andExpect(jsonPath("$.resources.wood", closeTo(5500, EPSILON)));
    }

    @Test
    void negativeOrHugeAmountsAreRejected() throws Exception {
        player.browser
                .post(cheatPath("resources"), "{\"wood\":-1,\"clay\":0,\"iron\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("wood"));
        player.browser
                .post(cheatPath("resources"), "{\"wood\":0,\"clay\":2000000000,\"iron\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void finishingTheOrdersCompletesTheWholeQueueNow() throws Exception {
        player.browser
                .post(cheatPath("resources"), "{\"wood\":5000,\"clay\":5000,\"iron\":5000}")
                .andExpect(status().isOk());
        player.enqueueOk(BuildingType.WOODCUTTER);
        player.enqueueOk(BuildingType.CLAY_PIT);
        player.enqueueOk(BuildingType.IRON_MINE);
        player.village().andExpect(jsonPath("$.buildQueue.length()").value(3));

        player.browser
                .post(cheatPath("finish-orders"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildQueue").isEmpty())
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(2))
                .andExpect(jsonPath("$.buildings.CLAY_PIT").value(2))
                .andExpect(jsonPath("$.buildings.IRON_MINE").value(2))
                // 39 points at the start, three upgrades worth 7 points each
                .andExpect(jsonPath("$.points").value(60));

        List<GameEvent> completions =
                events.findAll().stream()
                        .filter(e -> e.getKind() == GameEventKind.BUILD_COMPLETE)
                        .toList();
        assertThat(completions).hasSize(3);
        assertThat(completions).allMatch(e -> e.getStatus() == GameEventStatus.DONE);
        // the village is changed for good, not only in the answer
        assertThat(body(player.village())).contains("\"buildQueue\":[]");
    }

    @Test
    void unitsAreAddedToTheHomeStackWithoutLookingAtTheFarm() throws Exception {
        player.browser
                .post(cheatPath("units"), "{\"units\":{\"SPEARMAN\":300,\"NOBLE\":1}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(300))
                .andExpect(jsonPath("$.unitsHome.NOBLE").value(1))
                // buildings 30 + 300 + 100: more than the farm's 240
                .andExpect(jsonPath("$.population.used").value(430));

        player.browser
                .post(cheatPath("units"), "{\"units\":{\"SPEARMAN\":5}}")
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(305));
    }

    @Test
    void negativeHugeOrUnknownUnitsAreRejected() throws Exception {
        for (String body :
                new String[] {
                    "{\"units\":{\"SPEARMAN\":-1}}",
                    "{\"units\":{\"SPEARMAN\":100001}}",
                    "{\"units\":{\"DRAGON\":1}}",
                    "{}"
                }) {
            player.browser
                    .post(cheatPath("units"), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
        player.village().andExpect(jsonPath("$.unitsHome").isEmpty());
    }

    @Test
    void finishingTheOrdersAlsoFinishesRecruitment() throws Exception {
        jdbc.sql("update village set b_barracks = 1 where id = ?")
                .params(player.villageId)
                .update();
        player.recruitOk(UnitType.SPEARMAN, 3);
        player.recruitOk(UnitType.SPEARMAN, 2);

        player.browser
                .post(cheatPath("finish-orders"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(5))
                // buildings 30 + barracks 7 + 5 spearmen, as before
                .andExpect(jsonPath("$.population.used").value(42));
        player.recruitment().andExpect(jsonPath("$[0].queue").isEmpty());
    }

    @Test
    void finishingTheOrdersAlsoEndsDismissals() throws Exception {
        jdbc.sql("update village set b_barracks = 1 where id = ?")
                .params(player.villageId)
                .update();
        jdbc.sql("update unit_stack set u_spearman = 10 where home_village_id = ?")
                .params(player.villageId)
                .update();
        player.dismissOk(UnitType.SPEARMAN, 4);

        player.browser
                .post(cheatPath("finish-orders"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(6))
                // buildings 30 + barracks 7 + 6 spearmen: the four dismissed ones are gone
                .andExpect(jsonPath("$.population.used").value(43));
        player.recruitment().andExpect(jsonPath("$[0].dismissQueue").isEmpty());
    }

    @Test
    void finishingTheOrdersAlsoFinishesAResearch() throws Exception {
        jdbc.sql(
                        """
                        insert into research_order (player_id, village_id, unit, level, start_at,
                            end_at, cost_wood, cost_clay, cost_iron, created_at)
                        values (?, ?, 'SPEARMAN', 2, now(), now() + interval '1 day', 0, 0, 0, now())
                        """)
                .params(player.playerId, player.villageId)
                .update();

        player.browser.post(cheatPath("finish-orders"), "").andExpect(status().isOk());

        assertThat(
                        jdbc.sql(
                                        "select count(*) from research_order where player_id = ?"
                                                + " and end_at < now() + interval '1 hour'")
                                .param(player.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void finishingAnEmptyQueueChangesNothing() throws Exception {
        player.browser
                .post(cheatPath("finish-orders"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildQueue").isEmpty())
                .andExpect(jsonPath("$.points").value(39));
    }

    @Test
    void cheatsWorkOnOwnVillagesOnly() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);

        other.browser
                .post(cheatPath("resources"), "{\"wood\":1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(cheatPath("units"), "{\"units\":{\"SPEARMAN\":1}}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(cheatPath("finish-orders"), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        other.browser
                .post(cheatPath("finish-movements"), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    @Test
    void finishingTheMovementsLetsOwnTroopsArriveNow() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        String otherVillage = body(other.village());
        Coordinates target =
                new Coordinates(
                        number(otherVillage, "$.x").intValue(),
                        number(otherVillage, "$.y").intValue());
        player.browser
                .post(cheatPath("units"), "{\"units\":{\"SPEARMAN\":10}}")
                .andExpect(status().isOk());
        player.command("SUPPORT", target, "{\"SPEARMAN\":10}").andExpect(status().isCreated());

        // foreign movements coming in are not touched
        other.browser
                .post("/api/dev/villages/" + other.villageId + "/finish-movements", "")
                .andExpect(status().isOk());
        other.troops().andExpect(jsonPath("$.supportsHere").isEmpty());

        player.browser.post(cheatPath("finish-movements"), "").andExpect(status().isOk());
        player.troops()
                .andExpect(jsonPath("$.outgoing").isEmpty())
                .andExpect(jsonPath("$.ownUnitsElsewhere[0].units.SPEARMAN").value(10));
        other.troops().andExpect(jsonPath("$.supportsHere[0].units.SPEARMAN").value(10));
    }

    @Test
    void finishingTheMovementsAlsoLetsSentMerchantsArrive() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);
        String otherVillage = body(other.village());
        jdbc.sql("update village set b_market = 5 where id = ?").params(player.villageId).update();
        player.browser
                .post(
                        "/api/villages/" + player.villageId + "/transports",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":100,\"clay\":0,\"iron\":0}"
                                .formatted(
                                        number(otherVillage, "$.x").intValue(),
                                        number(otherVillage, "$.y").intValue()))
                .andExpect(status().isCreated());
        other.browser
                .get(other.villagePath() + "/market")
                .andExpect(jsonPath("$.incoming[0].resources.wood").value(100));

        player.browser.post(cheatPath("finish-movements"), "").andExpect(status().isOk());

        other.browser
                .get(other.villagePath() + "/market")
                .andExpect(jsonPath("$.incoming").isEmpty());
        assertThat(other.reportKinds()).contains("TRADE_ARRIVED");
    }

    @Test
    void aBarbarianVillageCanBePutOnAFreeField() throws Exception {
        // the top left corner: registered players spawn around the centre
        player.browser
                .post("/api/dev/barbarians", "{\"x\":3,\"y\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.x").value(3))
                .andExpect(jsonPath("$.y").value(3))
                .andExpect(jsonPath("$.name").isEmpty())
                .andExpect(jsonPath("$.playerId").isEmpty());
        assertThat(
                        jdbc.sql(
                                        "select count(*) from village where x = 3 and y = 3 and"
                                                + " player_id is null")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);

        player.browser
                .post("/api/dev/barbarians", "{\"x\":3,\"y\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_NOT_FREE"));
        player.browser
                .post(
                        "/api/dev/barbarians",
                        "{\"x\":" + TestWorldConfiguration.MAP_SIZE + ",\"y\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_NOT_FREE"));
        player.browser
                .post("/api/dev/barbarians", "{\"x\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ---- host cheats (08-host-console §3): the dev profile switches them on ----

    private ResultActions host(String cheat, long villageId, String json) throws Exception {
        return mockMvc.perform(
                post("/api/admin/cheats/villages/" + villageId + "/" + cheat)
                        .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private String lastAudit() {
        return jdbc.sql(
                        "select actor || ' ' || action || ' ' || target_id || ' ' || details::text"
                                + " from admin_action order by id desc limit 1")
                .query(String.class)
                .single();
    }

    @Test
    void theHostCheatsWorkOnAnyVillageAndAreAudited() throws Exception {
        TestPlayer other = TestPlayer.register(mockMvc);

        host("resources", other.villageId, "{\"wood\":1000,\"clay\":0,\"iron\":0}")
                .andExpect(status().isNoContent());
        other.village().andExpect(jsonPath("$.resources.wood", closeTo(1500, EPSILON)));
        assertThat(lastAudit())
                .startsWith("host-cli CHEAT_RESOURCES " + other.villageId)
                .contains("\"wood\": 1000");

        host("units", other.villageId, "{\"units\":{\"AXEMAN\":7}}")
                .andExpect(status().isNoContent());
        other.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(7));
        assertThat(lastAudit()).contains("CHEAT_UNITS").contains("\"AXEMAN\": 7");

        host("resources", other.villageId, "{\"wood\":5000,\"clay\":5000,\"iron\":5000}");
        other.enqueueOk(BuildingType.WOODCUTTER);
        host("finish-orders", other.villageId, "").andExpect(status().isNoContent());
        other.village()
                .andExpect(jsonPath("$.buildQueue").isEmpty())
                .andExpect(jsonPath("$.buildings.WOODCUTTER").value(2));
        assertThat(lastAudit()).contains("CHEAT_FINISH_ORDERS " + other.villageId);

        host("finish-movements", other.villageId, "").andExpect(status().isNoContent());
        assertThat(lastAudit()).contains("CHEAT_FINISH_MOVEMENTS");
    }

    @Test
    void hostCheatsCheckTheirInputAndTheVillage() throws Exception {
        host("resources", 999_999_999, "{\"wood\":1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        host("resources", player.villageId, "{\"wood\":-1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isBadRequest());
        // a player's session gets no further than the role check
        player.browser
                .post(
                        "/api/admin/cheats/villages/" + player.villageId + "/resources",
                        "{\"wood\":1,\"clay\":1,\"iron\":1}")
                .andExpect(status().isForbidden());
    }

    @Test
    void theFailEndpointAnswersAsAnUnexpectedError() throws Exception {
        // logs one ERROR with a stack trace (on purpose)
        player.browser
                .get("/api/dev/fail")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(header().exists("X-Request-Id"));
    }
}
