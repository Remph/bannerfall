package de.bannerfall.achievement;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.Host;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.chat.ChatService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.event.EventScheduler;
import de.bannerfall.event.GameEventKind;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.guild.GuildService;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Achievements (M16, 02-game-rules §19): what earns them, the filters, who sees them, and the
 * host's revoke. Battles are calculated by hand as in {@code BattleFlowTest}: luck is off, units
 * and levels are set with SQL, the clock is moved by hand and the event worker is called directly.
 */
@IntegrationTest
class AchievementFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventScheduler scheduler;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;
    @Autowired TestRandomSource dice;
    @Autowired SentMessages sent;
    @Autowired AchievementService achievements;
    @Autowired ChatService chatService;
    @Autowired GuildService guildService;
    @Autowired PlayerRepository players;
    @Autowired PlatformTransactionManager transactionManager;

    private double luckRangeBefore;
    private int nobleMaxDistanceBefore;
    private double loyaltyRegenBefore;
    private double unitSpeedBefore;
    private TestPlayer attacker;
    private TestPlayer defender;
    private Coordinates defenderAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        loyaltyRegenBefore = worldService.settings().loyaltyRegenPerHour();
        unitSpeedBefore = worldService.settings().unitSpeed();
        jdbc.sql(
                        "update world_settings set luck_range = 0, noble_max_distance = 1000,"
                                + " loyalty_regen_per_hour = 0")
                .update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        defenderAt = coordinates(defender);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        jdbc.sql(
                        "update world_settings set luck_range = ?, noble_max_distance = ?,"
                            + " loyalty_regen_per_hour = ?, unit_speed = ?, guilds_enabled = false")
                .params(
                        luckRangeBefore,
                        nobleMaxDistanceBefore,
                        loyaltyRegenBefore,
                        unitSpeedBefore)
                .update();
        worldService.reload();
        guildService.setInviteInterval(Duration.ofMinutes(1));
        jdbc.sql("delete from guild").update();
        // some tests make one of the two an NPC lord: other test classes count the lords in the
        // shared database
        jdbc.sql("update player set npc = false where id in (?, ?)")
                .params(attacker.playerId, defender.playerId)
                .update();
    }

    // ---- the lists ----

    @Test
    void theOwnListStartsLockedAndOnlyAPlayerWithALoginSeesIt() throws Exception {
        String list = body(attacker.browser.get("/api/achievements").andExpect(status().isOk()));

        assertThat(JsonPath.<List<String>>read(list, "$.achievements[*].key"))
                .containsExactly(
                        java.util.Arrays.stream(Achievement.values())
                                .map(Enum::name)
                                .toArray(String[]::new));
        assertThat(JsonPath.<List<Integer>>read(list, "$.achievements[*].level")).containsOnly(0);
        // a levelled one shows its thresholds and progress, a one-time one neither
        assertThat(
                        JsonPath.<List<Integer>>read(
                                list, "$.achievements[?(@.key=='VICTOR')].thresholds[*]"))
                .containsExactly(25, 250, 2500);
        assertThat(
                        JsonPath.<List<Integer>>read(
                                list, "$.achievements[?(@.key=='VICTOR')].progress"))
                .containsExactly(0);
        assertThat(
                        JsonPath.<List<Object>>read(
                                list, "$.achievements[?(@.key=='FIRST_BLOOD')].thresholds[*]"))
                .isEmpty();
        // the points and the villages are read from the game itself
        assertThat(
                        JsonPath.<List<Integer>>read(
                                list, "$.achievements[?(@.key=='LANDLORD')].progress"))
                .containsExactly(1);

        Browser anonymous = Browser.open(mockMvc);
        anonymous.get("/api/achievements").andExpect(status().isUnauthorized());
    }

    @Test
    void theProfileShowsTheEarnedOnesToEverybodyAndNothingForAnNpcLord() throws Exception {
        attacker.browser
                .get("/api/players/" + defender.playerId)
                .andExpect(jsonPath("$.achievements", hasSize(0)));

        wins(20, 1);

        defender.browser
                .get("/api/players/" + attacker.playerId)
                .andExpect(jsonPath("$.achievements", hasSize(1)))
                .andExpect(jsonPath("$.achievements[0].key").value("FIRST_BLOOD"))
                .andExpect(jsonPath("$.achievements[0].category").value("COMBAT"))
                .andExpect(jsonPath("$.achievements[0].level").value(1))
                .andExpect(jsonPath("$.achievements[0].earnedAt").isNotEmpty());
        // the second win is a counter with no level yet: nothing more to show
        jdbc.sql("update player set npc = true where id = ?").param(defender.playerId).update();
        attacker.browser
                .get("/api/players/" + defender.playerId)
                .andExpect(jsonPath("$.achievements", hasSize(0)));
    }

    // ---- combat ----

    @Test
    void aWinEarnsFirstBloodAndCountsLootOnlyOncePerVillageAndDay() throws Exception {
        setStock(defender, 300, 300, 300);

        Instant arrival = attackWithAxemen(20);
        arriveAt(arrival);

        assertThat(earned(attacker)).containsEntry("FIRST_BLOOD", 1);
        assertThat(counter(attacker, "VICTOR")).isEqualTo(1);
        // 20 axemen carry 300: that is the whole loot of the report
        assertThat(counter(attacker, "PLUNDERER")).isEqualTo(300);
        assertThat(sent.toPlayer(attacker.playerId))
                .anySatisfy(
                        json ->
                                assertThat(json)
                                        .contains("\"type\":\"ACHIEVEMENT_EARNED\"")
                                        .contains("\"achievement\":\"FIRST_BLOOD\"")
                                        .contains("\"level\":1"));
        assertThat(earned(defender)).isEmpty();

        // the same village again within a day (24 h ÷ speed 1): the battle happens, nothing counts
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setStock(defender, 300, 300, 300);
        arriveAt(attackWithAxemen(20));
        assertThat(counter(attacker, "VICTOR")).isEqualTo(1);
        assertThat(counter(attacker, "PLUNDERER")).isEqualTo(300);

        // a day after the counted win it counts again
        clock.set(arrival.plus(Duration.ofHours(24)).plusSeconds(1));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setStock(defender, 300, 300, 300);
        arriveAt(attackWithAxemen(20));
        assertThat(counter(attacker, "VICTOR")).isEqualTo(2);
        assertThat(counter(attacker, "PLUNDERER")).isEqualTo(600);
    }

    @Test
    void anAttackOfFewerThanTenUnitsCountsForNothing() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 9));

        arriveAt(attackWithAxemen(9));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat(earned(attacker)).isEmpty();
        assertThat(counter(attacker, "VICTOR")).isZero();
        assertThat(jdbc.sql("select count(*) from battle_credit").query(Long.class).single())
                .isGreaterThanOrEqualTo(0L);
        assertThat(
                        jdbc.sql("select count(*) from battle_credit where attacker_id = ?")
                                .param(attacker.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    @Test
    void aSuccessfulDefenceEarnsShieldAndBulwarkOncePerAttackerVillageAndDay() throws Exception {
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));

        Instant first = attackWithAxemen(20);
        arriveAt(first);

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("DEFENDER_WON");
        assertThat(earned(defender)).containsEntry("SHIELD", 1);
        assertThat(counter(defender, "BULWARK")).isEqualTo(1);
        assertThat(earned(attacker)).isEmpty();

        // the attacker sends the next ones at once (his axemen died, so he gets new ones)
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));
        arriveAt(attackWithAxemen(20));
        assertThat(counter(defender, "BULWARK")).isEqualTo(1);

        clock.set(first.plus(Duration.ofHours(24)).plusSeconds(1));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));
        arriveAt(attackWithAxemen(20));
        assertThat(counter(defender, "BULWARK")).isEqualTo(2);
    }

    @Test
    void scoutingOnlyAndSupportEarnNothing() throws Exception {
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attack("{\"SCOUT\":10}"));
        assertThat(earned(attacker)).isEmpty();
        assertThat(earned(defender)).isEmpty();
        assertThat(counter(attacker, "VICTOR")).isZero();
    }

    @Test
    void ramsThatLowerTheWallEarnBreach() throws Exception {
        jdbc.sql("update village set b_wall = 3 where id = ?").param(defender.villageId).update();
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50, UnitType.RAM, 20));

        arriveAt(attack("{\"AXEMAN\":50,\"RAM\":20}"));

        assertThat(
                        jdbc.sql("select b_wall from village where id = ?")
                                .param(defender.villageId)
                                .query(Integer.class)
                                .single())
                .isLessThan(3);
        assertThat(earned(attacker)).containsEntry("BREACH", 1).containsEntry("FIRST_BLOOD", 1);
    }

    @Test
    void anNpcLordEarnsNothingAndLeavesNoCredit() throws Exception {
        jdbc.sql("update player set npc = true where id = ?").param(attacker.playerId).update();

        arriveAt(attackWithAxemen(20));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat(earned(attacker)).isEmpty();
        assertThat(counter(attacker, "VICTOR")).isZero();
        assertThat(
                        jdbc.sql("select count(*) from battle_credit where attacker_id = ?")
                                .param(attacker.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
    }

    // ---- battles (1.4, 02 §19.1) ----

    @Test
    void slayerAndWardenCountTheKillsOfEveryBattleOnBothSides() throws Exception {
        // 50 axemen (A 2000) beat 30 spearmen (D 470) and lose round(50 × 0.235^1.5) = 6
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attackWithAxemen(50));
        assertThat(counter(attacker, "SLAYER")).isEqualTo(30);
        assertThat(counter(defender, "WARDEN")).isEqualTo(6);

        // at once again, lost: 20 axemen (A 800) against 100 swordsmen (D 5020); no day filter,
        // every kill counts
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));
        arriveAt(attackWithAxemen(20));
        assertThat(counter(attacker, "SLAYER")).isEqualTo(36);
        assertThat(counter(defender, "WARDEN")).isEqualTo(26);
        assertThat(earned(attacker)).doesNotContainKeys("PYRRHIC_VICTORY", "LUCKY_STRIKE");
        assertThat(earned(defender)).doesNotContainKey("LAST_STAND");
    }

    @Test
    void scoutsThatAllDieEarnSpyCatcherForTheVillagesOwner() throws Exception {
        setUnits(defender, Map.of(UnitType.SCOUT, 5));
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));

        // more scouts than the defender: some survive, nothing
        arriveAt(attack("{\"SCOUT\":10}"));
        assertThat(earned(defender)).doesNotContainKey("SPY_CATCHER");

        setUnits(attacker, Map.of(UnitType.SCOUT, 5));
        arriveAt(attack("{\"SCOUT\":5}"));
        assertThat(earned(defender)).containsEntry("SPY_CATCHER", 1);
        // the dead scouts count for Warden too
        assertThat(counter(defender, "WARDEN")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void aWinThatCostsThreeQuartersOfAHundredUnitsIsAPyrrhicVictory() throws Exception {
        // 100 axemen (A 4000) against 220 spearmen (D 3320): ratio 0.83^1.5 = 0.756, 76 die
        setUnits(defender, Map.of(UnitType.SPEARMAN, 220));

        arriveAt(attackWithAxemen(100));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat(earned(attacker)).containsEntry("PYRRHIC_VICTORY", 1);
        assertThat(counter(attacker, "SLAYER")).isEqualTo(220);
    }

    @Test
    void outnumberedDefendersHoldALastStandAndTheirSupportersAreBrothersInArms() throws Exception {
        TestPlayer ally = TestPlayer.register(mockMvc);
        // 5 own and 30 supporting swordsmen: D 1770 against 40 axemen (A 1600)
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 5));
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_swordsman)"
                                + " values (?, ?, 30)")
                .params(ally.villageId, defender.villageId)
                .update();

        arriveAt(attackWithAxemen(40));

        assertThat(earned(defender)).containsEntry("SHIELD", 1).containsEntry("LAST_STAND", 1);
        assertThat(earned(ally)).containsOnlyKeys("BROTHERS_IN_ARMS");
        assertThat(earned(attacker)).doesNotContainKey("BROTHERS_IN_ARMS");
    }

    @Test
    void beatingThePlayerWhoBeatYouWithinADayEarnsAvenger() throws Exception {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), attacker.playerId)
                .update();
        // fast units: wherever the two villages lie, the answer arrives within the day
        jdbc.sql("update world_settings set unit_speed = 100").update();
        worldService.reload();
        // the defender strikes first and wins against the attacker's empty village
        setUnits(defender, Map.of(UnitType.AXEMAN, 20));
        String json =
                "{\"kind\":\"ATTACK\",\"target\":{\"x\":%d,\"y\":%d},\"units\":{\"AXEMAN\":20}}"
                        .formatted(coordinates(attacker).x(), coordinates(attacker).y());
        arriveAt(
                Instant.parse(
                        JsonPath.read(
                                body(
                                        defender.browser
                                                .post(defender.villagePath() + "/commands", json)
                                                .andExpect(status().isCreated())),
                                "$.arriveAt")));
        assertThat(earned(attacker)).doesNotContainKey("AVENGER");

        // the answer: a counted win against him
        arriveAt(attackWithAxemen(50));

        assertThat(earned(attacker)).containsEntry("AVENGER", 1);
        assertThat(earned(defender)).doesNotContainKey("AVENGER");
    }

    @Test
    void anOldDefeatIsNoReasonForAvenger() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                status ->
                        achievements.beaten(
                                players.findById(attacker.playerId).orElseThrow(),
                                players.findById(defender.playerId).orElseThrow(),
                                clock.instant().minus(Duration.ofHours(25))));

        arriveAt(attackWithAxemen(20));

        assertThat(earned(attacker)).containsEntry("FIRST_BLOOD", 1).doesNotContainKey("AVENGER");
    }

    @Test
    void catapultsThatTakeFiveLevelsOffABuildingEarnDemolisher() throws Exception {
        jdbc.sql("update village set b_barracks = 10 where id = ?")
                .param(defender.villageId)
                .update();
        // 4 + 2 × L catapults per level: 24 + 22 + 20 + 18 + 16 = 100 take the Barracks to 5
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50, UnitType.CATAPULT, 100));

        // the units' JSON is followed by the command's catapult target
        arriveAt(attack("{\"AXEMAN\":50,\"CATAPULT\":100},\"catapultTarget\":\"BARRACKS\""));

        assertThat(
                        jdbc.sql("select b_barracks from village where id = ?")
                                .param(defender.villageId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(5);
        assertThat(earned(attacker)).containsEntry("DEMOLISHER", 1);
    }

    @Test
    void wallLevelsTheRamsTakeAreNoDemolisher() throws Exception {
        jdbc.sql("update village set b_wall = 10 where id = ?").param(defender.villageId).update();
        // 2 + L rams per level: the 50 of 70 rams that surely survive take the wall to 5 or
        // lower, then 14 catapults (4 + 2 L per level) take one level at most
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.RAM, 70, UnitType.CATAPULT, 14));

        arriveAt(attack("{\"AXEMAN\":100,\"RAM\":70,\"CATAPULT\":14},\"catapultTarget\":\"WALL\""));

        assertThat(
                        jdbc.sql("select b_wall from village where id = ?")
                                .param(defender.villageId)
                                .query(Integer.class)
                                .single())
                .isLessThanOrEqualTo(5);
        assertThat(earned(attacker)).containsKey("BREACH").doesNotContainKey("DEMOLISHER");
    }

    @Test
    void aWinThatOnlyLuckMadeEarnsLuckyStrike() throws Exception {
        jdbc.sql("update world_settings set luck_range = 0.25").update();
        worldService.reload();
        // luck −0.25 + 0.5 × 0.6 = +5 %: 20 axemen hit with 840 instead of 800, 16 swordsmen
        // hold 820
        dice.use(FixedRandomSource.constant(0.6));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 16));

        arriveAt(attackWithAxemen(20));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat(earned(attacker)).containsEntry("LUCKY_STRIKE", 1);
    }

    @Test
    void aWinThatLuckOnlyHelpedIsNoLuckyStrike() throws Exception {
        jdbc.sql("update world_settings set luck_range = 0.25").update();
        worldService.reload();
        dice.use(FixedRandomSource.constant(0.99));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 10));

        arriveAt(attackWithAxemen(20));

        assertThat(earned(attacker)).containsKey("FIRST_BLOOD").doesNotContainKey("LUCKY_STRIKE");
    }

    @Test
    void whatTheMarketExchangeCostsCountsForMerchantPrince() throws Exception {
        jdbc.sql(
                        "update village set b_market = 5, b_warehouse = 20, wood = 5000,"
                                + " clay = 5000, iron = 5000, resources_updated_at = ?"
                                + " where id = ?")
                .params(Timestamp.from(clock.instant()), attacker.villageId)
                .update();

        attacker.browser
                .post(
                        attacker.villagePath() + "/market/exchange",
                        "{\"give\":\"WOOD\",\"receive\":\"IRON\",\"amount\":1000}")
                .andExpect(status().isOk());

        // 1000 iron at the default ratio 1.5 cost 1500 wood
        assertThat(counter(attacker, "MERCHANT_PRINCE")).isEqualTo(1500);
        assertThat(earned(attacker)).doesNotContainKey("FIRST_DEAL");
    }

    @Test
    void aCounterAlreadyPastALoweredLevelGetsItAtTheNextLook() throws Exception {
        // as if Bulwark had been 5 / 25 / 100 when the counter reached 4
        jdbc.sql(
                        "insert into achievement_counter (player_id, achievement, value)"
                                + " values (?, 'BULWARK', 4)")
                .param(defender.playerId)
                .update();
        assertThat(earned(defender)).isEmpty();

        defender.browser.get("/api/achievements").andExpect(status().isOk());

        assertThat(earned(defender)).containsEntry("BULWARK", 1);
    }

    @Test
    void aLevelEarnedBeforeANumberWentUpStaysAtLogin() throws Exception {
        // Victor silver at 60 wins under the old 10 / 50 / 250, now silver needs 250
        jdbc.sql(
                        "insert into achievement_counter (player_id, achievement, value)"
                                + " values (?, 'VICTOR', 60)")
                .param(attacker.playerId)
                .update();
        jdbc.sql(
                        "insert into player_achievement (player_id, achievement, level, earned_at)"
                                + " values (?, 'VICTOR', 1, ?), (?, 'VICTOR', 2, ?)")
                .params(
                        attacker.playerId,
                        Timestamp.from(clock.instant()),
                        attacker.playerId,
                        Timestamp.from(clock.instant()))
                .update();

        attacker.browser.get("/api/achievements").andExpect(status().isOk());

        assertThat(earned(attacker)).containsEntry("VICTOR", 2);
        assertThat(levelRows(attacker, "VICTOR")).isEqualTo(2);
    }

    // ---- conquest ----

    @Test
    void aConquestEarnsConquerorTopplerAndFallenAndARespawnPhoenix() throws Exception {
        dice.use(FixedRandomSource.constant(0.99));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.NOBLE, 3));

        arriveAt(attack("{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(earned(attacker)).containsEntry("CONQUEROR", 1).containsEntry("TOPPLER", 1);
        assertThat(earned(attacker)).doesNotContainKey("LORD_BREAKER");
        // the loser is told, the winner is not "Fallen"
        assertThat(earned(defender)).containsEntry("FALLEN", 1).doesNotContainKey("CONQUEROR");

        defender.browser.get("/api/me").andExpect(jsonPath("$.needsRespawn").value(true));
        defender.browser.post("/api/me/respawn", "").andExpect(status().isCreated());
        assertThat(earned(defender)).containsEntry("PHOENIX", 1);
    }

    @Test
    void aVillageOfAnNpcLordEarnsLordBreakerAndNeverFallenForTheLord() throws Exception {
        dice.use(FixedRandomSource.constant(0.99));
        jdbc.sql("update player set npc = true where id = ?").param(defender.playerId).update();
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.NOBLE, 3));

        arriveAt(attack("{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(earned(attacker))
                .containsEntry("CONQUEROR", 1)
                .containsEntry("LORD_BREAKER", 1)
                .doesNotContainKey("TOPPLER");
        assertThat(earned(defender)).isEmpty();
    }

    @Test
    void anNpcLordConquestMakesAHumanFallen() throws Exception {
        dice.use(FixedRandomSource.constant(0.99));
        jdbc.sql("update player set npc = true where id = ?").param(attacker.playerId).update();
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.NOBLE, 3));

        arriveAt(attack("{\"AXEMAN\":10,\"NOBLE\":3}"));

        assertThat(earned(attacker)).isEmpty();
        assertThat(earned(defender)).containsEntry("FALLEN", 1);
    }

    // ---- building and recruiting ----

    @Test
    void completedBuildingsEarnForemanAndScholar() throws Exception {
        jdbc.sql("update village set b_headquarters = 9 where id = ?")
                .param(attacker.villageId)
                .update();
        buildOrder(attacker, BuildingType.HEADQUARTERS, 10, 60);
        buildOrder(attacker, BuildingType.ACADEMY, 1, 120);
        assertThat(earned(attacker)).isEmpty();

        clock.advance(Duration.ofMinutes(10));
        processor.processDue(clock.instant());

        assertThat(earned(attacker)).containsEntry("FOREMAN", 1).containsEntry("SCHOLAR", 1);
    }

    @Test
    void aSmallerHeadquartersLevelEarnsNoForeman() throws Exception {
        jdbc.sql("update village set b_headquarters = 3 where id = ?")
                .param(attacker.villageId)
                .update();
        buildOrder(attacker, BuildingType.HEADQUARTERS, 4, 60);

        clock.advance(Duration.ofMinutes(10));
        processor.processDue(clock.instant());

        assertThat(earned(attacker)).doesNotContainKey("FOREMAN");
    }

    @Test
    void finishedUnitsEarnTheArmyAchievementsAndScoutsAndNoblesDoNotCount() throws Exception {
        Instant start = clock.instant();
        recruitOrder(attacker, UnitType.SPEARMAN, 150, start);
        recruitOrder(attacker, UnitType.SCOUT, 30, start);
        recruitOrder(attacker, UnitType.NOBLE, 1, start);
        recruitOrder(attacker, UnitType.RAM, 60, start);

        clock.advance(Duration.ofMinutes(10));
        attacker.village().andExpect(status().isOk()); // the catch-up finishes the orders

        // 150 spearmen and 60 rams count, 30 scouts and the noble don't
        assertThat(counter(attacker, "STANDING_ARMY")).isEqualTo(210);
        assertThat(earned(attacker))
                .containsEntry("STANDING_ARMY", 1)
                .containsEntry("SIEGECRAFT", 1)
                .containsEntry("NOBLE_BLOOD", 1);
    }

    @Test
    void theLevelsOfPointsVillagesAndDaysAreReadFromTheGame() throws Exception {
        jdbc.sql("update player set points = 6000, village_count = 3 where id = ?")
                .param(attacker.playerId)
                .update();
        jdbc.sql("update player set created_at = ? where id = ?")
                .params(
                        Timestamp.from(clock.instant().minus(Duration.ofDays(8))),
                        attacker.playerId)
                .update();

        String list = body(attacker.browser.get("/api/achievements").andExpect(status().isOk()));

        assertThat(earned(attacker))
                .containsEntry("GROWTH", 2)
                .containsEntry("LANDLORD", 1)
                .containsEntry("VETERAN", 1);
        assertThat(JsonPath.<List<Integer>>read(list, "$.achievements[?(@.key=='GROWTH')].level"))
                .containsExactly(2);
        assertThat(
                        JsonPath.<List<Integer>>read(
                                list, "$.achievements[?(@.key=='VETERAN')].progress"))
                .containsExactly(8);
        // once earned, a level stays when the value drops
        jdbc.sql("update player set points = 10, village_count = 1 where id = ?")
                .param(attacker.playerId)
                .update();
        attacker.browser.get("/api/achievements").andExpect(status().isOk());
        assertThat(earned(attacker)).containsEntry("GROWTH", 2).containsEntry("LANDLORD", 1);
    }

    @Test
    void aWorldThatRanBeforeV11GrantsTownHallAcademyAndPioneerFromTheCurrentState()
            throws Exception {
        // the buildings are there, but no event of them is left to hook into
        jdbc.sql("update village set b_headquarters = 10, b_academy = 1 where id = ?")
                .param(attacker.villageId)
                .update();
        jdbc.sql("update village set b_headquarters = 9 where id = ?")
                .param(defender.villageId)
                .update();
        // the world opened a day ago: the attacker registered in the pre-game before that, the
        // defender after it (the test world has no opening time of its own)
        Timestamp initializedBefore =
                jdbc.sql("select initialized_at from world_settings")
                        .query((rs, row) -> rs.getTimestamp(1))
                        .list()
                        .get(0); // null in the test world
        Instant opened = clock.instant().minus(Duration.ofDays(1));
        jdbc.sql("update world_settings set initialized_at = ?")
                .param(Timestamp.from(opened))
                .update();
        try {
            jdbc.sql("update player set created_at = ? where id = ?")
                    .params(Timestamp.from(opened.minus(Duration.ofHours(5))), attacker.playerId)
                    .update();
            jdbc.sql("update player set created_at = ? where id = ?")
                    .params(Timestamp.from(opened.plusSeconds(1)), defender.playerId)
                    .update();
            assertThat(earned(attacker)).isEmpty();

            attacker.browser.get("/api/achievements").andExpect(status().isOk());
            defender.browser.get("/api/achievements").andExpect(status().isOk());

            assertThat(earned(attacker))
                    .containsEntry("FOREMAN", 1)
                    .containsEntry("SCHOLAR", 1)
                    .containsEntry("PIONEER", 1);
            assertThat(earned(defender)).isEmpty();

            // a second look changes nothing, and a revoked one is not granted again
            attacker.browser.get("/api/achievements").andExpect(status().isOk());
            assertThat(levelRows(attacker, "FOREMAN")).isEqualTo(1);
            new Host(mockMvc)
                    .post(
                            "/api/admin/players/"
                                    + attacker.playerId
                                    + "/achievements/PIONEER/revoke",
                            "{}")
                    .andExpect(status().isOk());
            attacker.browser.get("/api/achievements").andExpect(status().isOk());
            assertThat(earned(attacker)).containsOnlyKeys("FOREMAN", "SCHOLAR");
        } finally {
            jdbc.sql("update world_settings set initialized_at = ?")
                    .param(initializedBefore)
                    .update();
        }
    }

    // ---- market, chat, guilds ----

    @Test
    void anAcceptedOfferEarnsFirstDealAndCountsWhatEachSideGives() throws Exception {
        for (TestPlayer player : List.of(attacker, defender)) {
            jdbc.sql(
                            "update village set b_market = 5, b_warehouse = 10, wood = 5000,"
                                    + " clay = 5000, iron = 5000, resources_updated_at = ?"
                                    + " where id = ?")
                    .params(Timestamp.from(clock.instant()), player.villageId)
                    .update();
        }
        long offerId =
                number(
                                body(
                                        attacker.browser
                                                .post(
                                                        attacker.villagePath() + "/offers",
                                                        "{\"offerResource\":\"WOOD\",\"offerAmount\":1000,"
                                                            + "\"wantResource\":\"CLAY\",\"wantAmount\":500,"
                                                            + "\"count\":2}")
                                                .andExpect(status().isCreated())),
                                "$.id")
                        .longValue();
        assertThat(earned(attacker)).isEmpty(); // an open offer is not a deal

        defender.browser
                .post(
                        "/api/offers/" + offerId + "/accept",
                        "{\"villageId\":%d,\"count\":1}".formatted(defender.villageId))
                .andExpect(status().isCreated());

        assertThat(earned(attacker)).containsEntry("FIRST_DEAL", 1);
        assertThat(earned(defender)).containsEntry("FIRST_DEAL", 1);
        // the offer's owner gave 1000 wood, the acceptor 500 clay
        assertThat(counter(attacker, "MERCHANT_PRINCE")).isEqualTo(1000);
        assertThat(counter(defender, "MERCHANT_PRINCE")).isEqualTo(500);
    }

    @Test
    void plainTransportsCountForNothing() throws Exception {
        jdbc.sql(
                        "update village set b_market = 5, b_warehouse = 10, wood = 5000,"
                                + " resources_updated_at = ? where id = ?")
                .params(Timestamp.from(clock.instant()), attacker.villageId)
                .update();

        attacker.browser
                .post(
                        attacker.villagePath() + "/transports",
                        "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":1000,\"clay\":0,\"iron\":0}"
                                .formatted(defenderAt.x(), defenderAt.y()))
                .andExpect(status().isCreated());

        assertThat(earned(attacker)).isEmpty();
        assertThat(counter(attacker, "MERCHANT_PRINCE")).isZero();
    }

    @Test
    void theFirstWorldChatMessageEarnsFirstWords() throws Exception {
        chatService.send(attacker.playerId, "Hallo");
        assertThat(earned(attacker)).containsEntry("FIRST_WORDS", 1);
        assertThat(earned(defender)).isEmpty();
    }

    @Test
    void foundingAndJoiningAGuildEarnTheGuildAchievements() throws Exception {
        jdbc.sql("update world_settings set guilds_enabled = true").update();
        worldService.reload();
        guildService.setInviteInterval(Duration.ZERO);
        String suffix = Long.toString(System.nanoTime(), 36);

        attacker.browser
                .post(
                        "/api/guilds",
                        "{\"name\":\"Wolves "
                                + suffix
                                + "\",\"tag\":\""
                                + suffix.substring(suffix.length() - 4)
                                + "\"}")
                .andExpect(status().isCreated());
        assertThat(earned(attacker))
                .containsEntry("GUILD_FOUNDER", 1)
                .containsEntry("GUILD_MEMBER", 1);

        String name =
                jdbc.sql("select name from player where id = ?")
                        .param(defender.playerId)
                        .query(String.class)
                        .single();
        attacker.browser
                .post("/api/guilds/mine/invitations", "{\"playerName\":\"" + name + "\"}")
                .andExpect(status().isCreated());
        long invitation =
                number(body(defender.browser.get("/api/guilds/mine")), "$.invitations[0].id")
                        .longValue();
        assertThat(earned(defender)).isEmpty();
        defender.browser
                .post("/api/guilds/invitations/" + invitation + "/accept", "")
                .andExpect(status().isOk());
        assertThat(earned(defender))
                .containsEntry("GUILD_MEMBER", 1)
                .doesNotContainKey("GUILD_FOUNDER");
    }

    // ---- granting rules ----

    @Test
    void parallelEventsCountExactlyAndTellTheLevelOnce() throws Exception {
        int before = sent.toPlayer(attacker.playerId).size();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    for (int i = 0; i < 25; i++) {
                                        tx.executeWithoutResult(
                                                status ->
                                                        achievements.count(
                                                                players.findById(attacker.playerId)
                                                                        .orElseThrow(),
                                                                Achievement.STANDING_ARMY,
                                                                1));
                                    }
                                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(counter(attacker, "STANDING_ARMY")).isEqualTo(200);
        assertThat(earned(attacker)).isEqualTo(Map.of("STANDING_ARMY", 1));
        assertThat(
                        sent
                                .toPlayer(attacker.playerId)
                                .subList(before, sent.toPlayer(attacker.playerId).size())
                                .stream()
                                .filter(json -> json.contains("ACHIEVEMENT_EARNED"))
                                .count())
                .isEqualTo(1);
    }

    @Test
    void aCounterJumpingOverSeveralLevelsGrantsEachOfThem() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                status ->
                        achievements.count(
                                players.findById(attacker.playerId).orElseThrow(),
                                Achievement.PLUNDERER,
                                250_000));

        assertThat(earned(attacker)).isEqualTo(Map.of("PLUNDERER", 2));
        assertThat(levelRows(attacker, "PLUNDERER")).isEqualTo(2);
    }

    @Test
    void aRolledBackEventGrantsNothing() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(
                    status -> {
                        achievements.grant(
                                players.findById(attacker.playerId).orElseThrow(),
                                Achievement.FIRST_WORDS);
                        throw new IllegalStateException("the event failed");
                    });
        } catch (IllegalStateException expected) {
            // the transaction rolled back
        }
        assertThat(earned(attacker)).isEmpty();
    }

    // ---- the host ----

    @Test
    void theHostRevokesAnAchievementForGoodAndItIsAudited() throws Exception {
        Host host = new Host(mockMvc);
        arriveAt(attackWithAxemen(20));
        assertThat(earned(attacker)).containsEntry("FIRST_BLOOD", 1);
        String path = "/api/admin/players/" + attacker.playerId + "/achievements/";

        // only the host or an admin
        attacker.browser.post(path + "FIRST_BLOOD/revoke", "{}").andExpect(status().isForbidden());
        host.post(path + "NO_SUCH_THING/revoke", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACHIEVEMENT_NOT_FOUND"));
        host.post("/api/admin/players/999999999/achievements/FIRST_BLOOD/revoke", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));

        host.post(path + "first_blood/revoke", "{\"reason\":\"alt account\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievement").value("FIRST_BLOOD"))
                .andExpect(jsonPath("$.changed").value(true));
        host.post(path + "FIRST_BLOOD/revoke", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));
        assertThat(earned(attacker)).doesNotContainKey("FIRST_BLOOD");
        assertThat(
                        jdbc.sql(
                                        "select count(*) from admin_action where action ="
                                                + " 'REVOKE_ACHIEVEMENT' and target_id = ?")
                                .param(attacker.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1L);

        // he can't earn it again, and what he did meanwhile is not lost for the others
        clock.advance(Duration.ofDays(2));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        arriveAt(attackWithAxemen(20));
        assertThat(earned(attacker)).doesNotContainKey("FIRST_BLOOD");
        assertThat(counter(attacker, "VICTOR")).isEqualTo(2);
    }

    @Test
    void revokingALevelledAchievementRemovesItsCounterAndStopsCounting() throws Exception {
        Host host = new Host(mockMvc);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(
                status ->
                        achievements.count(
                                players.findById(attacker.playerId).orElseThrow(),
                                Achievement.VICTOR,
                                260));
        assertThat(earned(attacker)).isEqualTo(Map.of("VICTOR", 2));

        host.post("/api/admin/players/" + attacker.playerId + "/achievements/VICTOR/revoke", "{}")
                .andExpect(status().isOk());
        assertThat(earned(attacker)).isEmpty();
        assertThat(counter(attacker, "VICTOR")).isZero();

        tx.executeWithoutResult(
                status ->
                        achievements.count(
                                players.findById(attacker.playerId).orElseThrow(),
                                Achievement.VICTOR,
                                500));
        assertThat(earned(attacker)).isEmpty();
        assertThat(counter(attacker, "VICTOR")).isZero();
    }

    // ---- helpers ----

    /** Two wins against the defender's village, a day apart, or one: {@code count} of them. */
    private void wins(int axemen, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            setUnits(attacker, Map.of(UnitType.AXEMAN, axemen));
            arriveAt(attackWithAxemen(axemen));
            clock.advance(Duration.ofHours(25));
        }
    }

    private Instant attackWithAxemen(int count) throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, count));
        return attack("{\"AXEMAN\":" + count + "}");
    }

    private Instant attack(String unitsJson) throws Exception {
        String json =
                "{\"kind\":\"ATTACK\",\"target\":{\"x\":"
                        + defenderAt.x()
                        + ",\"y\":"
                        + defenderAt.y()
                        + "},\"units\":"
                        + unitsJson
                        + "}";
        String sentJson =
                body(
                        attacker.browser
                                .post(attacker.villagePath() + "/commands", json)
                                .andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sentJson, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    /** The highest level of every achievement the player has. */
    private Map<String, Integer> earned(TestPlayer player) {
        Map<String, Integer> found = new TreeMap<>();
        jdbc.sql(
                        "select achievement, max(level) as level from player_achievement"
                                + " where player_id = ? group by achievement")
                .param(player.playerId)
                .query((rs, row) -> found.put(rs.getString("achievement"), rs.getInt("level")))
                .list();
        return found;
    }

    private int levelRows(TestPlayer player, String key) {
        return jdbc.sql(
                        "select count(*) from player_achievement"
                                + " where player_id = ? and achievement = ?")
                .params(player.playerId, key)
                .query(Integer.class)
                .single();
    }

    private long counter(TestPlayer player, String key) {
        return jdbc.sql(
                        "select coalesce(max(value), 0) from achievement_counter"
                                + " where player_id = ? and achievement = ?")
                .params(player.playerId, key)
                .query(Long.class)
                .single();
    }

    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set u_"
                                    + type.name().toLowerCase(Locale.ROOT)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    private void setStock(TestPlayer player, int wood, int clay, int iron) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(wood, clay, iron, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    /** A queued build order whose event finishes it after {@code seconds}. */
    private void buildOrder(
            TestPlayer player, BuildingType building, int targetLevel, int seconds) {
        Instant start = clock.instant();
        long id =
                jdbc.sql(
                                """
                                insert into build_order (village_id, building, target_level,
                                    duration_seconds, start_at, end_at, cost_wood, cost_clay,
                                    cost_iron, cost_pop, created_at)
                                values (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?) returning id
                                """)
                        .params(
                                player.villageId,
                                building.name(),
                                targetLevel,
                                seconds,
                                Timestamp.from(start),
                                Timestamp.from(start.plusSeconds(seconds)),
                                Timestamp.from(start))
                        .query(Long.class)
                        .single();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                scheduler.schedule(
                                        GameEventKind.BUILD_COMPLETE,
                                        id,
                                        start.plusSeconds(seconds)));
    }

    /** A recruit order that makes one unit per second, free of cost. */
    private void recruitOrder(TestPlayer player, UnitType unit, int count, Instant startAt) {
        jdbc.sql(
                        """
                        insert into recruit_order (village_id, building, unit, count,
                            completed_count, seconds_per_unit, start_at, end_at, unit_cost_wood,
                            unit_cost_clay, unit_cost_iron, unit_pop, created_at)
                        values (?, ?, ?, ?, 0, 1, ?, ?, 0, 0, 0, 1, ?)
                        """)
                .params(
                        player.villageId,
                        gameData.unit(unit).building().name(),
                        unit.name(),
                        count,
                        Timestamp.from(startAt),
                        Timestamp.from(startAt.plusSeconds(count)),
                        Timestamp.from(startAt))
                .update();
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
