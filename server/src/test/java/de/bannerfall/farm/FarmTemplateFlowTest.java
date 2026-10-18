package de.bannerfall.farm;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Farm templates (02-game-rules §18, 1.4): A to E per player, shared by his villages; an entry uses
 * one or its own units, and follows its template's changes.
 */
@IntegrationTest
class FarmTemplateFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;

    private TestPlayer farmer;
    private TestPlayer victim;
    private Coordinates victimAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        farmer = TestPlayer.register(mockMvc);
        victim = TestPlayer.register(mockMvc);
        victimAt = coordinates(victim);
        jdbc.sql("update unit_stack set u_axeman = 20 where home_village_id = ?")
                .params(farmer.villageId)
                .update();
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), victim.playerId)
                .update();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    private static String targets(TestPlayer player) {
        return player.villagePath() + "/farm-targets";
    }

    private void saveTemplate(TestPlayer player, String letter, String units) throws Exception {
        player.browser
                .put("/api/farm-templates/" + letter, "{\"units\":" + units + "}")
                .andExpect(status().isOk());
    }

    private long addWithTemplate(TestPlayer player, Coordinates at, String letter)
            throws Exception {
        String body =
                "{\"target\":{\"x\":%d,\"y\":%d},\"units\":null,\"template\":\"%s\"}"
                        .formatted(at.x(), at.y(), letter);
        return number(
                        body(
                                player.browser
                                        .post(targets(player), body)
                                        .andExpect(status().isCreated())),
                        "$.id")
                .longValue();
    }

    @Test
    void templatesAreMadeChangedListedAndDeleted() throws Exception {
        farmer.browser.get("/api/farm-templates").andExpect(jsonPath("$", hasSize(0)));

        saveTemplate(farmer, "B", "{\"AXEMAN\":10,\"SPEARMAN\":0}");
        saveTemplate(farmer, "A", "{\"LIGHT_CAVALRY\":5}");
        farmer.browser
                .get("/api/farm-templates")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].letter").value("A"))
                .andExpect(jsonPath("$[1].letter").value("B"))
                .andExpect(jsonPath("$[1].units.AXEMAN").value(10))
                .andExpect(jsonPath("$[1].units.SPEARMAN").doesNotExist());

        saveTemplate(farmer, "B", "{\"AXEMAN\":12}");
        farmer.browser
                .get("/api/farm-templates")
                .andExpect(jsonPath("$[1].units.AXEMAN").value(12));

        farmer.browser.delete("/api/farm-templates/A").andExpect(status().isNoContent());
        farmer.browser
                .delete("/api/farm-templates/A")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_TEMPLATE_NOT_FOUND"));
        farmer.browser.get("/api/farm-templates").andExpect(jsonPath("$", hasSize(1)));
        // another player's templates are his own
        victim.browser.get("/api/farm-templates").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anAttackFromTheMapCountsAsTheEntrysLastSend() throws Exception {
        saveTemplate(farmer, "A", "{\"AXEMAN\":5}");
        addWithTemplate(farmer, victimAt, "A");
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].lastSentAt").value(nullValue()));

        // the map's "Farm with A" sends the template as an ordinary attack
        clock.advance(Duration.ofMinutes(3));
        Instant now = clock.instant();
        farmer.browser
                .post(
                        farmer.villagePath() + "/commands",
                        "{\"kind\":\"ATTACK\",\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"AXEMAN\":5}}"
                                .formatted(victimAt.x(), victimAt.y()))
                .andExpect(status().isCreated());
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].lastSentAt").value(now.toString()));

        // a support isn't a farm run
        clock.advance(Duration.ofMinutes(3));
        farmer.browser
                .post(
                        farmer.villagePath() + "/commands",
                        "{\"kind\":\"SUPPORT\",\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"AXEMAN\":5}}"
                                .formatted(victimAt.x(), victimAt.y()))
                .andExpect(status().isCreated());
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].lastSentAt").value(now.toString()));
    }

    @Test
    void aTemplateFollowsTheRulesOfOwnUnits() throws Exception {
        farmer.browser
                .put("/api/farm-templates/A", "{\"units\":{}}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .put("/api/farm-templates/A", "{\"units\":{\"NOBLE\":1}}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .put("/api/farm-templates/A", "{\"units\":{\"PALADIN\":1}}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .put("/api/farm-templates/A", "{\"units\":{\"AXEMAN\":30001}}")
                .andExpect(status().isBadRequest());
        // five letters, no more
        farmer.browser
                .put("/api/farm-templates/F", "{\"units\":{\"AXEMAN\":1}}")
                .andExpect(status().isBadRequest());
        farmer.browser.get("/api/farm-templates").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anEntryUsesATemplateAndFollowsItsChanges() throws Exception {
        saveTemplate(farmer, "A", "{\"AXEMAN\":5}");
        long id = addWithTemplate(farmer, victimAt, "A");
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].template").value("A"))
                .andExpect(jsonPath("$[0].units.AXEMAN").value(5));

        saveTemplate(farmer, "A", "{\"AXEMAN\":15}");
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].template").value("A"))
                .andExpect(jsonPath("$[0].units.AXEMAN").value(15));

        // the send takes the template's new units
        farmer.browser
                .post(targets(farmer) + "/" + id + "/send", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.units.AXEMAN").value(15));
    }

    @Test
    void aTemplateIsSharedByAllVillagesOfThePlayer() throws Exception {
        // a third player's village becomes the farmer's second one
        TestPlayer other = TestPlayer.register(mockMvc);
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(farmer.playerId, other.villageId)
                .update();
        String secondTargets = "/api/villages/" + other.villageId + "/farm-targets";

        saveTemplate(farmer, "B", "{\"AXEMAN\":3}");
        addWithTemplate(farmer, victimAt, "B");
        farmer.browser
                .post(
                        secondTargets,
                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":null,\"template\":\"B\"}"
                                .formatted(victimAt.x(), victimAt.y()))
                .andExpect(status().isCreated());

        saveTemplate(farmer, "B", "{\"AXEMAN\":4,\"SCOUT\":1}");
        farmer.browser.get(targets(farmer)).andExpect(jsonPath("$[0].units.AXEMAN").value(4));
        farmer.browser
                .get(secondTargets)
                .andExpect(jsonPath("$[0].units.AXEMAN").value(4))
                .andExpect(jsonPath("$[0].units.SCOUT").value(1));
    }

    @Test
    void aDeletedTemplateLeavesItsEntriesWithTheirUnits() throws Exception {
        saveTemplate(farmer, "C", "{\"AXEMAN\":7}");
        addWithTemplate(farmer, victimAt, "C");
        farmer.browser.delete("/api/farm-templates/C").andExpect(status().isNoContent());
        farmer.browser
                .get(targets(farmer))
                .andExpect(jsonPath("$[0].template").value(nullValue()))
                .andExpect(jsonPath("$[0].units.AXEMAN").value(7));

        // a template made again under the same letter doesn't pick them up
        saveTemplate(farmer, "C", "{\"AXEMAN\":9}");
        farmer.browser.get(targets(farmer)).andExpect(jsonPath("$[0].units.AXEMAN").value(7));
    }

    @Test
    void anEntrySwitchesBetweenTemplatesAndOwnUnits() throws Exception {
        saveTemplate(farmer, "A", "{\"AXEMAN\":5}");
        long id =
                number(
                                body(
                                        farmer.browser.post(
                                                targets(farmer),
                                                "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"AXEMAN\":2}}"
                                                        .formatted(victimAt.x(), victimAt.y()))),
                                "$.id")
                        .longValue();
        farmer.browser.get(targets(farmer)).andExpect(jsonPath("$[0].template").value(nullValue()));

        farmer.browser
                .put(targets(farmer) + "/" + id, "{\"units\":null,\"template\":\"A\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("A"))
                .andExpect(jsonPath("$.units.AXEMAN").value(5));

        farmer.browser
                .put(targets(farmer) + "/" + id, "{\"units\":{\"AXEMAN\":8},\"template\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value(nullValue()))
                .andExpect(jsonPath("$.units.AXEMAN").value(8));
        // own units no longer follow the template
        saveTemplate(farmer, "A", "{\"AXEMAN\":6}");
        farmer.browser.get(targets(farmer)).andExpect(jsonPath("$[0].units.AXEMAN").value(8));
    }

    @Test
    void unitsAndTemplateAreExclusiveAndTheTemplateMustExist() throws Exception {
        String at = "\"target\":{\"x\":%d,\"y\":%d}".formatted(victimAt.x(), victimAt.y());
        saveTemplate(farmer, "A", "{\"AXEMAN\":5}");
        farmer.browser
                .post(targets(farmer), "{" + at + ",\"units\":{\"AXEMAN\":1},\"template\":\"A\"}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .post(targets(farmer), "{" + at + ",\"units\":null,\"template\":null}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .post(targets(farmer), "{" + at + ",\"units\":null,\"template\":\"D\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_TEMPLATE_NOT_FOUND"));
        // another player's template A is not the farmer's
        victim.browser
                .put("/api/farm-templates/A", "{\"units\":{\"SPEARMAN\":1}}")
                .andExpect(status().isOk());
        farmer.browser.get(targets(farmer)).andExpect(jsonPath("$", hasSize(0)));
    }
}
