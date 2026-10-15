package de.bannerfall.combat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Loyalty and conquest (M11, 02-game-rules §10): nobles that survive a won battle lower the
 * loyalty, at 0 the village changes owner. Luck is off ({@code luckRange = 0}) and the dice are
 * fixed after registering ({@link TestRandomSource}): {@code 0.0} makes every noble hit 20, {@code
 * 0.99} hits 35. The noble range is opened up, so any two test players can reach each other, and
 * loyalty doesn't grow back on the way (regeneration 0) unless a test says so.
 */
@IntegrationTest
class ConquestFlowTest {

    /** Every noble hits 20. */
    private static final double LOW_HIT = 0.0;

    /** Every noble hits 35. */
    private static final double HIGH_HIT = 0.99;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired TestRandomSource dice;
    @Autowired BarbarianService barbarians;
    @Autowired VillageRepository villages;
    @Autowired PlayerRepository players;
    @Autowired SentMessages sent;

    private double luckRangeBefore;
    private int nobleMaxDistanceBefore;
    private double loyaltyRegenBefore;
    private TestPlayer attacker;
    private TestPlayer defender;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        loyaltyRegenBefore = worldService.settings().loyaltyRegenPerHour();
        jdbc.sql(
                        "update world_settings set luck_range = 0, noble_max_distance = 1000,"
                                + " loyalty_regen_per_hour = 0")
                .update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        endProtection(defender);
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set luck_range = ?, noble_max_distance = ?,"
                                + " loyalty_regen_per_hour = ?")
                .params(luckRangeBefore, nobleMaxDistanceBefore, loyaltyRegenBefore)
                .update();
        worldService.reload();
    }

    @Test
    void noblesLowerTheLoyaltyAndTheThirdWaveConquers() throws Exception {
        dice.use(FixedRandomSource.constant(LOW_HIT));
        int defenderPoints = number(body(defender.village()), "$.points").intValue();
        String defenderVillageName = villageName(defender);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":2}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 100, "after", 60, "conquered", false));
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("DEFENSE"), "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 100, "after", 60, "conquered", false));
        defender.village().andExpect(jsonPath("$.loyalty").value(60));
        // the survivors walk home as usual
        assertThat(JsonPath.<String>read(body(attacker.troops()), "$.incoming[0].kind"))
                .isEqualTo("RETURN");

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":2}"));
        defender.village().andExpect(jsonPath("$.loyalty").value(20));
        int messagesBefore = sent.toPlayer(defender.playerId).size();

        Instant conquest = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":2}");
        arriveAt(conquest);

        // the village is the attacker's now, with loyalty 25 and its name kept
        defender.village().andExpect(status().isNotFound());
        attacker.browser
                .get("/api/villages/" + defender.villageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyalty").value(25))
                .andExpect(jsonPath("$.name").value(defenderVillageName));
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                attacker.newestReport("ATTACK"), "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 20, "after", 0, "conquered", true));

        // CONQUEST for the attacker, VILLAGE_LOST for the defender, with the village as it was
        assertThat(attacker.reportKinds()).startsWith("CONQUEST", "ATTACK");
        assertThat(defender.reportKinds()).startsWith("VILLAGE_LOST", "DEFENSE");
        String conquered = attacker.newestReport("CONQUEST");
        assertThat(JsonPath.<String>read(conquered, "$.payload.type")).isEqualTo("CONQUEST");
        assertThat(instant(conquered, "$.happenedAt")).isEqualTo(conquest);
        assertThat(number(conquered, "$.payload.village.playerId").longValue())
                .isEqualTo(defender.playerId);
        assertThat(number(conquered, "$.payload.from.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(JsonPath.<String>read(conquered, "$.payload.newName"))
                .isEqualTo(villageName(attacker, defender.villageId));
        assertThat((Object) JsonPath.read(defender.newestReport("VILLAGE_LOST"), "$.payload"))
                .isEqualTo(JsonPath.read(conquered, "$.payload"));

        // one noble is used up, the others stay as a support stack; nothing walks home
        String troops =
                body(attacker.browser.get("/api/villages/" + defender.villageId + "/troops"));
        assertThat(number(troops, "$.supportsHere[0].homeVillage.id").longValue())
                .isEqualTo(attacker.villageId);
        assertThat(JsonPath.<Map<String, Integer>>read(troops, "$.supportsHere[0].units"))
                .isEqualTo(Map.of("AXEMAN", 10, "NOBLE", 1));
        attacker.troops().andExpect(jsonPath("$.incoming").isEmpty());

        // villages and points of both players
        Player winner = players.findById(attacker.playerId).orElseThrow();
        Player loser = players.findById(defender.playerId).orElseThrow();
        assertThat(winner.getVillageCount()).isEqualTo(2);
        assertThat(winner.getPoints())
                .isEqualTo(points(attacker.villageId) + points(defender.villageId));
        assertThat(points(defender.villageId)).isEqualTo(defenderPoints);
        assertThat(loser.getVillageCount()).isZero();
        assertThat(loser.getPoints()).isZero();
        attacker.browser.get("/api/me").andExpect(jsonPath("$.villages.length()").value(2));
        defender.browser
                .get("/api/me")
                .andExpect(jsonPath("$.villages").isEmpty())
                .andExpect(jsonPath("$.needsRespawn").value(true));
        attacker.browser.get("/api/me").andExpect(jsonPath("$.needsRespawn").value(false));

        // told live
        List<String> toDefender = sent.toPlayer(defender.playerId);
        assertThat(toDefender.subList(messagesBefore, toDefender.size()))
                .anySatisfy(
                        json ->
                                assertThat(json)
                                        .contains("\"type\":\"VILLAGE_LOST\"")
                                        .contains("\"villageId\":" + defender.villageId))
                .noneSatisfy(json -> assertThat(json).contains("VILLAGE_UPDATED"));
        assertThat(sent.toPlayer(attacker.playerId))
                .anySatisfy(json -> assertThat(json).contains("\"type\":\"VILLAGE_CONQUERED\""));
    }

    @Test
    void enoughNoblesInOneAttackConquerAtOnce() throws Exception {
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        jdbc.sql("update village set wood = 800, clay = 800, iron = 800 where id = ?")
                .params(defender.villageId)
                .update();

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}")); // 3 × 35 > 100

        assertThat(owner(defender.villageId)).isEqualTo(attacker.playerId);
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 100, "after", 0, "conquered", true));
        // the army stays in the village, so it takes no loot: the stock stays there
        assertThat(JsonPath.<Map<String, Integer>>read(report, "$.payload.loot"))
                .isEqualTo(Map.of("wood", 0, "clay", 0, "iron", 0));
        attacker.browser
                .get("/api/villages/" + defender.villageId)
                .andExpect(jsonPath("$.resources.wood", greaterThanOrEqualTo(800.0)));
    }

    @Test
    void noblesThatDieDoNothingAndAttacksWithoutNoblesShowTheAttackerTheUnchangedLoyalty()
            throws Exception {
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("DEFENDER_WON");
        assertThat((Object) JsonPath.read(defender.newestReport("DEFENSE"), "$.payload.loyalty"))
                .isNull();
        defender.village().andExpect(jsonPath("$.loyalty").value(100));
        // nobody came back, so the attacker learns nothing about the loyalty
        assertThat((Object) JsonPath.read(attacker.newestReport("ATTACK"), "$.payload.loyalty"))
                .isNull();

        setUnits(defender, Map.of());
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10}"));
        // no nobles: the attacker sees the loyalty as it is (M21), the defender's copy has no entry
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                attacker.newestReport("ATTACK"), "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 100, "after", 100, "conquered", false));
        assertThat((Object) JsonPath.read(defender.newestReport("DEFENSE"), "$.payload.loyalty"))
                .isNull();
    }

    @Test
    void loyaltyGrowsBackByItselfAndTheMapShowsIt() throws Exception {
        jdbc.sql("update world_settings set loyalty_regen_per_hour = 1").update();
        worldService.reload();
        dice.use(FixedRandomSource.constant(LOW_HIT));
        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":2}");
        arriveAt(arrival); // the regeneration up to the arrival happens first: 100 is the most
        defender.village().andExpect(jsonPath("$.loyalty").value(60));

        // speed 1: one point per hour, nothing written in between. The owner's map is live ...
        clock.set(arrival.plus(Duration.ofHours(10)).plusSeconds(1800));
        Coordinates at = coordinates(defender);
        String mapUrl = "/api/map?x=" + at.x() + "&y=" + at.y() + "&w=1&h=1";
        String own = body(defender.browser.get(mapUrl));
        assertThat(number(own, "$[0].loyalty").intValue()).isEqualTo(70);
        assertThat((Object) JsonPath.read(own, "$[0].loyaltySeenAt")).isNull();
        defender.village().andExpect(jsonPath("$.loyalty").value(70));
        // ... the attacker's shows what he saw at the arrival, not what it is now (M21)
        String seen = body(attacker.browser.get(mapUrl));
        assertThat(number(seen, "$[0].loyalty").intValue()).isEqualTo(60);
        assertThat(Instant.parse(JsonPath.read(seen, "$[0].loyaltySeenAt"))).isEqualTo(arrival);

        clock.advance(Duration.ofDays(3));
        defender.village().andExpect(jsonPath("$.loyalty").value(100));
    }

    @Test
    void aBannerFlyingInAConqueredVillageGoesIntoTheConquerorsStash() throws Exception {
        // a tier II war banner flies in the defender's village (M32a, 02-game-rules §21.6.1)
        long banner = flyBanner(defender, defender.villageId, "WAR", 2);
        dice.use(FixedRandomSource.constant(HIGH_HIT));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(owner(defender.villageId)).isEqualTo(attacker.playerId);
        // intact (same type and tier), not flying any more, and his now
        assertThat(
                        jdbc.sql(
                                        "select owner_id || ':' || type || ':' || tier || ':' ||"
                                                + " coalesce(equipped_village_id::text, '-')"
                                                + " from banner where id = ?")
                                .param(banner)
                                .query(String.class)
                                .single())
                .isEqualTo(attacker.playerId + ":WAR:2:-");
        assertThat(
                        count(
                                "select count(*) from banner_event where banner_id = ? and kind ="
                                        + " 'CONQUERED'",
                                banner))
                .isEqualTo(1);
        attacker.browser
                .get("/api/banners")
                .andExpect(jsonPath("$.banners.length()").value(1))
                .andExpect(jsonPath("$.banners[0].villageId").doesNotExist());
        defender.browser.get("/api/banners").andExpect(jsonPath("$.banners.length()").value(0));
        // both conquest reports name the banner that moved (M32b)
        Map<String, Object> moved = Map.of("type", "WAR", "tier", 2, "toStash", true);
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                attacker.newestReport("CONQUEST"), "$.payload.banner"))
                .isEqualTo(moved);
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("VILLAGE_LOST"), "$.payload.banner"))
                .isEqualTo(moved);
        // the report list tags the conqueror's report only
        assertThat(attacker.bannersGainedIn("CONQUEST"))
                .containsExactly(Map.of("type", "WAR", "tier", 2));
        assertThat(defender.bannersGainedIn("VILLAGE_LOST")).isEmpty();
        // M32c: the conqueror took a banner that wasn't his, the defender lost his colours
        assertThat(achievementLevel(attacker, "BANNER_THIEF")).isEqualTo(1);
        assertThat(achievementLevel(defender, "COLOURS_LOST")).isEqualTo(1);
    }

    private int achievementLevel(TestPlayer player, String key) {
        return jdbc.sql(
                        "select coalesce(max(level), 0) from player_achievement where player_id = ?"
                                + " and achievement = ?")
                .params(player.playerId, key)
                .query(Integer.class)
                .single();
    }

    @Test
    void aBarbarianVillageIsConqueredAndGetsAName() throws Exception {
        Village barbarian = barbarians.spawnAt(freeFieldNear(coordinates(attacker)));
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        int reportsOfDefender = defender.reportKinds().size();

        arriveAt(attack(attacker, barbarian.coordinates(), "{\"AXEMAN\":10,\"NOBLE\":3}"));

        String village =
                body(
                        attacker.browser
                                .get("/api/villages/" + barbarian.getId())
                                .andExpect(status().isOk()));
        String expectedName = villageName(attacker, attacker.villageId);
        assertThat(JsonPath.<String>read(village, "$.name")).isEqualTo(expectedName);
        assertThat(number(village, "$.loyalty").intValue()).isEqualTo(25);
        String conquered = attacker.newestReport("CONQUEST");
        assertThat((Object) JsonPath.read(conquered, "$.payload.village.name")).isNull();
        assertThat((Object) JsonPath.read(conquered, "$.payload.village.playerId")).isNull();
        assertThat(JsonPath.<String>read(conquered, "$.payload.newName")).isEqualTo(expectedName);
        assertThat(villages.findBarbarianIds()).doesNotContain(barbarian.getId());
        assertThat(players.findById(attacker.playerId).orElseThrow().getVillageCount())
                .isEqualTo(2);
        assertThat(defender.reportKinds()).hasSize(reportsOfDefender);
    }

    @Test
    void thePreviousOwnersOrdersAndUnitsOutsideTheVillageAreLost() throws Exception {
        TestPlayer neighbour = TestPlayer.register(mockMvc);
        endProtection(neighbour);
        jdbc.sql(
                        "update unit_stack set u_axeman = 50"
                                + " where home_village_id = ? and location_village_id = ?")
                .params(defender.villageId, defender.villageId)
                .update();
        long supportId = insertStack(defender.villageId, neighbour.villageId, 7);
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        Instant conquest = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}");
        // a second before: nothing of it can be done or arrive by then
        clock.set(conquest.minusSeconds(1));
        long buildOrderId = defender.enqueueOk(BuildingType.WOODCUTTER);
        jdbc.sql("update village set b_barracks = 1 where id = ?")
                .param(defender.villageId)
                .update();
        defender.recruit(UnitType.SPEARMAN, 3).andExpect(status().isCreated());
        // a research that still runs is lost with the village, a finished one is the player's;
        // "still runs" at the battle's time, also when the worker gets to it a little later
        insertResearch(defender, 3, conquest.plusSeconds(3));
        insertResearch(defender, 2, conquest.minusSeconds(3600));
        long defenderAttackId =
                number(
                                body(
                                        defender.command(
                                                        "ATTACK",
                                                        coordinates(neighbour),
                                                        "{\"AXEMAN\":50}")
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        int neighbourMessagesBefore = sent.toPlayer(neighbour.playerId).size();
        neighbour.troops().andExpect(jsonPath("$.incoming.length()").value(1));

        clock.set(conquest.plusSeconds(10));
        processor.processDue(conquest);

        assertThat(owner(defender.villageId)).isEqualTo(attacker.playerId);
        assertThat(count("select count(*) from unit_stack where id = ?", supportId)).isZero();
        assertThat(
                        count(
                                "select count(*) from movement where home_village_id = ?",
                                defender.villageId))
                .isZero();
        // ref_id points into different tables per kind, so the kind belongs to the lookup: the
        // attacker's own RETURN or a recruit order may have the same id as these rows
        assertThat(
                        count(
                                "select count(*) from game_event where kind = 'MOVEMENT_ARRIVE'"
                                        + " and ref_id = ?",
                                defenderAttackId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from game_event where kind = 'BUILD_COMPLETE'"
                                        + " and ref_id = ?",
                                buildOrderId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from build_order where village_id = ?",
                                defender.villageId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from recruit_order where village_id = ?",
                                defender.villageId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from research_order where village_id = ?",
                                defender.villageId))
                .isEqualTo(1);
        assertThat(count("select level from research_order where player_id = ?", defender.playerId))
                .isEqualTo(2);
        neighbour
                .troops()
                .andExpect(jsonPath("$.incoming").isEmpty())
                .andExpect(jsonPath("$.supportsHere").isEmpty());
        assertThat(
                        sent.toPlayer(neighbour.playerId)
                                .subList(
                                        neighbourMessagesBefore,
                                        sent.toPlayer(neighbour.playerId).size()))
                .hasSize(2)
                .allSatisfy(json -> assertThat(json).contains("VILLAGE_UPDATED"));
        // nothing is left that could still arrive
        assertThat(count("select count(*) from game_event where status = 'PENDING'")).isZero();
    }

    @Test
    void aPaladinOutsideTheLostVillageIsLostAndFreesTheSlot() throws Exception {
        TestPlayer neighbour = TestPlayer.register(mockMvc);
        endProtection(neighbour);
        // the defender's paladin stands in the neighbour's village as a support (M31)
        long supportId = insertStack(defender.villageId, neighbour.villageId, 0);
        jdbc.sql("update unit_stack set u_paladin = 1 where id = ?").param(supportId).update();
        defender.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(1));
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        Instant conquest = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}");

        arriveAt(conquest);

        assertThat(owner(defender.villageId)).isEqualTo(attacker.playerId);
        assertThat(count("select count(*) from unit_stack where id = ?", supportId)).isZero();
        // the player is out of villages and respawns, but the paladin did not come with him
        defender.browser.get("/api/paladin").andExpect(jsonPath("$.used").value(0));
    }

    @Test
    void aLaterAttackOnAVillageItsSenderConqueredArrivesLikeASupport() throws Exception {
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 30, UnitType.NOBLE, 4));
        Instant first = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}");
        clock.advance(Duration.ofMinutes(1));
        // a noble too, so it is as slow as the first one and arrives a minute later
        Instant second = attack(attacker, defender, "{\"AXEMAN\":20,\"NOBLE\":1}");
        // the attacker sees his own attack coming, but it is no threat to him
        arriveAt(first);
        attacker.browser
                .get("/api/villages/" + defender.villageId)
                .andExpect(jsonPath("$.incomingAttacks").value(0));
        // and he sees its units, like those of a support
        String incoming =
                body(attacker.browser.get("/api/villages/" + defender.villageId + "/troops"));
        assertThat(JsonPath.<String>read(incoming, "$.incoming[0].kind")).isEqualTo("ATTACK");
        assertThat(JsonPath.<Map<String, Integer>>read(incoming, "$.incoming[0].units"))
                .isEqualTo(Map.of("AXEMAN", 20, "NOBLE", 1));
        attacker.browser
                .get("/api/me")
                .andExpect(jsonPath("$.villages[1].incomingAttacks").value(0));

        arriveAt(second);

        String troops =
                body(attacker.browser.get("/api/villages/" + defender.villageId + "/troops"));
        assertThat(JsonPath.<Map<String, Integer>>read(troops, "$.supportsHere[0].units"))
                .isEqualTo(Map.of("AXEMAN", 30, "NOBLE", 3));
        assertThat(attacker.reportKinds()).startsWith("SUPPORT_ARRIVED", "CONQUEST", "ATTACK");
        attacker.browser
                .get("/api/villages/" + defender.villageId)
                .andExpect(jsonPath("$.loyalty").value(25));
    }

    @Test
    void aTrainClearsTheVillageAndItsNoblesConquerItInOrder() throws Exception {
        // M37, 02-game-rules §6.1: one confirm, the attacks land one after another
        dice.use(FixedRandomSource.constant(HIGH_HIT)); // every noble hits 35
        setUnits(attacker, Map.of(UnitType.AXEMAN, 300, UnitType.NOBLE, 4));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        String noble = "{\"AXEMAN\":10,\"NOBLE\":1}";
        int messagesBefore = sent.toPlayer(defender.playerId).size();

        String train =
                body(
                        attacker.train(
                                        coordinates(defender),
                                        "{\"AXEMAN\":260}",
                                        noble,
                                        noble,
                                        noble,
                                        noble)
                                .andExpect(status().isCreated()));
        List<String> arrivals = JsonPath.read(train, "$[*].arriveAt");
        assertThat(arrivals).hasSize(5).containsOnly(arrivals.getFirst());
        // the defender hears of the train once
        List<String> toDefender = sent.toPlayer(defender.playerId);
        assertThat(toDefender.subList(messagesBefore, toDefender.size()))
                .filteredOn(json -> json.contains("\"type\":\"INCOMING_ATTACK\""))
                .hasSize(1);

        arriveAt(Instant.parse(arrivals.getFirst()));

        // the clearing attack took the spearmen, so no noble died; 100 → 65 → 30 → conquered
        assertThat(owner(defender.villageId)).isEqualTo(attacker.playerId);
        assertThat(attacker.reportKinds())
                .startsWith("SUPPORT_ARRIVED", "CONQUEST", "ATTACK", "ATTACK", "ATTACK", "ATTACK");
        // the third noble stayed with its 10 axemen; the fourth attack found the village already
        // his and arrived like a support
        String troops =
                body(attacker.browser.get("/api/villages/" + defender.villageId + "/troops"));
        assertThat(JsonPath.<Map<String, Integer>>read(troops, "$.supportsHere[0].units"))
                .isEqualTo(Map.of("AXEMAN", 20, "NOBLE", 1));
        attacker.browser
                .get("/api/villages/" + defender.villageId)
                .andExpect(jsonPath("$.loyalty").value(25));
    }

    @Test
    void aPlayerWithoutVillagesRespawnsOnce() throws Exception {
        String defenderVillageName = villageName(defender);
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}"));
        dice.reset();
        clock.advance(Duration.ofHours(1));

        String village =
                body(defender.browser.post("/api/me/respawn", "").andExpect(status().isCreated()));

        long newVillageId = number(village, "$.id").longValue();
        assertThat(newVillageId).isNotEqualTo(defender.villageId);
        assertThat(JsonPath.<String>read(village, "$.name")).isEqualTo(defenderVillageName);
        String me = body(defender.browser.get("/api/me"));
        assertThat(JsonPath.<Boolean>read(me, "$.needsRespawn")).isFalse();
        assertThat(number(me, "$.villages[0].id").longValue()).isEqualTo(newVillageId);
        int hours = worldService.settings().beginnerProtectionHours();
        assertThat(instant(me, "$.protectedUntil"))
                .isEqualTo(clock.instant().plus(Duration.ofHours(hours)));
        defender.browser
                .get("/api/villages/" + newVillageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loyalty").value(100));
        assertThat(players.findById(defender.playerId).orElseThrow().getVillageCount())
                .isEqualTo(1);

        // only once, and never for a player who still has a village
        defender.browser
                .post("/api/me/respawn", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPAWN_NOT_ALLOWED"));
        attacker.browser
                .post("/api/me/respawn", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPAWN_NOT_ALLOWED"));
    }

    // ---- who sees the loyalty (M21, 02-game-rules §10) ----

    @Test
    void aVillagesLoyaltyIsKnownOnlyToItsOwnerAndThoseWhoAttackedIt() throws Exception {
        TestPlayer outsider = TestPlayer.register(mockMvc);
        Coordinates at = coordinates(defender);

        assertThat(loyaltyOnMap(attacker, at)).isNull();
        assertThat(loyaltyOnMap(outsider, at)).isNull();
        assertThat(loyaltyOnMap(defender, at)).isEqualTo(100);

        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":10}");
        arriveAt(arrival);

        String seen = mapAt(attacker, at);
        assertThat(number(seen, "$[0].loyalty").intValue()).isEqualTo(100);
        assertThat(instant(seen, "$[0].loyaltySeenAt")).isEqualTo(arrival);
        // the sighting is the attacker's own: nobody else gets it (guild mates: see below)
        assertThat(loyaltyOnMap(outsider, at)).isNull();
    }

    @Test
    void aGuildMateDoesNotShareTheSighting() throws Exception {
        TestPlayer mate = TestPlayer.register(mockMvc);
        String unique = Long.toString(System.nanoTime(), 36);
        long guild =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .params("Mates " + unique, unique.substring(unique.length() - 4))
                        .query(Long.class)
                        .single();
        jdbc.sql(
                        "insert into guild_member (player_id, guild_id, role, joined_at)"
                                + " values (?, ?, 'LEADER', now()), (?, ?, 'MEMBER', now())")
                .params(attacker.playerId, guild, mate.playerId, guild)
                .update();
        try {
            Coordinates at = coordinates(defender);

            arriveAt(attack(attacker, defender, "{\"AXEMAN\":10}"));

            assertThat(loyaltyOnMap(attacker, at)).isEqualTo(100);
            assertThat(loyaltyOnMap(mate, at)).isNull();
        } finally {
            jdbc.sql("delete from guild where id = ?").param(guild).update();
        }
    }

    @Test
    void anAttackThatNobodySurvivesShowsNothing() throws Exception {
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(loyaltyOnMap(attacker, coordinates(defender))).isNull();
    }

    @Test
    void theMapKeepsTheLastSeenValueAndTheNextAttackRefreshesIt() throws Exception {
        jdbc.sql("update world_settings set loyalty_regen_per_hour = 1").update();
        worldService.reload();
        dice.use(FixedRandomSource.constant(LOW_HIT));
        Coordinates at = coordinates(defender);

        Instant first = attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":2}");
        arriveAt(first);
        assertThat(loyaltyOnMap(attacker, at)).isEqualTo(60);

        // ten hours later the village is at 70, but the attacker still has his 60 from back then
        clock.set(first.plus(Duration.ofHours(10)).plusSeconds(60));
        assertThat(loyaltyOnMap(attacker, at)).isEqualTo(60);
        assertThat(loyaltyOnMap(defender, at)).isEqualTo(70);

        // a scouting-only attack counts: it is an attack, and it refreshes the sighting. Two more
        // hours first: the scouts arrive within minutes, which would not yet move 70 to 71.
        clock.advance(Duration.ofHours(2));
        Instant second = attack(attacker, defender, "{\"SCOUT\":5}");
        arriveAt(second);
        Integer now = loyaltyOnMap(defender, at);
        assertThat(now).isGreaterThan(70);
        assertThat(loyaltyOnMap(attacker, at)).isEqualTo(now);
    }

    @Test
    void theSightingSurvivesDeletingTheReport() throws Exception {
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10}"));
        List<Number> reportIds =
                JsonPath.read(body(attacker.reports()), "$.items[?(@.kind == 'ATTACK')].id");
        long reportId = reportIds.get(0).longValue();

        attacker.browser.delete("/api/reports/" + reportId).andExpect(status().isNoContent());

        assertThat(loyaltyOnMap(attacker, coordinates(defender))).isEqualTo(100);
    }

    @Test
    void aConqueredVillageShowsItsLiveLoyaltyToTheNewOwner() throws Exception {
        dice.use(FixedRandomSource.constant(HIGH_HIT));
        Coordinates at = coordinates(defender);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":3}"));

        String body = mapAt(attacker, at);
        assertThat(number(body, "$[0].loyalty").intValue()).isEqualTo(25);
        assertThat((Object) JsonPath.read(body, "$[0].loyaltySeenAt")).isNull();
    }

    @Test
    void aBarbarianVillageShowsItsLoyaltyOnceAttacked() throws Exception {
        Village barbarian = barbarians.spawnAt(freeFieldNear(coordinates(attacker)));
        Coordinates at = new Coordinates(barbarian.getX(), barbarian.getY());
        assertThat(loyaltyOnMap(attacker, at)).isNull();

        arriveAt(attack(attacker, at, "{\"AXEMAN\":10}"));

        assertThat(loyaltyOnMap(attacker, at)).isEqualTo(100);
    }

    @Test
    void ramsThatLowerABarbarianWallMarkItRazedAndAttacksWithoutRamsDoNot() throws Exception {
        Village walled = barbarians.spawnAt(freeFieldNear(coordinates(attacker)));
        jdbc.sql("update village set b_wall = 5 where id = ?").param(walled.getId()).update();

        arriveAt(attack(attacker, walled.coordinates(), "{\"AXEMAN\":20}"));
        assertThat(villages.findById(walled.getId()).orElseThrow().isWallRazed()).isFalse();

        arriveAt(attack(attacker, walled.coordinates(), "{\"AXEMAN\":20,\"RAM\":10}"));
        Village after = villages.findById(walled.getId()).orElseThrow();
        assertThat(after.getBuildings().get(BuildingType.WALL)).isLessThan(5);
        assertThat(after.isWallRazed()).isTrue();
    }

    private String mapAt(TestPlayer viewer, Coordinates at) throws Exception {
        return body(viewer.browser.get("/api/map?x=" + at.x() + "&y=" + at.y() + "&w=1&h=1"));
    }

    /**
     * What the viewer's map shows as the loyalty of the one village at {@code at}; null if hidden.
     */
    private Integer loyaltyOnMap(TestPlayer viewer, Coordinates at) throws Exception {
        return JsonPath.read(mapAt(viewer, at), "$[0].loyalty");
    }

    // ---- helpers ----

    /** Sends an attack from the player's village and returns its arrival. */
    private Instant attack(TestPlayer from, TestPlayer to, String unitsJson) throws Exception {
        return attack(from, coordinates(to), unitsJson);
    }

    /** Puts the units at home first (on top of what is there), then sends them. */
    private Instant attack(TestPlayer from, Coordinates target, String unitsJson) throws Exception {
        Map<String, Integer> units = JsonPath.read(unitsJson, "$");
        units.forEach(
                (type, count) ->
                        jdbc.sql(
                                        "update unit_stack set "
                                                + column(UnitType.valueOf(type))
                                                + " = greatest("
                                                + column(UnitType.valueOf(type))
                                                + ", ?)"
                                                + " where home_village_id = ?"
                                                + " and location_village_id = ?")
                                .params(count, from.villageId, from.villageId)
                                .update());
        String sent =
                body(from.command("ATTACK", target, unitsJson).andExpect(status().isCreated()));
        return instant(sent, "$.arriveAt");
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    /** The home stack has exactly these units. */
    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set "
                                    + column(type)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    private long insertStack(long homeId, long locationId, int spearmen) {
        return jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman)"
                                + " values (?, ?, ?) returning id")
                .params(homeId, locationId, spearmen)
                .query(Long.class)
                .single();
    }

    private void endProtection(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    /** A field next to {@code near} with no village on it or next to it. */
    private Coordinates freeFieldNear(Coordinates near) {
        List<Coordinates> occupied = villages.findAllCoordinates();
        for (int r = 2; r < 30; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    Coordinates spot = new Coordinates(near.x() + dx, near.y() + dy);
                    boolean free =
                            spot.x() >= 0
                                    && spot.y() >= 0
                                    && spot.x() < worldService.settings().mapSize()
                                    && spot.y() < worldService.settings().mapSize()
                                    && occupied.stream().allMatch(v -> v.distanceTo(spot) >= 2);
                    if (free) {
                        return spot;
                    }
                }
            }
        }
        throw new AssertionError("no free field near " + near);
    }

    private Long owner(long villageId) {
        return jdbc.sql("select player_id from village where id = ?")
                .param(villageId)
                .query(Long.class)
                .single();
    }

    private int points(long villageId) {
        return jdbc.sql("select points from village where id = ?")
                .param(villageId)
                .query(Integer.class)
                .single();
    }

    /** A research of the player's spearmen to {@code level}, done at {@code endAt}. */
    private void insertResearch(TestPlayer player, int level, Instant endAt) {
        jdbc.sql(
                        """
                        insert into research_order (player_id, village_id, unit, level, start_at,
                            end_at, cost_wood, cost_clay, cost_iron, created_at)
                        values (?, ?, 'SPEARMAN', ?, ?, ?, 0, 0, 0, ?)
                        """)
                .params(
                        player.playerId,
                        player.villageId,
                        level,
                        Timestamp.from(endAt.minusSeconds(100)),
                        Timestamp.from(endAt),
                        Timestamp.from(clock.instant()))
                .update();
    }

    /** A banner of this player's flying in one of his villages. */
    private long flyBanner(TestPlayer owner, long villageId, String type, int tier) {
        return jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, ?, ?, ?, now(), 'GRANTED', now()) returning id
                        """)
                .params(owner.playerId, type, tier, villageId)
                .query(Long.class)
                .single();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    /** The default village name of the player ("name's village"), read from his first village. */
    private static String villageName(TestPlayer player) throws Exception {
        return villageName(player, player.villageId);
    }

    private static String villageName(TestPlayer player, long villageId) throws Exception {
        return JsonPath.read(body(player.browser.get("/api/villages/" + villageId)), "$.name");
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.browser.get("/api/me"));
        return new Coordinates(
                number(village, "$.villages[0].x").intValue(),
                number(village, "$.villages[0].y").intValue());
    }

    private static String column(UnitType type) {
        return "u_" + type.name().toLowerCase(Locale.ROOT);
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
