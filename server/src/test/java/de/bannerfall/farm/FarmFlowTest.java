package de.bannerfall.farm;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.rules.Travel;
import de.bannerfall.village.OwnerChange;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The farm helper (02-game-rules §18): a list per village, one-tap sending of the template as an
 * attack, and the rules of the list. The attacker has 20 axemen at home (10 min/field); the target
 * player has no beginner protection unless a test gives him some.
 */
@IntegrationTest
class FarmFlowTest {

    /**
     * A row near the bottom of the test map, away from the spawn ring: nobody else's village is
     * there.
     */
    private static final int MAP_ROW = TestWorldConfiguration.MAP_SIZE - 12;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired OwnerChange ownerChange;
    @Autowired PlatformTransactionManager transactionManager;

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
        setProtection(victim, clock.instant().minusSeconds(1));
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

    private void setProtection(TestPlayer player, Instant until) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(until), player.playerId)
                .update();
    }

    private static String path(TestPlayer player) {
        return player.villagePath() + "/farm-targets";
    }

    private static String addBody(Coordinates at, String units) {
        return "{\"target\":{\"x\":%d,\"y\":%d},\"units\":%s}".formatted(at.x(), at.y(), units);
    }

    private ResultActions add(Coordinates at, String units) throws Exception {
        return farmer.browser.post(path(farmer), addBody(at, units));
    }

    private long addOk(Coordinates at, String units) throws Exception {
        return number(body(add(at, units).andExpect(status().isCreated())), "$.id").longValue();
    }

    // ---- the list ----

    @Test
    void aTargetIsListedWithItsTemplateDistanceAndTravelTime() throws Exception {
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$", hasSize(0)));

        Coordinates farmerAt = coordinates(farmer);
        double distance = Travel.distance(farmerAt, victimAt);
        long id = addOk(victimAt, "{\"AXEMAN\":10,\"SPEARMAN\":0}");

        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].target.id").value(victim.villageId))
                .andExpect(jsonPath("$[0].target.playerId").value(victim.playerId))
                // only the types that are sent
                .andExpect(jsonPath("$[0].units.AXEMAN").value(10))
                .andExpect(jsonPath("$[0].units.SPEARMAN").doesNotExist())
                .andExpect(
                        jsonPath("$[0].distance")
                                .value(org.hamcrest.Matchers.closeTo(distance, 1e-9)))
                .andExpect(jsonPath("$[0].travelSeconds").isNumber())
                .andExpect(jsonPath("$[0].protectedUntil").value(nullValue()))
                .andExpect(jsonPath("$[0].lastSentAt").value(nullValue()));
    }

    @Test
    void theTemplateCanBeChangedAndATargetRemoved() throws Exception {
        long id = addOk(victimAt, "{\"AXEMAN\":10}");

        farmer.browser
                .put(path(farmer) + "/" + id, "{\"units\":{\"AXEMAN\":5,\"SCOUT\":1}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units.AXEMAN").value(5))
                .andExpect(jsonPath("$.units.SCOUT").value(1));
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$[0].units.AXEMAN").value(5));

        farmer.browser.delete(path(farmer) + "/" + id).andExpect(status().isNoContent());
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$", hasSize(0)));
        farmer.browser
                .delete(path(farmer) + "/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_TARGET_NOT_FOUND"));
    }

    @Test
    void aProtectedTargetShowsHisProtection() throws Exception {
        Instant until = clock.instant().plus(Duration.ofHours(5));
        setProtection(victim, until);
        addOk(victimAt, "{\"AXEMAN\":10}");
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].protectedUntil").value(until.toString()));
    }

    // ---- rules of the list ----

    @Test
    void theTemplateIsChecked() throws Exception {
        add(victimAt, "{}").andExpect(status().isBadRequest());
        add(victimAt, "{\"AXEMAN\":0}").andExpect(status().isBadRequest());
        add(victimAt, "{\"AXEMAN\":-1}").andExpect(status().isBadRequest());
        add(victimAt, "{\"AXEMAN\":30001}").andExpect(status().isBadRequest());
        add(victimAt, "{\"AXEMAN\":10,\"NOBLE\":1}").andExpect(status().isBadRequest());
        // M31: the paladin is one hero, not part of "the usual troops"
        add(victimAt, "{\"AXEMAN\":10,\"PALADIN\":1}").andExpect(status().isBadRequest());
        add(victimAt, "{\"DRAGON\":1}").andExpect(status().isBadRequest());
        farmer.browser
                .post(path(farmer), "{\"units\":{\"AXEMAN\":1}}")
                .andExpect(status().isBadRequest());
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$", hasSize(0)));
        // the same checks when changing a template
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        farmer.browser
                .put(path(farmer) + "/" + id, "{\"units\":{\"NOBLE\":1}}")
                .andExpect(status().isBadRequest());
        farmer.browser
                .put(path(farmer) + "/" + id, "{\"units\":{}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTargetMustExistAndIsNotYourOwnVillage() throws Exception {
        add(new Coordinates(-5, -5), "{\"AXEMAN\":10}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        add(coordinates(farmer), "{\"AXEMAN\":10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_VILLAGE"));
    }

    @Test
    void aTargetIsOnTheListOnlyOnce() throws Exception {
        addOk(victimAt, "{\"AXEMAN\":10}");
        add(victimAt, "{\"AXEMAN\":5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_FARM_LIST"));
        // another village of the farmer has its own list
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- sending ----

    @Test
    void oneTapSendsTheTemplateAsAnAttack() throws Exception {
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        Instant now = clock.instant();

        String sent =
                body(
                        farmer.browser
                                .post(path(farmer) + "/" + id + "/send", "")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.kind").value("ATTACK"))
                                .andExpect(jsonPath("$.units.AXEMAN").value(10))
                                .andExpect(jsonPath("$.targetVillage.id").value(victim.villageId)));
        assertThat(JsonPath.<String>read(sent, "$.departAt")).isEqualTo(now.toString());

        farmer.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(10));
        victim.village().andExpect(jsonPath("$.incomingAttacks").value(1));
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].lastSentAt").value(now.toString()));

        // again with the other half, then the units are gone
        clock.advance(Duration.ofSeconds(5));
        farmer.browser.post(path(farmer) + "/" + id + "/send", "").andExpect(status().isCreated());
        farmer.browser
                .post(path(farmer) + "/" + id + "/send", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_UNITS"));
        // a refused send changes nothing: no partial attack
        farmer.village().andExpect(jsonPath("$.unitsHome.AXEMAN").doesNotExist());
        victim.village().andExpect(jsonPath("$.incomingAttacks").value(2));
    }

    @Test
    void aProtectedTargetCannotBeHit() throws Exception {
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        setProtection(victim, clock.instant().plus(Duration.ofDays(30)));
        farmer.browser
                .post(path(farmer) + "/" + id + "/send", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_TARGET"));
        farmer.village().andExpect(jsonPath("$.unitsHome.AXEMAN").value(20));
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$[0].lastSentAt").value(nullValue()));
    }

    @Test
    void farmingABarbarianVillageKeepsTheProtectionFarmingAPlayerEndsIt() throws Exception {
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofDays(1));
        setProtection(farmer, until);
        Placed barbarian = barbarianVillage();
        try {
            long barbarianEntry = addOk(barbarian.at(), "{\"AXEMAN\":5}");
            long playerEntry = addOk(victimAt, "{\"AXEMAN\":5}");

            // M25: the same code as a normal command, so the barbarian target is free to hit
            farmer.browser
                    .post(path(farmer) + "/" + barbarianEntry + "/send", "")
                    .andExpect(status().isCreated());
            assertThat(protectedUntil(farmer)).isEqualTo(until);

            farmer.browser
                    .post(path(farmer) + "/" + playerEntry + "/send", "")
                    .andExpect(status().isCreated());
            assertThat(protectedUntil(farmer)).isEqualTo(now);
        } finally {
            jdbc.sql("delete from movement where target_village_id = ?")
                    .param(barbarian.id())
                    .update();
            jdbc.sql("delete from village where id = ?").param(barbarian.id()).update();
        }
    }

    // ---- send all (M39) ----

    private String sendAll() throws Exception {
        return body(farmer.browser.post(path(farmer) + "/send-all", "").andExpect(status().isOk()));
    }

    private long homeAxemen() throws Exception {
        Number axemen = JsonPath.read(body(farmer.village()), "$.unitsHome.AXEMAN");
        return axemen == null ? 0 : axemen.longValue();
    }

    @Test
    void sendAllWorksDownTheListAndStopsWhereTheUnitsRunOut() throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        Placed one = barbarianVillage();
        Placed two = barbarianVillage();
        Placed three = barbarianVillage();
        try {
            long first = addOk(one.at(), "{\"AXEMAN\":8}");
            long second = addOk(two.at(), "{\"AXEMAN\":15}");
            // would still fit after the stop: the run doesn't look past it
            addOk(three.at(), "{\"AXEMAN\":1}");
            Instant now = clock.instant();

            String result = sendAll();
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent")).containsExactly((int) first);
            assertThat(JsonPath.<List<Object>>read(result, "$.skipped")).isEmpty();
            assertThat(JsonPath.<String>read(result, "$.stop")).isEqualTo("NOT_ENOUGH_UNITS");
            assertThat(number(result, "$.stoppedAt").longValue()).isEqualTo(second);

            assertThat(homeAxemen()).isEqualTo(12);
            farmer.browser
                    .get(path(farmer))
                    .andExpect(jsonPath("$[0].lastSentAt").value(now.toString()))
                    .andExpect(jsonPath("$[1].lastSentAt").value(nullValue()))
                    .andExpect(jsonPath("$[2].lastSentAt").value(nullValue()));
        } finally {
            removeBarbarians(one, two, three);
        }
    }

    @Test
    void sendAllSendsTheWholeListAndSkipsTargetsThatCantBeHit() throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        Placed one = barbarianVillage();
        Placed mine = barbarianVillage();
        Placed two = barbarianVillage();
        try {
            long first = addOk(one.at(), "{\"AXEMAN\":5}");
            // under protection at arrival, and asks for more than the village has: skipped, the
            // units only decide for an entry that could be sent
            long protectedPlayer = addOk(victimAt, "{\"AXEMAN\":50}");
            long conquered = addOk(mine.at(), "{\"AXEMAN\":5}");
            long last = addOk(two.at(), "{\"AXEMAN\":5}");
            setProtection(victim, clock.instant().plus(Duration.ofDays(30)));
            jdbc.sql("update village set player_id = ? where id = ?")
                    .params(farmer.playerId, mine.id())
                    .update();

            String result = sendAll();
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent"))
                    .containsExactly((int) first, (int) last);
            assertThat(number(result, "$.skipped[0].targetId").longValue())
                    .isEqualTo(protectedPlayer);
            assertThat(JsonPath.<String>read(result, "$.skipped[0].reason"))
                    .isEqualTo("PROTECTED_TARGET");
            assertThat(number(result, "$.skipped[1].targetId").longValue()).isEqualTo(conquered);
            assertThat(JsonPath.<String>read(result, "$.skipped[1].reason"))
                    .isEqualTo("OWN_VILLAGE");
            assertThat(JsonPath.<String>read(result, "$.stop")).isEqualTo("DONE");
            assertThat(JsonPath.<Object>read(result, "$.stoppedAt")).isNull();
            assertThat(homeAxemen()).isEqualTo(10);
            victim.village().andExpect(jsonPath("$.incomingAttacks").value(0));
        } finally {
            removeBarbarians(one, mine, two);
        }
    }

    @Test
    void sendAllLeavesPlayersOutWhileTheSenderIsProtected() throws Exception {
        Instant until = clock.instant().plus(Duration.ofDays(1));
        setProtection(farmer, until);
        Placed barbarian = barbarianVillage();
        try {
            long player = addOk(victimAt, "{\"AXEMAN\":5}");
            long free = addOk(barbarian.at(), "{\"AXEMAN\":5}");

            String result = sendAll();
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent")).containsExactly((int) free);
            assertThat(number(result, "$.skipped[0].targetId").longValue()).isEqualTo(player);
            assertThat(JsonPath.<String>read(result, "$.skipped[0].reason"))
                    .isEqualTo("ENDS_OWN_PROTECTION");
            assertThat(JsonPath.<String>read(result, "$.stop")).isEqualTo("DONE");
            // one tap for the whole list never ends the protection
            assertThat(protectedUntil(farmer)).isEqualTo(until);
            victim.village().andExpect(jsonPath("$.incomingAttacks").value(0));

            // without protection the player's village is sent like the rest
            setProtection(farmer, clock.instant().minusSeconds(1));
            result = sendAll();
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent"))
                    .containsExactly((int) player, (int) free);
            victim.village().andExpect(jsonPath("$.incomingAttacks").value(1));
            assertThat(homeAxemen()).isEqualTo(5);
        } finally {
            removeBarbarians(barbarian);
        }
    }

    @Test
    void sendAllOnAnEmptyListSendsNothing() throws Exception {
        String result = sendAll();
        assertThat(JsonPath.<List<Object>>read(result, "$.sent")).isEmpty();
        assertThat(JsonPath.<String>read(result, "$.stop")).isEqualTo("DONE");
        assertThat(homeAxemen()).isEqualTo(20);
    }

    @Test
    void sendAllUsesOnlyYourOwnList() throws Exception {
        addOk(victimAt, "{\"AXEMAN\":10}");
        victim.browser
                .post(path(farmer) + "/send-all", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        assertThat(homeAxemen()).isEqualTo(20);
    }

    private void removeBarbarians(Placed... placed) {
        for (Placed village : placed) {
            jdbc.sql("delete from movement where target_village_id = ?")
                    .param(village.id())
                    .update();
            jdbc.sql("delete from village where id = ?").param(village.id()).update();
        }
    }

    private Instant protectedUntil(TestPlayer player) {
        return jdbc.sql("select protected_until from player where id = ?")
                .param(player.playerId)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    // ---- whose list it is ----

    @Test
    void listsBelongToTheirVillages() throws Exception {
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        // another player can neither read nor change the farmer's list
        victim.browser.get(path(farmer)).andExpect(status().isNotFound());
        victim.browser
                .post(path(farmer), addBody(victimAt, "{\"AXEMAN\":1}"))
                .andExpect(status().isNotFound());
        // nor use an entry of the farmer's list through his own village
        victim.browser
                .post(path(victim) + "/" + id + "/send", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_TARGET_NOT_FOUND"));
        victim.browser.delete(path(victim) + "/" + id).andExpect(status().isNotFound());
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void aListOfSeveralTargetsKeepsTheOrderAndTheirOwners() throws Exception {
        Placed barbarian = barbarianVillage();
        try {
            long first = addOk(victimAt, "{\"AXEMAN\":3}");
            long second = addOk(barbarian.at(), "{\"AXEMAN\":4}");
            farmer.browser
                    .get(path(farmer))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(first))
                    .andExpect(jsonPath("$[0].target.playerId").value(victim.playerId))
                    .andExpect(jsonPath("$[0].units.AXEMAN").value(3))
                    .andExpect(jsonPath("$[1].id").value(second))
                    .andExpect(jsonPath("$[1].target.id").value(barbarian.id()))
                    .andExpect(jsonPath("$[1].target.playerId").value(nullValue()))
                    .andExpect(jsonPath("$[1].units.AXEMAN").value(4));
        } finally {
            jdbc.sql("delete from village where id = ?").param(barbarian.id()).update();
        }
    }

    @Test
    void aVillageOfYourOwnIsNoTarget() throws Exception {
        Placed barbarian = barbarianVillage();
        try {
            jdbc.sql("update village set player_id = ? where id = ?")
                    .params(farmer.playerId, barbarian.id())
                    .update();
            add(barbarian.at(), "{\"AXEMAN\":10}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OWN_VILLAGE"));
        } finally {
            jdbc.sql("delete from village where id = ?").param(barbarian.id()).update();
        }
    }

    private record Placed(long id, Coordinates at) {}

    /** A barbarian village on a free field of a row near the bottom of the test map. */
    private Placed barbarianVillage() {
        for (int x = 120; x < TestWorldConfiguration.MAP_SIZE; x++) {
            Long id =
                    jdbc.sql(
                                    """
                                    insert into village (player_id, name, x, y, wood, clay, iron,
                                                         resources_updated_at, loyalty_updated_at,
                                                         points, created_at)
                                    values (null, null, :x, :y, 0, 0, 0, now(), now(), 0, now())
                                    on conflict (x, y) do nothing
                                    returning id
                                    """)
                            .param("x", x)
                            .param("y", MAP_ROW)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                return new Placed(id, new Coordinates(x, MAP_ROW));
            }
        }
        throw new IllegalStateException("No free field in the row");
    }

    @Test
    void theListGoesWhenTheVillageChangesHands() throws Exception {
        addOk(victimAt, "{\"AXEMAN\":10}");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> ownerChange.dropOrders(farmer.villageId, clock.instant()));
        Long left =
                jdbc.sql("select count(*) from farm_target where village_id = ?")
                        .params(farmer.villageId)
                        .query(Long.class)
                        .single();
        assertThat(left).isZero();
    }

    @Test
    void theEndpointsNeedLogin() throws Exception {
        Browser stranger = Browser.open(mockMvc);
        stranger.get(path(farmer)).andExpect(status().isUnauthorized());
        stranger.post(path(farmer) + "/1/send", "").andExpect(status().isUnauthorized());
        stranger.post(path(farmer) + "/send-all", "").andExpect(status().isUnauthorized());
    }
}
