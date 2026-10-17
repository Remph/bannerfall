package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin side of NPC lords (M12d, 04-api *Admin*): who may call it, the lord list, spawning, the
 * difficulty and the CUSTOM values. The database is shared, so every test removes its lords.
 */
@IntegrationTest
class NpcAdminTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired PlayerRepository players;
    @Autowired NpcBrainRepository brains;
    @Autowired MutableClock clock;

    private Browser admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        admin = Browser.open(mockMvc);
        String name = Browser.uniqueUsername();
        admin.register(name);
        jdbc.sql("update account set role = 'ADMIN' where username = ?").param(name).update();
        admin.login(name, Browser.PASSWORD).andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        String lordVillages =
                "select id from village where player_id in (select id from player where npc)";
        jdbc.sql(
                        "delete from unit_stack where home_village_id in ("
                                + lordVillages
                                + ") or location_village_id in ("
                                + lordVillages
                                + ")")
                .update();
        jdbc.sql("delete from village where player_id in (select id from player where npc)")
                .update();
        jdbc.sql("delete from player where npc").update();
        jdbc.sql("update world_settings set npc_difficulty = 'NORMAL', npc_custom = null").update();
        worldService.reload();
    }

    @Test
    void aPlayerGetsForbiddenOnEveryNpcAdminEndpoint() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);

        player.browser.get("/api/admin/npc-lords").andExpect(status().isForbidden());
        player.browser
                .post("/api/admin/npc-lords", "{\"personality\":\"BALANCED\"}")
                .andExpect(status().isForbidden());
        player.browser.get("/api/admin/npc-settings").andExpect(status().isForbidden());
        player.browser.get("/api/admin/npc-balance/custom").andExpect(status().isForbidden());
        player.browser
                .put("/api/admin/npc-balance/custom", customJson("0.9"))
                .andExpect(status().isForbidden());
    }

    @Test
    void spawningALordAnswers201WithItsRowAndItShowsUpInTheListNewestFirst() throws Exception {
        admin.post("/api/admin/npc-lords", "{\"personality\":\"DEFENSIVE\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personality").value("DEFENSIVE"))
                .andExpect(jsonPath("$.template").value("DEFENSIVE"))
                .andExpect(jsonPath("$.villageCount").value(1))
                .andExpect(jsonPath("$.lastThinkAt").doesNotExist())
                .andExpect(jsonPath("$.nextThinkAt").isNotEmpty())
                .andExpect(jsonPath("$.braked").value(false))
                .andExpect(jsonPath("$.boosted").value(false))
                .andExpect(jsonPath("$.productionFactor").value(0.9));
        admin.post("/api/admin/npc-lords", "{\"personality\":\"OFFENSIVE\"}")
                .andExpect(status().isCreated());

        admin.get("/api/admin/npc-lords")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].personality").value("OFFENSIVE"))
                .andExpect(jsonPath("$[1].personality").value("DEFENSIVE"));
        List<Player> lords = players.findNpcLords();
        assertThat(lords).hasSize(2);
        assertThat(brains.findById(lords.getFirst().getId())).isPresent();
        // a round was scheduled for each, like at world start
        assertThat(
                        jdbc.sql(
                                        "select count(*) from game_event where kind = 'NPC_THINK'"
                                                + " and status = 'PENDING'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void anUnknownPersonalityIsRefused() throws Exception {
        admin.post("/api/admin/npc-lords", "{\"personality\":\"SNEAKY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        admin.post("/api/admin/npc-lords", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void aFullMapAnswers409WorldFullAndLeavesNoLordBehind() throws Exception {
        jdbc.sql("update world_settings set map_size = 1").update();
        worldService.reload();
        try {
            // a 1 x 1 map has room for no village once any exists; use the shared map's villages
            admin.post("/api/admin/npc-lords", "{\"personality\":\"BALANCED\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WORLD_FULL"));
            assertThat(players.findNpcLords()).isEmpty();
        } finally {
            jdbc.sql("update world_settings set map_size = ?")
                    .param(TestWorldConfiguration.MAP_SIZE)
                    .update();
            worldService.reload();
        }
    }

    @Test
    void theListShowsTheRoundsAndTheRubberBand() throws Exception {
        admin.post("/api/admin/npc-lords", "{\"personality\":\"BALANCED\"}")
                .andExpect(status().isCreated());
        long lordId = players.findNpcLords().getFirst().getId();
        jdbc.sql("update player set production_factor = 1.125 where id = ?").param(lordId).update();
        NpcBrain brain = brains.findById(lordId).orElseThrow();
        brain.setMemory(brain.getMemory().withStance(new NpcMemory.Stance(true, false)));
        brain.thoughtAt(clock.instant());
        brains.save(brain);

        admin.get("/api/admin/npc-lords")
                .andExpect(jsonPath("$[0].boosted").value(true))
                .andExpect(jsonPath("$[0].braked").value(false))
                .andExpect(jsonPath("$[0].productionFactor").value(1.125))
                .andExpect(jsonPath("$[0].lastThinkAt").isNotEmpty());

        // a lord without a pending round (out of the game) has no nextThinkAt
        jdbc.sql("delete from game_event where kind = 'NPC_THINK'").update();
        admin.get("/api/admin/npc-lords").andExpect(jsonPath("$[0].nextThinkAt").doesNotExist());
    }

    @Test
    void theDifficultyIsChangedAndPickingCustomForTheFirstTimeStoresACopyOfNormal()
            throws Exception {
        admin.get("/api/admin/npc-settings").andExpect(jsonPath("$.difficulty").value("NORMAL"));

        admin.put("/api/admin/npc-settings", "{\"difficulty\":\"HARD\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("HARD"));
        assertThat(worldService.settings().npcDifficulty()).isEqualTo(NpcDifficulty.HARD);
        assertThat(worldService.settings().npcCustom()).isNull();

        admin.put("/api/admin/npc-settings", "{\"difficulty\":\"CUSTOM\"}")
                .andExpect(status().isOk());
        assertThat(worldService.settings().npcDifficulty()).isEqualTo(NpcDifficulty.CUSTOM);
        assertThat(worldService.settings().npcCustom().productionFactor()).isEqualTo(0.9);
        assertThat(worldService.settings().npcCustom().attacksPerHumanPerDay()).isEqualTo(8);

        admin.put("/api/admin/npc-settings", "{\"difficulty\":\"EXTREME\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void theCustomValuesAreANormalCopyUntilStoredThenTheyRoundTripAndTakeEffect() throws Exception {
        admin.get("/api/admin/npc-balance/custom")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionFactor").value(0.9))
                .andExpect(jsonPath("$.boostBelow").value(0.5))
                .andExpect(jsonPath("$.boostFactor").value(1.25))
                .andExpect(jsonPath("$.leadMargin").value(1.25))
                .andExpect(jsonPath("$.attacksPerPlayerPerDay").value(4))
                .andExpect(jsonPath("$.attacksPerHumanPerDay").value(8))
                .andExpect(jsonPath("$.quietHours").value(true))
                .andExpect(jsonPath("$.spareLastVillage").value(true))
                .andExpect(jsonPath("$.buildQuietFrom").value("23:00"))
                .andExpect(jsonPath("$.buildQuietTo").value("07:00"))
                .andExpect(jsonPath("$.farmLootShare").value(0.5))
                .andExpect(jsonPath("$.farmRest").value(true));
        admin.put("/api/admin/npc-settings", "{\"difficulty\":\"CUSTOM\"}");

        admin.put("/api/admin/npc-balance/custom", customJson("1.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionFactor").value(1.3));

        admin.get("/api/admin/npc-balance/custom")
                .andExpect(jsonPath("$.productionFactor").value(1.3))
                .andExpect(jsonPath("$.attacksPerHumanPerDay").value(5))
                .andExpect(jsonPath("$.farmLootShare").value(0.25))
                .andExpect(jsonPath("$.farmRest").value(false));
        assertThat(worldService.settings().npcCustom().productionFactor()).isEqualTo(1.3);
        // the lords' next round uses it
        assertThat(balanceFactor()).isEqualTo(1.3);
    }

    @Test
    void invalidCustomValuesAreRefusedAndNothingIsStored() throws Exception {
        admin.put("/api/admin/npc-balance/custom", customJson("0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        admin.put(
                        "/api/admin/npc-balance/custom",
                        customJson("1.0").replace("\"23:00\"", "\"25:99\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        admin.put(
                        "/api/admin/npc-balance/custom",
                        customJson("1.0")
                                .replace(
                                        "\"attacksPerPlayerPerDay\":2",
                                        "\"attacksPerPlayerPerDay\":-1"))
                .andExpect(status().isBadRequest());
        admin.put(
                        "/api/admin/npc-balance/custom",
                        customJson("1.0")
                                .replace("\"farmLootShare\":0.25", "\"farmLootShare\":-0.25"))
                .andExpect(status().isBadRequest());
        admin.put("/api/admin/npc-balance/custom", "null").andExpect(status().isBadRequest());

        assertThat(worldService.storedNpcCustom()).isNull();
    }

    private double balanceFactor() {
        return worldService.settings().npcCustom().productionFactor();
    }

    private static String customJson(String productionFactor) {
        return """
        {"productionFactor":%s,"boostBelow":0.5,"boostFactor":1.25,"leadMargin":1.25,\
        "attacksPerPlayerPerDay":2,"attacksPerHumanPerDay":5,"quietHours":true,\
        "spareLastVillage":false,"buildQuietFrom":"23:00","buildQuietTo":"07:00",\
        "farmLootShare":0.25,"farmRest":false}\
        """
                .formatted(productionFactor);
    }
}
