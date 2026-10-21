package de.bannerfall.farm;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import de.bannerfall.event.EventProcessor;
import de.bannerfall.rules.Travel;
import de.bannerfall.village.OwnerChange;
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
    @Autowired WorldService worldService;
    @Autowired EventProcessor processor;

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

    @Test
    void aMuchSmallerTargetShowsTheMorale() throws Exception {
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        farmer.browser.get(path(farmer)).andExpect(jsonPath("$[0].morale").value(nullValue()));

        // 0.3 + 3 × 0.1 = 60 % (1.6, 02-game-rules §8.2)
        setPoints(farmer, 10_000);
        setPoints(victim, 1_000);
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].morale").value(0.6));
    }

    private void setPoints(TestPlayer player, int points) {
        jdbc.sql("update player set points = ? where id = ?")
                .params(points, player.playerId)
                .update();
    }

    // ---- rules of the list ----

    // ---- the last raid (1.6) ----

    @Test
    void aLandedAttackShowsItsHaulNextToWhatTheUnitsCanCarry() throws Exception {
        // nobody defends: the attackers win and take what they can carry
        jdbc.sql(
                        "update unit_stack set u_spearman = 0, u_swordsman = 0, u_axeman = 0,"
                                + " u_scout = 0, u_light_cavalry = 0, u_heavy_cavalry = 0"
                                + " where home_village_id = ?")
                .params(victim.villageId)
                .update();
        setStock(victim.villageId, 5000);
        long id = addOk(victimAt, "{\"AXEMAN\":10}");
        // 10 axemen carry 15 each
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].haulCapacity").value(150))
                .andExpect(jsonPath("$[0].lastRaid").value(nullValue()));

        Instant first = sendAndLand(id);
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].lastRaid.at").value(first.toString()))
                .andExpect(jsonPath("$[0].lastRaid.won").value(true))
                .andExpect(jsonPath("$[0].lastRaid.loot").value(150))
                // they carried all they could: the village had more
                .andExpect(jsonPath("$[0].lastRaid.full").value(true));

        // an empty village (all below the hiding place): the next raid brings nothing
        Instant second = sendAndLand(id, 0);
        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].lastRaid.at").value(second.toString()))
                .andExpect(jsonPath("$[0].lastRaid.won").value(true))
                .andExpect(jsonPath("$[0].lastRaid.loot").value(0))
                .andExpect(jsonPath("$[0].lastRaid.full").value(false));
    }

    @Test
    void aLostRaidShowsAsLost() throws Exception {
        jdbc.sql("update unit_stack set u_spearman = 1000 where home_village_id = ?")
                .params(victim.villageId)
                .update();
        long id = addOk(victimAt, "{\"AXEMAN\":10}");

        sendAndLand(id);

        farmer.browser
                .get(path(farmer))
                .andExpect(jsonPath("$[0].lastRaid.won").value(false))
                .andExpect(jsonPath("$[0].lastRaid.loot").value(0))
                .andExpect(jsonPath("$[0].lastRaid.full").value(false));
    }

    /** Sends the entry and lets the attack land; when it landed. */
    private Instant sendAndLand(long id) throws Exception {
        return sendAndLand(id, null);
    }

    /** The same with the target's stock set to {@code stock} of each just before it lands. */
    private Instant sendAndLand(long id, Integer stock) throws Exception {
        String sent =
                body(
                        farmer.browser
                                .post(path(farmer) + "/" + id + "/send", "")
                                .andExpect(status().isCreated()));
        Instant at = Instant.parse(JsonPath.read(sent, "$.arriveAt"));
        clock.set(at);
        if (stock != null) {
            setStock(victim.villageId, stock);
        }
        processor.processDue(at);
        return at;
    }

    private void setStock(long villageId, int each) {
        jdbc.sql(
                        "update village set wood = :each, clay = :each, iron = :each,"
                                + " resources_updated_at = :now where id = :id")
                .param("each", each)
                .param("now", Timestamp.from(clock.instant()))
                .param("id", villageId)
                .update();
    }

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
    void sendAllSkipsWhatTheUnitsDontCoverAndGoesOnDownTheList() throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        jdbc.sql("update unit_stack set u_light_cavalry = 10 where home_village_id = ?")
                .params(farmer.villageId)
                .update();
        Placed one = barbarianVillage();
        Placed two = barbarianVillage();
        Placed three = barbarianVillage();
        Placed four = barbarianVillage();
        try {
            // 20 axemen at home: the first two fit, the third doesn't any more
            long first = addOk(one.at(), "{\"AXEMAN\":8}");
            long second = addOk(two.at(), "{\"AXEMAN\":8}");
            long third = addOk(three.at(), "{\"AXEMAN\":8}");
            // other units below it still go out (1.3; the run used to stop at the third)
            long cavalry = addOk(four.at(), "{\"LIGHT_CAVALRY\":5}");
            Instant now = clock.instant();

            String result = sendAll();
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent"))
                    .containsExactly((int) first, (int) second, (int) cavalry);
            assertThat(number(result, "$.skipped[0].targetId").longValue()).isEqualTo(third);
            assertThat(JsonPath.<String>read(result, "$.skipped[0].reason"))
                    .isEqualTo("NOT_ENOUGH_UNITS");
            assertThat(JsonPath.<List<Object>>read(result, "$.skipped")).hasSize(1);

            assertThat(homeAxemen()).isEqualTo(4);
            farmer.village().andExpect(jsonPath("$.unitsHome.LIGHT_CAVALRY").value(5));
            farmer.browser
                    .get(path(farmer))
                    .andExpect(jsonPath("$[0].lastSentAt").value(now.toString()))
                    .andExpect(jsonPath("$[1].lastSentAt").value(now.toString()))
                    .andExpect(jsonPath("$[2].lastSentAt").value(nullValue()))
                    .andExpect(jsonPath("$[3].lastSentAt").value(now.toString()));
        } finally {
            removeBarbarians(one, two, three, four);
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
    void sendBannerChancesLeavesOutTheTargetsWhoseRollIsUsed() throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        jdbc.sql("update unit_stack set u_axeman = 100 where home_village_id = ?")
                .params(farmer.villageId)
                .update();
        Placed rolled = barbarianVillage();
        Placed small = barbarianVillage();
        Placed scouted = barbarianVillage();
        Placed open = barbarianVillage();
        try {
            addOk(victimAt, "{\"AXEMAN\":5}");
            addOk(rolled.at(), "{\"AXEMAN\":30}");
            // free rolls, but these templates can't win one (§21.7): against barbarians the
            // attacker's army must have 30 population, and scouts alone fight no battle
            addOk(small.at(), "{\"AXEMAN\":29}");
            addOk(scouted.at(), "{\"SCOUT\":20}");
            long openEntry = addOk(open.at(), "{\"AXEMAN\":30}");
            // the roll against the victim (all of his villages) and one barbarian village is used
            roll("P" + victim.playerId, clock.instant().minus(Duration.ofHours(23)));
            roll("V" + rolled.id(), clock.instant().minus(Duration.ofHours(1)));
            // a roll older than a day is back
            roll("V" + open.id(), clock.instant().minus(Duration.ofHours(25)));
            farmer.browser
                    .get(path(farmer))
                    .andExpect(
                            jsonPath("$[*].bannerChance")
                                    .value(contains(false, false, false, false, true)));

            String result =
                    body(
                            farmer.browser
                                    .post(path(farmer) + "/send-all?bannerChanceOnly=true", "")
                                    .andExpect(status().isOk()));
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent"))
                    .containsExactly((int) openEntry);
            assertThat(JsonPath.<List<Object>>read(result, "$.skipped")).isEmpty();
            assertThat(homeAxemen()).isEqualTo(70);
        } finally {
            jdbc.sql("delete from banner_roll where winner_id = ?").param(farmer.playerId).update();
            removeBarbarians(rolled, small, scouted, open);
        }
    }

    @Test
    void sendBannerChancesLeavesOutPairsThatHaveAnAttackOnTheWay() throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        jdbc.sql("update unit_stack set u_axeman = 200, u_scout = 10 where home_village_id = ?")
                .params(farmer.villageId)
                .update();
        Placed first = barbarianVillage();
        Placed secondOfVictim = barbarianVillage();
        Placed attacked = barbarianVillage();
        Placed scouted = barbarianVillage();
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(victim.playerId, secondOfVictim.id())
                .update();
        try {
            long firstEntry = addOk(first.at(), "{\"AXEMAN\":30}");
            long victimEntry = addOk(victimAt, "{\"AXEMAN\":30}");
            // the victim's second village: one pair with his first (§21.7)
            addOk(secondOfVictim.at(), "{\"AXEMAN\":30}");
            long attackedEntry = addOk(attacked.at(), "{\"AXEMAN\":30}");
            long scoutedEntry = addOk(scouted.at(), "{\"AXEMAN\":30}");
            // on the way: an attack that will use the roll, and scouts alone, which won't
            farmer.browser
                    .post(path(farmer) + "/" + attackedEntry + "/send", "")
                    .andExpect(status().isCreated());
            farmer.command("ATTACK", scouted.at(), "{\"SCOUT\":5}").andExpect(status().isCreated());
            farmer.browser
                    .get(path(farmer))
                    .andExpect(
                            jsonPath("$[*].bannerChance")
                                    .value(contains(true, true, false, false, true)));

            String result =
                    body(
                            farmer.browser
                                    .post(path(farmer) + "/send-all?bannerChanceOnly=true", "")
                                    .andExpect(status().isOk()));
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent"))
                    .containsExactly((int) firstEntry, (int) victimEntry, (int) scoutedEntry);
            // every pair has its attack on the way now
            farmer.browser
                    .get(path(farmer))
                    .andExpect(
                            jsonPath("$[*].bannerChance")
                                    .value(contains(false, false, false, false, false)));
        } finally {
            jdbc.sql("delete from movement where target_village_id = ?")
                    .param(victim.villageId)
                    .update();
            removeBarbarians(first, secondOfVictim, attacked, scouted);
        }
    }

    @Test
    void sendBannerChancesGivesThePairsChanceToTheNextEntryWhenTheUnitsDontCoverTheFirst()
            throws Exception {
        setProtection(farmer, clock.instant().minusSeconds(1));
        Placed secondOfVictim = barbarianVillage();
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(victim.playerId, secondOfVictim.id())
                .update();
        try {
            long tooBig = addOk(victimAt, "{\"AXEMAN\":50}");
            long next = addOk(secondOfVictim.at(), "{\"AXEMAN\":10}");

            String result =
                    body(
                            farmer.browser
                                    .post(path(farmer) + "/send-all?bannerChanceOnly=true", "")
                                    .andExpect(status().isOk()));
            // the first entry of the pair couldn't go, so its chance is still free for the next
            assertThat(JsonPath.<List<Integer>>read(result, "$.sent")).containsExactly((int) next);
            assertThat(number(result, "$.skipped[0].targetId").longValue()).isEqualTo(tooBig);
            assertThat(JsonPath.<String>read(result, "$.skipped[0].reason"))
                    .isEqualTo("NOT_ENOUGH_UNITS");
            assertThat(homeAxemen()).isEqualTo(10);
        } finally {
            jdbc.sql("delete from movement where target_village_id = ?")
                    .param(victim.villageId)
                    .update();
            removeBarbarians(secondOfVictim);
        }
    }

    @Test
    void sendBannerChancesNeedsBannerDrops() throws Exception {
        double factor =
                jdbc.sql("select banner_drop_factor from world_settings")
                        .query(Double.class)
                        .single();
        try {
            jdbc.sql("update world_settings set banner_drop_factor = 0").update();
            worldService.reload();
            farmer.browser
                    .post(path(farmer) + "/send-all?bannerChanceOnly=true", "")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
            // the plain "send all" doesn't care
            sendAll();
        } finally {
            jdbc.sql("update world_settings set banner_drop_factor = ?").param(factor).update();
            worldService.reload();
        }
    }

    private void roll(String loserKey, Instant at) {
        jdbc.sql("insert into banner_roll (winner_id, loser_key, rolled_at) values (?, ?, ?)")
                .params(farmer.playerId, loserKey, Timestamp.from(at))
                .update();
    }

    @Test
    void sendAllOnAnEmptyListSendsNothing() throws Exception {
        String result = sendAll();
        assertThat(JsonPath.<List<Object>>read(result, "$.sent")).isEmpty();
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
            jdbc.sql(
                            "delete from movement where target_village_id = :id"
                                    + " or source_village_id = :id")
                    .param("id", village.id())
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
