package de.bannerfall.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.abuse.InvariantCheck;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M14e: the invariant check stays quiet for the states the game itself produces (builds, recruits,
 * attacks, support, trade, an offer) and notices a missing event. The database is shared with other
 * tests, so this compares the counts before and after.
 */
@IntegrationTest
class InvariantCheckTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired MutableClock clock;
    @Autowired InvariantCheck invariants;

    private TestPlayer alice;
    private TestPlayer bob;
    private Coordinates bobAt;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestPlayer.register(mockMvc);
        bob = TestPlayer.register(mockMvc);
        bobAt = Fixture.coordinates(bob);
        Fixture.richVillage(jdbc, alice.villageId);
        Fixture.richVillage(jdbc, bob.villageId);
        Fixture.endProtection(jdbc, bob.playerId, clock.instant().minusSeconds(1));
    }

    @Test
    void whatTheGameDoesIsNotAProblemAndAMissingEventIs() throws Exception {
        Map<String, Integer> before = counts();

        alice.enqueue(BuildingType.WAREHOUSE).andExpect(status().isCreated());
        alice.enqueue(BuildingType.FARM).andExpect(status().isCreated());
        alice.enqueue(BuildingType.WAREHOUSE).andExpect(status().isCreated());
        alice.recruit(UnitType.SPEARMAN, 5).andExpect(status().isCreated());
        Fixture.setHomeUnits(jdbc, alice.villageId, "u_spearman", 20);
        alice.command("SUPPORT", bobAt, "{\"SPEARMAN\":5}").andExpect(status().isCreated());
        alice.command("ATTACK", bobAt, "{\"SPEARMAN\":10}").andExpect(status().isCreated());
        alice.browser
                .post(
                        alice.villagePath() + "/transports",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":100,\"clay\":0,\"iron\":0}"
                                .formatted(bobAt.x(), bobAt.y()))
                .andExpect(status().isCreated());
        alice.browser
                .post(
                        alice.villagePath() + "/offers",
                        """
                        {"offerResource":"WOOD","offerAmount":1000,"wantResource":"CLAY",
                         "wantAmount":1000,"count":1}\
                        """)
                .andExpect(status().isCreated());
        alice.browser
                .post(
                        alice.villagePath() + "/farm-targets",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"SPEARMAN\":5}}"
                                .formatted(bobAt.x(), bobAt.y()))
                .andExpect(status().isCreated());
        assertThat(counts()).isEqualTo(before);

        // a farm entry the API never makes: a noble in the template
        jdbc.sql("update farm_target set u_noble = 1 where village_id = ?")
                .param(alice.villageId)
                .update();
        assertThat(counts("BAD_FARM_TARGET")).isEqualTo(before.get("BAD_FARM_TARGET") + 1);
        jdbc.sql("update farm_target set u_noble = 0 where village_id = ?")
                .param(alice.villageId)
                .update();

        // 1.4: an entry that uses a template but sends other units
        alice.browser
                .put("/api/farm-templates/A", "{\"units\":{\"SPEARMAN\":5}}")
                .andExpect(status().isOk());
        jdbc.sql(
                        "update farm_target set template_id = (select id from farm_template"
                                + " where player_id = ?) where village_id = ?")
                .params(alice.playerId, alice.villageId)
                .update();
        assertThat(counts()).isEqualTo(before);
        jdbc.sql("update farm_target set u_spearman = 6 where village_id = ?")
                .param(alice.villageId)
                .update();
        assertThat(counts("BAD_FARM_TARGET")).isEqualTo(before.get("BAD_FARM_TARGET") + 1);
        jdbc.sql("update farm_target set u_spearman = 5 where village_id = ?")
                .param(alice.villageId)
                .update();

        deleteEventOf("movement", "MOVEMENT_ARRIVE", alice.villageId, "home_village_id");
        assertThat(counts("MOVEMENT_WITHOUT_EVENT"))
                .isEqualTo(before.get("MOVEMENT_WITHOUT_EVENT") + 2);

        jdbc.sql(
                        """
                        delete from game_event where kind = 'BUILD_COMPLETE' and ref_id in
                            (select id from build_order where village_id = ?)
                        """)
                .param(alice.villageId)
                .update();
        assertThat(counts("BUILD_ORDER_WITHOUT_EVENT"))
                .isEqualTo(before.get("BUILD_ORDER_WITHOUT_EVENT") + 3);

        jdbc.sql(
                        """
                        delete from game_event where kind = 'TRADE_ARRIVE' and ref_id in
                            (select id from trade_transport where source_village_id = ?)
                        """)
                .param(alice.villageId)
                .update();
        assertThat(counts("TRANSPORT_WITHOUT_EVENT"))
                .isEqualTo(before.get("TRANSPORT_WITHOUT_EVENT") + 1);
    }

    /** Deletes the pending arrival of the village's movements. */
    private void deleteEventOf(String table, String kind, long villageId, String column) {
        jdbc.sql(
                        "delete from game_event where kind = ? and ref_id in (select id from "
                                + table
                                + " where "
                                + column
                                + " = ?)")
                .params(kind, villageId)
                .update();
    }

    private int counts(String kind) {
        return counts().getOrDefault(kind, 0);
    }

    /** Problem kind to count, of a fresh run; kinds without a problem are 0. */
    private Map<String, Integer> counts() {
        Map<String, Integer> counts = new HashMap<>();
        for (String kind :
                java.util.List.of(
                        "NEGATIVE_RESOURCES",
                        "LOYALTY_OUT_OF_RANGE",
                        "LEVEL_OUT_OF_RANGE",
                        "RESOURCES_ABOVE_CAPACITY",
                        "NEGATIVE_UNITS",
                        "BAD_FARM_TARGET",
                        "MOVEMENT_WITHOUT_EVENT",
                        "BUILD_ORDER_WITHOUT_EVENT",
                        "TRANSPORT_WITHOUT_EVENT",
                        "STACK_WITHOUT_VILLAGE")) {
            counts.put(kind, 0);
        }
        invariants.run().problems().forEach(p -> counts.put(p.kind(), (int) (int) p.count()));
        return counts;
    }
}
