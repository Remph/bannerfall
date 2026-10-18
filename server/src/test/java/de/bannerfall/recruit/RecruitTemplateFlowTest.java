package de.bannerfall.recruit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Recruit templates (02-game-rules §5.2b, 1.4): A to E per player, each a name and a goal army,
 * shared by his villages.
 */
@IntegrationTest
class RecruitTemplateFlowTest {

    private static final String TEMPLATES = "/api/recruit-templates";

    @Autowired MockMvc mockMvc;

    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        player = TestPlayer.register(mockMvc);
    }

    private void save(String letter, String body) throws Exception {
        player.browser.put(TEMPLATES + "/" + letter, body).andExpect(status().isOk());
    }

    private void refused(String letter, String body) throws Exception {
        player.browser
                .put(TEMPLATES + "/" + letter, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void templatesAreMadeChangedListedAndDeleted() throws Exception {
        player.browser.get(TEMPLATES).andExpect(jsonPath("$", hasSize(0)));

        save("B", "{\"name\":\"  Verteidigung \",\"units\":{\"SPEARMAN\":4000,\"AXEMAN\":0}}");
        player.browser
                .put(TEMPLATES + "/A", "{\"name\":\"Angriff\",\"units\":{\"AXEMAN\":3000}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letter").value("A"))
                .andExpect(jsonPath("$.name").value("Angriff"))
                .andExpect(jsonPath("$.units.AXEMAN").value(3000));
        player.browser
                .get(TEMPLATES)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].letter").value("A"))
                .andExpect(jsonPath("$[1].letter").value("B"))
                // trimmed, and only the unit types above 0
                .andExpect(jsonPath("$[1].name").value("Verteidigung"))
                .andExpect(jsonPath("$[1].units.SPEARMAN").value(4000))
                .andExpect(jsonPath("$[1].units.AXEMAN").doesNotExist());

        save("A", "{\"name\":\"Off\",\"units\":{\"LIGHT_CAVALRY\":1200}}");
        player.browser
                .get(TEMPLATES)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Off"))
                .andExpect(jsonPath("$[0].units.LIGHT_CAVALRY").value(1200))
                .andExpect(jsonPath("$[0].units.AXEMAN").doesNotExist());

        player.browser.delete(TEMPLATES + "/A").andExpect(status().isNoContent());
        player.browser
                .delete(TEMPLATES + "/A")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_TEMPLATE_NOT_FOUND"));
        player.browser.get(TEMPLATES).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void anotherPlayersTemplatesAreHisOwn() throws Exception {
        save("A", "{\"name\":\"Angriff\",\"units\":{\"AXEMAN\":3000}}");
        TestPlayer other = TestPlayer.register(mockMvc);
        other.browser.get(TEMPLATES).andExpect(jsonPath("$", hasSize(0)));
        other.browser
                .delete(TEMPLATES + "/A")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECRUIT_TEMPLATE_NOT_FOUND"));
        // his own A doesn't touch the first player's
        other.browser
                .put(TEMPLATES + "/A", "{\"name\":\"Deff\",\"units\":{\"SPEARMAN\":10}}")
                .andExpect(status().isOk());
        player.browser.get(TEMPLATES).andExpect(jsonPath("$[0].name").value("Angriff"));
    }

    @Test
    void everyUnitTypeMayBePartOfTheGoal() throws Exception {
        // the template only says what the village should have; recruiting checks the rest
        save("C", "{\"name\":\"Adel\",\"units\":{\"NOBLE\":4,\"PALADIN\":1,\"BANNER_THIEF\":20}}");
        player.browser
                .get(TEMPLATES)
                .andExpect(jsonPath("$[0].units.NOBLE").value(4))
                .andExpect(jsonPath("$[0].units.PALADIN").value(1))
                .andExpect(jsonPath("$[0].units.BANNER_THIEF").value(20));
    }

    @Test
    void templatesOutsideTheLimitsAreRefused() throws Exception {
        refused("A", "{\"name\":\"Leer\",\"units\":{}}");
        refused("A", "{\"name\":\"Leer\",\"units\":{\"AXEMAN\":0}}");
        refused("A", "{\"name\":\"Zu viel\",\"units\":{\"AXEMAN\":30001}}");
        refused("A", "{\"name\":\"Minus\",\"units\":{\"AXEMAN\":-1}}");
        refused("A", "{\"name\":\"Kein Typ\",\"units\":{\"AXEMAN\":null}}");
        refused("A", "{\"name\":\"Ohne\"}");
        refused("A", "{\"name\":\"   \",\"units\":{\"AXEMAN\":1}}");
        refused("A", "{\"units\":{\"AXEMAN\":1}}");
        refused("A", "{\"name\":\"Siebzehn Zeichen!\",\"units\":{\"AXEMAN\":1}}");
        refused("A", "{\"name\":\"Zeile\\nzwei\",\"units\":{\"AXEMAN\":1}}");
        refused("A", "{\"name\":\"Drache\",\"units\":{\"DRAGON\":1}}");
        // five letters, no more
        refused("F", "{\"name\":\"Sechs\",\"units\":{\"AXEMAN\":1}}");
        // sixteen characters are fine, and 30 000 is one order's limit
        save("A", "{\"name\":\"Sechzehn Zeichen\",\"units\":{\"AXEMAN\":30000}}");
        player.browser.get(TEMPLATES).andExpect(jsonPath("$", hasSize(1)));
    }
}
