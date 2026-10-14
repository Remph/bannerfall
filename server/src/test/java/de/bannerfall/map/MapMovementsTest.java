package de.bannerfall.map;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/map/movements (M26, M36): the markers of the map show the player's own commands, his
 * units walking home and what is on the way to his own villages, and nothing about anybody else's
 * movements. The attacker has axemen, spearmen and a noble at home; the defender has no beginner
 * protection. Arrivals: the clock is moved by hand and the event worker is called directly.
 */
@IntegrationTest
class MapMovementsTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;
    @Autowired EventProcessor processor;

    private TestPlayer attacker;
    private TestPlayer defender;
    private Coordinates defenderAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        String village = body(defender.village());
        defenderAt =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        setHomeUnits(attacker, "u_axeman", 50);
        setHomeUnits(attacker, "u_spearman", 50);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
    }

    @Test
    void theSenderSeesHisCommandsWithNoblesAndTheDefenderSeesThemWithout() throws Exception {
        String first = send("ATTACK", "{\"AXEMAN\":10}");
        sendNobleAttack();
        send("SUPPORT", "{\"SPEARMAN\":5}");
        Instant firstArrival = Instant.parse(JsonPath.read(first, "$.arriveAt"));

        movements(attacker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(defender.villageId))
                .andExpect(jsonPath("$[0].x").value(defenderAt.x()))
                .andExpect(jsonPath("$[0].y").value(defenderAt.y()))
                .andExpect(jsonPath("$[0].attacks").value(2))
                .andExpect(jsonPath("$[0].nobleAttacks").value(1))
                .andExpect(jsonPath("$[0].supports").value(1));
        // the axemen walk faster than the noble and the spearmen
        assertThat(Instant.parse(JsonPath.read(body(movements(attacker)), "$[0].nextArriveAt")))
                .isEqualTo(firstArrival);

        // the defender learns that two attacks come, not that one of them carries a noble
        movements(defender)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(defender.villageId))
                .andExpect(jsonPath("$[0].attacks").value(2))
                .andExpect(jsonPath("$[0].nobleAttacks").value(0))
                .andExpect(jsonPath("$[0].supports").value(1));
    }

    @Test
    void otherPlayersSeeNothingOfIt() throws Exception {
        send("ATTACK", "{\"AXEMAN\":10}");
        send("SUPPORT", "{\"SPEARMAN\":5}");

        TestPlayer bystander = TestPlayer.register(mockMvc);

        movements(bystander).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aCancelledCommandWalkingHomeIsAReturnOnItsHomeVillage() throws Exception {
        long attack = number(send("ATTACK", "{\"AXEMAN\":10}"), "$.id").longValue();
        clock.advance(Duration.ofSeconds(60));

        attacker.cancelMovement(attack).andExpect(status().isOk());

        // it is a RETURN to the attacker's village now: no attack on the defender any more
        Instant home =
                Instant.parse(JsonPath.read(body(attacker.troops()), "$.incoming[0].arriveAt"));
        movements(attacker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(attacker.villageId))
                .andExpect(jsonPath("$[0].attacks").value(0))
                .andExpect(jsonPath("$[0].supports").value(0))
                .andExpect(jsonPath("$[0].returns").value(1))
                .andExpect(jsonPath("$[0].nextArriveAt").value(nullValue()));
        assertThat(nextReturnAt(attacker)).isEqualTo(home);
        movements(defender).andExpect(jsonPath("$").isEmpty());

        // home: the marker goes
        clock.set(home);
        processor.processDue(clock.instant());
        movements(attacker).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void survivorsOfABattleWalkHomeAndOnlyTheirOwnerSeesThem() throws Exception {
        Instant arrival =
                Instant.parse(JsonPath.read(send("ATTACK", "{\"AXEMAN\":10}"), "$.arriveAt"));

        clock.set(arrival);
        processor.processDue(clock.instant());

        movements(attacker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(attacker.villageId))
                .andExpect(jsonPath("$[0].attacks").value(0))
                .andExpect(jsonPath("$[0].returns").value(1));
        // the defender's map doesn't follow the attacker home
        movements(defender).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aSupportSentBackShowsOnlyOnItsHomeVillage() throws Exception {
        Instant arrival =
                Instant.parse(JsonPath.read(send("SUPPORT", "{\"SPEARMAN\":5}"), "$.arriveAt"));
        clock.set(arrival);
        processor.processDue(clock.instant());
        long stackId = number(body(defender.troops()), "$.supportsHere[0].stackId").longValue();
        movements(attacker).andExpect(jsonPath("$").isEmpty());

        defender.returnStack(stackId, "{\"SPEARMAN\":5}").andExpect(status().isCreated());

        movements(attacker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(attacker.villageId))
                .andExpect(jsonPath("$[0].supports").value(0))
                .andExpect(jsonPath("$[0].returns").value(1));
        movements(defender).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void anIncomingAttackAndAReturnShareTheVillageButKeepTheirOwnTimes() throws Exception {
        long attack = number(send("ATTACK", "{\"AXEMAN\":10}"), "$.id").longValue();
        clock.advance(Duration.ofSeconds(60));
        attacker.cancelMovement(attack).andExpect(status().isOk());
        Instant home =
                Instant.parse(JsonPath.read(body(attacker.troops()), "$.incoming[0].arriveAt"));
        setHomeUnits(defender, "u_axeman", 10);
        String village = body(attacker.village());
        Coordinates attackerAt =
                new Coordinates(
                        number(village, "$.x").intValue(), number(village, "$.y").intValue());
        String counter =
                body(
                        defender.command("ATTACK", attackerAt, "{\"AXEMAN\":10}")
                                .andExpect(status().isCreated()));

        movements(attacker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(attacker.villageId))
                .andExpect(jsonPath("$[0].attacks").value(1))
                .andExpect(jsonPath("$[0].returns").value(1));
        assertThat(Instant.parse(JsonPath.read(body(movements(attacker)), "$[0].nextArriveAt")))
                .isEqualTo(Instant.parse(JsonPath.read(counter, "$.arriveAt")));
        assertThat(nextReturnAt(attacker)).isEqualTo(home);
        // the defender sees his own attack, not the attacker's return
        movements(defender)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(attacker.villageId))
                .andExpect(jsonPath("$[0].attacks").value(1))
                .andExpect(jsonPath("$[0].returns").value(0))
                .andExpect(jsonPath("$[0].nextReturnAt").value(nullValue()));
    }

    @Test
    void ownAttacksOnAVillageHeOwnsByNowAreLeftOut() throws Exception {
        send("ATTACK", "{\"AXEMAN\":10}");
        send("SUPPORT", "{\"SPEARMAN\":5}");

        // as after a conquest by an earlier wave: the later attack will arrive like a support
        setOwner(defender.villageId, attacker.playerId);

        List<Integer> attacks = JsonPath.read(body(movements(attacker)), "$[*].attacks");
        List<Integer> supports = JsonPath.read(body(movements(attacker)), "$[*].supports");
        assertThat(attacks).containsExactly(0);
        assertThat(supports).containsExactly(1);
        movements(defender).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void attacksOnBarbarianVillagesAreShown() throws Exception {
        setOwner(defender.villageId, null);

        send("ATTACK", "{\"AXEMAN\":10}");

        movements(attacker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(defender.villageId))
                .andExpect(jsonPath("$[0].attacks").value(1));
    }

    private Instant nextReturnAt(TestPlayer player) throws Exception {
        return Instant.parse(JsonPath.read(body(movements(player)), "$[0].nextReturnAt"));
    }

    private ResultActions movements(TestPlayer player) throws Exception {
        return player.browser.get("/api/map/movements");
    }

    private String send(String kind, String unitsJson) throws Exception {
        return body(attacker.command(kind, defenderAt, unitsJson).andExpect(status().isCreated()));
    }

    /**
     * Nobles may only go {@code nobleMaxDistance} fields; registered players spawn further apart.
     */
    private void sendNobleAttack() throws Exception {
        setHomeUnits(attacker, "u_noble", 1);
        int nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        jdbc.sql("update world_settings set noble_max_distance = 1000").update();
        worldService.reload();
        try {
            send("ATTACK", "{\"AXEMAN\":10,\"NOBLE\":1}");
        } finally {
            jdbc.sql("update world_settings set noble_max_distance = ?")
                    .param(nobleMaxDistanceBefore)
                    .update();
            worldService.reload();
        }
    }

    private void setHomeUnits(TestPlayer player, String column, int count) {
        jdbc.sql(
                        "update unit_stack set "
                                + column
                                + " = ? where home_village_id = ? and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    private void setOwner(long villageId, Long playerId) {
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(playerId, villageId)
                .update();
    }
}
