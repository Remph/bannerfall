package de.bannerfall.combat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
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
 * Attacks that arrive and fight (M8b, 02-game-rules §7–§9, §14) between registered players. Luck is
 * switched off ({@code luckRange = 0}), so every battle here can be calculated by hand; units,
 * levels and resources are set with SQL. The clock is moved by hand and the event worker is called
 * directly. Nothing touches the attacked village between sending and arrival unless a test says so,
 * so the battle's own catch-up is the first one.
 */
@IntegrationTest
class BattleFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired GameData gameData;

    private double luckRangeBefore;
    private TestPlayer attacker;
    private TestPlayer defender;
    private Coordinates defenderAt;

    @BeforeEach
    void setUp() throws Exception {
        // processDue handles everything that is due: no event of another test may be left over
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        setLuckRange(0);
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        defenderAt = coordinates(defender);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        setLuckRange(luckRangeBefore);
        setLevelsEnabled(false);
    }

    // ---- loot ----

    @Test
    void anUndefendedVillageIsLootedAndTheLootComesHome() throws Exception {
        setUnits(attacker, Map.of(UnitType.SPEARMAN, 50));
        setLevel(defender, BuildingType.HIDING_PLACE, 1); // hides 150 of each
        Instant sentAt = clock.instant();
        Instant arrival = attack("{\"SPEARMAN\":40}"); // carry 40 × 25 = 1000
        Instant backHome = arrival.plus(Duration.between(sentAt, arrival));
        // anchored at the arrival / the return: no production in between
        setResources(defender, 300, 1000, 1000, arrival);
        setResources(attacker, 100, 100, 100, backHome);

        arriveAt(arrival);

        // A = 400, D = 20: (20/400)^1.5 × 40 rounds to 0 losses
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.type")).isEqualTo("BATTLE");
        assertThat(instant(report, "$.happenedAt")).isEqualTo(arrival);
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat(JsonPath.<Double>read(report, "$.payload.luck")).isZero();
        assertThat(units(report, "$.payload.attackerUnits")).isEqualTo(Map.of("SPEARMAN", 40));
        assertThat(units(report, "$.payload.attackerLosses")).isEmpty();
        assertThat(units(report, "$.payload.defenderSide.units")).isEmpty();
        assertThat(JsonPath.<List<?>>read(report, "$.payload.defenderSide.stacks")).isEmpty();
        assertThat(units(report, "$.payload.loot"))
                .isEqualTo(Map.of("wood", 150, "clay", 425, "iron", 425)); // 02 §9.3 example
        assertThat((Object) JsonPath.read(report, "$.payload.scouting")).isNull();
        assertThat((Object) JsonPath.read(report, "$.payload.catapultTarget")).isNull();

        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.attackerUnits")).isEqualTo(Map.of("SPEARMAN", 40));
        assertThat(units(defense, "$.payload.loot"))
                .isEqualTo(Map.of("wood", 150, "clay", 425, "iron", 425));
        defender.village()
                .andExpect(jsonPath("$.resources.wood").value(150.0))
                .andExpect(jsonPath("$.resources.clay").value(575.0))
                .andExpect(jsonPath("$.resources.iron").value(575.0));

        String troops = body(attacker.troops());
        assertThat(JsonPath.<String>read(troops, "$.incoming[0].kind")).isEqualTo("RETURN");
        assertThat(instant(troops, "$.incoming[0].arriveAt")).isEqualTo(backHome);
        arriveAt(backHome);
        attacker.village()
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(50))
                .andExpect(jsonPath("$.resources.wood").value(250.0))
                .andExpect(jsonPath("$.resources.clay").value(525.0))
                .andExpect(jsonPath("$.resources.iron").value(525.0));
    }

    // ---- unit levels (M30) ----

    @Test
    void theLevelsOfTheDefenderHoldABattleThatWouldBeLost() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        // A = 800, D = 50 × 15 + 20 = 770 on level 1 (attacker wins); 50 × 18 + 20 = 920 on level 3
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));

        arriveAt(attack("{\"AXEMAN\":20}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("DEFENDER_WON");
        // the reports name the levels that fought: the defenders' copy per stack
        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.attackerLevels")).isEmpty();
        assertThat(units(defense, "$.payload.defenderSide.stacks[0].levels"))
                .isEqualTo(Map.of("SPEARMAN", 3));
        // the defender wins and loses round(50 × (800 / 920)^1.5) = 41
        defender.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(9));
    }

    @Test
    void aPaladinDefendsWithHisOwnValuesAndHasNoLevels() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 6));
        setUnits(defender, Map.of(UnitType.PALADIN, 2));
        // A = 6 × 40 = 240, D = 2 × 150 + 20 = 320 (M31: no levels, so the base values count)

        arriveAt(attack("{\"AXEMAN\":6}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("DEFENDER_WON");
        // the defender loses round(2 × (240 / 320)^1.5) = 1 of the two
        defender.village().andExpect(jsonPath("$.unitsHome.PALADIN").value(1));
        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.defenderSide.stacks[0].levels")).isEmpty();
        assertThat(units(defense, "$.payload.defenderSide.units")).isEqualTo(Map.of("PALADIN", 2));
        assertThat(units(defense, "$.payload.defenderSide.losses")).isEqualTo(Map.of("PALADIN", 1));
    }

    @Test
    void theLevelsOfTheAttackerWinItBack() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));
        research(attacker, UnitType.AXEMAN, 3, clock.instant().minusSeconds(60));

        arriveAt(attack("{\"AXEMAN\":20}"));

        // A = 20 × 48 = 960 against D = 920
        assertThat(units(attacker.newestReport("ATTACK"), "$.payload.attackerLevels"))
                .isEqualTo(Map.of("AXEMAN", 3));
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        defender.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").doesNotExist());
    }

    @Test
    void aResearchThatFinishesAfterTheArrivalDoesNotCount() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        Instant arrival = attack("{\"AXEMAN\":20}");
        research(defender, UnitType.SPEARMAN, 3, arrival.plusSeconds(1));

        arriveAt(arrival);

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
    }

    @Test
    void aResearchThatFinishesOnTheArrivalCounts() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        Instant arrival = attack("{\"AXEMAN\":20}");
        research(defender, UnitType.SPEARMAN, 3, arrival);

        arriveAt(arrival);

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("DEFENDER_WON");
    }

    @Test
    void withLevelsSwitchedOffTheResearchedLevelsAreIgnored() throws Exception {
        setLevelsEnabled(false);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));

        arriveAt(attack("{\"AXEMAN\":20}"));

        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");
    }

    @Test
    void aSupportFightsWithTheLevelsOfItsOwnerNotOfTheVillagesOwner() throws Exception {
        setLevelsEnabled(true);
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of());
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 50));
        // the village's owner has level 3, the supporter hasn't: D = 770, the attacker wins
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));
        Instant arrival = attack("{\"AXEMAN\":20}");
        arriveAt(arrival);
        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("ATTACKER_WON");

        // now the supporter has it: D = 920
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 50));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        research(supporter, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));
        arriveAt(attack("{\"AXEMAN\":20}"));
        assertThat(JsonPath.<String>read(attacker.newestReport("ATTACK"), "$.payload.outcome"))
                .isEqualTo("DEFENDER_WON");
    }

    // ---- who sees what ----

    @Test
    void whenTheDefenderWinsTheAttackerLearnsNothingAboutHim() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SWORDSMAN, 100));
        int populationBefore = populationUsed(attacker);

        arriveAt(attack("{\"AXEMAN\":20}"));

        // 02 §8 vector: A = 800, D = 5020, the defender loses round(100 × 0.06362) = 6
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("DEFENDER_WON");
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 20));
        assertThat((Object) JsonPath.read(report, "$.payload.defenderSide")).isNull();
        assertThat(units(report, "$.payload.loot"))
                .isEqualTo(Map.of("wood", 0, "clay", 0, "iron", 0));

        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.attackerUnits")).isEqualTo(Map.of("AXEMAN", 20));
        assertThat(units(defense, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 20));
        assertThat(units(defense, "$.payload.defenderSide.units"))
                .isEqualTo(Map.of("SWORDSMAN", 100));
        assertThat(units(defense, "$.payload.defenderSide.losses"))
                .isEqualTo(Map.of("SWORDSMAN", 6));
        assertThat(number(defense, "$.payload.defenderSide.stacks[0].home.id").longValue())
                .isEqualTo(defender.villageId);

        defender.village().andExpect(jsonPath("$.unitsHome.SWORDSMAN").value(94));
        // nobody walks home, and the dead free their population
        attacker.troops()
                .andExpect(jsonPath("$.incoming").isEmpty())
                .andExpect(jsonPath("$.outgoing").isEmpty());
        assertThat(populationUsed(attacker)).isEqualTo(populationBefore - 20);
    }

    @Test
    void supportersLoseTheirShareAndGetTheDefenseReport() throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 50));

        arriveAt(attack("{\"AXEMAN\":20}"));

        // A = 800, D = 100 × 15 + 20 = 1520: each stack loses round(50 × 0.38184) = 19
        defender.village().andExpect(jsonPath("$.unitsHome.SPEARMAN").value(31));
        supporter.troops().andExpect(jsonPath("$.ownUnitsElsewhere[0].units.SPEARMAN").value(31));
        assertThat(supporter.reportKinds()).containsExactly("DEFENSE");
        String defense = supporter.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.defenderSide.units"))
                .isEqualTo(Map.of("SPEARMAN", 100));
        assertThat(units(defense, "$.payload.defenderSide.losses"))
                .isEqualTo(Map.of("SPEARMAN", 38));
        // the village's own units first, then the support
        assertThat(JsonPath.<List<Number>>read(defense, "$.payload.defenderSide.stacks[*].home.id"))
                .extracting(Number::longValue)
                .containsExactly(defender.villageId, supporter.villageId);
        assertThat(units(defense, "$.payload.defenderSide.stacks[1].losses"))
                .isEqualTo(Map.of("SPEARMAN", 19));
        // owner and supporter see the same battle
        assertThat((Object) JsonPath.read(defender.newestReport("DEFENSE"), "$.payload"))
                .isEqualTo(JsonPath.read(defense, "$.payload"));
    }

    @Test
    void aSupportThatLosesAllItsUnitsIsGoneAndTheSurvivingAttackerSeesOnlyTheTotals()
            throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 50));

        arriveAt(attack("{\"AXEMAN\":100}"));

        // A = 4000, D = 1520: the attacker loses round(100 × 0.23424) = 23
        defender.troops().andExpect(jsonPath("$.supportsHere").isEmpty());
        supporter.troops().andExpect(jsonPath("$.ownUnitsElsewhere").isEmpty());
        String report = attacker.newestReport("ATTACK");
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 23));
        assertThat(units(report, "$.payload.defenderSide.units"))
                .isEqualTo(Map.of("SPEARMAN", 100));
        assertThat(units(report, "$.payload.defenderSide.losses"))
                .isEqualTo(Map.of("SPEARMAN", 100));
        // who supports the village is for the defenders only
        assertThat(JsonPath.<List<?>>read(report, "$.payload.defenderSide.stacks")).isEmpty();
        assertThat(supporter.reportKinds()).containsExactly("DEFENSE");
        String defense = supporter.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.defenderSide.stacks[1].losses"))
                .isEqualTo(Map.of("SPEARMAN", 50));
        attacker.troops().andExpect(jsonPath("$.incoming[0].units.AXEMAN").value(77));
    }

    @Test
    void upgradedScoutsShowTheLevelsOfTheDefendersInTheReport() throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SCOUT, 4, UnitType.SPEARMAN, 30));
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 5, UnitType.AXEMAN, 5));
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));
        research(supporter, UnitType.SPEARMAN, 2, clock.instant().minusSeconds(60));
        research(supporter, UnitType.AXEMAN, 2, clock.instant().minusSeconds(60));
        research(attacker, UnitType.SCOUT, 2, clock.instant().minusSeconds(60));

        arriveAt(attack("{\"SCOUT\":10}"));

        // level 2 scouts: the attacker learns the highest level per unit type among the defenders
        // (his copy has no stacks); the scouts of the defender have none
        String report = attacker.newestReport("SCOUT");
        assertThat(units(report, "$.payload.defenderSide.levels"))
                .isEqualTo(Map.of("SPEARMAN", 3, "AXEMAN", 2));
        // the attacker's own levels show as before
        assertThat(units(report, "$.payload.attackerLevels")).isEqualTo(Map.of("SCOUT", 2));
        // the defenders' copy names the levels per stack and carries no summary
        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.defenderSide.levels")).isEmpty();
        assertThat(units(defense, "$.payload.defenderSide.stacks[0].levels"))
                .isEqualTo(Map.of("SPEARMAN", 3));
    }

    @Test
    void scoutsOfLevelOneOrAFailedScoutingShowNoLevels() throws Exception {
        setLevelsEnabled(true);
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SCOUT, 4, UnitType.SPEARMAN, 30));
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));

        // level 1 scouts
        arriveAt(attack("{\"SCOUT\":10}"));
        assertThat(units(attacker.newestReport("SCOUT"), "$.payload.defenderSide.units"))
                .containsEntry("SPEARMAN", 30);
        assertThat(units(attacker.newestReport("SCOUT"), "$.payload.defenderSide.levels"))
                .isEmpty();

        // level 2 scouts that are outnumbered die: the report shows nothing about the defenders
        research(attacker, UnitType.SCOUT, 2, clock.instant().minusSeconds(60));
        setUnits(attacker, Map.of(UnitType.SCOUT, 3));
        Instant arrival = attack("{\"SCOUT\":3}");
        arriveAt(arrival);
        String failed = attacker.newestReport("SCOUT");
        assertThat((Object) JsonPath.read(failed, "$.payload.scouting")).isNull();
        assertThat((Object) JsonPath.read(failed, "$.payload.defenderSide")).isNull();
    }

    @Test
    void withLevelsSwitchedOffNoLevelsAreScouted() throws Exception {
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SCOUT, 4, UnitType.SPEARMAN, 30));
        research(defender, UnitType.SPEARMAN, 3, clock.instant().minusSeconds(60));
        research(attacker, UnitType.SCOUT, 2, clock.instant().minusSeconds(60));

        arriveAt(attack("{\"SCOUT\":10}"));

        assertThat(units(attacker.newestReport("SCOUT"), "$.payload.defenderSide.levels"))
                .isEmpty();
    }

    @Test
    void onlyScoutsRevealResourcesBuildingsAndUnitsWithoutABattle() throws Exception {
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SCOUT, 4, UnitType.SPEARMAN, 30));
        addSupport(supporter, defender, Map.of(UnitType.SPEARMAN, 5));
        setLevel(defender, BuildingType.WALL, 3);
        Instant sentAt = clock.instant();
        Instant arrival = attack("{\"SCOUT\":10}");
        setResources(defender, 321, 432, 543, arrival);

        arriveAt(arrival);

        // 10 > 4 scouts: the attacker loses round(10 × 0.4^1.5) = 3
        String report = attacker.newestReport("SCOUT");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("SCOUTS_ONLY");
        assertThat((Object) JsonPath.read(report, "$.payload.luck")).isNull();
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("SCOUT", 3));
        assertThat(JsonPath.<Boolean>read(report, "$.payload.scouting.succeeded")).isTrue();
        assertThat(units(report, "$.payload.scouting.resources"))
                .isEqualTo(Map.of("wood", 321, "clay", 432, "iron", 543));
        assertThat(number(report, "$.payload.scouting.buildings.WALL").intValue()).isEqualTo(3);
        assertThat(number(report, "$.payload.scouting.buildings.HEADQUARTERS").intValue())
                .isEqualTo(1);
        assertThat(number(report, "$.payload.scouting.buildings.BARRACKS").intValue()).isZero();
        assertThat(units(report, "$.payload.defenderSide.units"))
                .isEqualTo(Map.of("SCOUT", 4, "SPEARMAN", 35));
        assertThat(units(report, "$.payload.defenderSide.losses")).isEmpty();
        assertThat(JsonPath.<List<?>>read(report, "$.payload.defenderSide.stacks")).isEmpty();
        assertThat(number(report, "$.payload.defenderSide.wallAfter").intValue()).isEqualTo(3);

        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<String>read(defense, "$.payload.outcome")).isEqualTo("SCOUTS_ONLY");
        assertThat(JsonPath.<Boolean>read(defense, "$.payload.scouting.succeeded")).isTrue();
        assertThat((Object) JsonPath.read(defense, "$.payload.scouting.resources")).isNull();
        assertThat((Object) JsonPath.read(defense, "$.payload.scouting.buildings")).isNull();
        assertThat(units(defense, "$.payload.attackerLosses")).isEqualTo(Map.of("SCOUT", 3));
        // no battle: nothing happened to the support, its owner isn't bothered
        assertThat(supporter.reportKinds()).isEmpty();

        defender.village()
                .andExpect(jsonPath("$.unitsHome.SCOUT").value(4))
                .andExpect(jsonPath("$.unitsHome.SPEARMAN").value(30))
                .andExpect(jsonPath("$.resources.wood").value(321.0));
        Instant backHome = arrival.plus(Duration.between(sentAt, arrival));
        arriveAt(backHome);
        attacker.village().andExpect(jsonPath("$.unitsHome.SCOUT").value(7));
    }

    @Test
    void onlyASuccessfulScoutSeesTheBannerFlyingInTheTarget() throws Exception {
        // a tier III Treasury banner in a level 2 hall: the report shows the banner as it is
        setLevel(defender, BuildingType.BANNER_HALL, 2);
        jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, 'TREASURY', 3, ?, now(), 'GRANTED', now())
                        """)
                .params(defender.playerId, defender.villageId)
                .update();
        setUnits(attacker, Map.of(UnitType.SCOUT, 10));
        setUnits(defender, Map.of(UnitType.SCOUT, 4));

        arriveAt(attack("{\"SCOUT\":10}"));

        String report = attacker.newestReport("SCOUT");
        assertThat(JsonPath.<String>read(report, "$.payload.scouting.banner.type"))
                .isEqualTo("TREASURY");
        assertThat(number(report, "$.payload.scouting.banner.tier").intValue()).isEqualTo(3);
        // the defender's copy shows no scouting details
        assertThat(
                        (Object)
                                JsonPath.read(
                                        defender.newestReport("DEFENSE"),
                                        "$.payload.scouting.banner"))
                .isNull();
    }

    @Test
    void aFailedScoutLearnsNothingAboutTheBanner() throws Exception {
        jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, 'WAR', 1, ?, now(), 'GRANTED', now())
                        """)
                .params(defender.playerId, defender.villageId)
                .update();
        setUnits(attacker, Map.of(UnitType.SCOUT, 4));
        setUnits(defender, Map.of(UnitType.SCOUT, 10));

        arriveAt(attack("{\"SCOUT\":4}"));

        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Boolean>read(defense, "$.payload.scouting.succeeded")).isFalse();
        assertThat((Object) JsonPath.read(defense, "$.payload.scouting.banner")).isNull();
        // the attacker's scouts did not get through: his report has no scouting part at all
        String report = attacker.newestReport("SCOUT");
        assertThat((Object) JsonPath.read(report, "$.payload.scouting")).isNull();
    }

    @Test
    void anAttackerWhoseOwnSupportStandsInTheTargetGetsBothReportsOnce() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));
        addSupport(attacker, defender, Map.of(UnitType.SPEARMAN, 50));

        arriveAt(attack("{\"AXEMAN\":100}"));

        // A = 4000, D = 1520 as above: his support fights against his own attack and dies
        assertThat(attacker.reportKinds()).containsExactlyInAnyOrder("ATTACK", "DEFENSE");
        assertThat(defender.reportKinds()).containsExactly("DEFENSE");
        // as a supporter he sees the stacks, as the attacker only the totals
        String defense = attacker.newestReport("DEFENSE");
        assertThat(JsonPath.<List<Number>>read(defense, "$.payload.defenderSide.stacks[*].home.id"))
                .extracting(Number::longValue)
                .containsExactly(defender.villageId, attacker.villageId);
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<List<?>>read(report, "$.payload.defenderSide.stacks")).isEmpty();
    }

    // ---- siege ----

    @Test
    void ramsLowerTheWall() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 20, UnitType.RAM, 10));
        setLevel(defender, BuildingType.WALL, 5); // without new points: the battle sets them

        arriveAt(attack("{\"AXEMAN\":20,\"RAM\":10}"));

        // Wb = 5 (5 rams can't take level 5), D = 270 × 1.25 = 337.5, A = 820, ratio 0.26405:
        // 5 axemen and 3 rams die; 7 surviving rams take the wall from 5 to 4
        String report = attacker.newestReport("ATTACK");
        assertThat(units(report, "$.payload.attackerLosses"))
                .isEqualTo(Map.of("AXEMAN", 5, "RAM", 3));
        assertThat(number(report, "$.payload.defenderSide.wallBefore").intValue()).isEqualTo(5);
        assertThat(number(report, "$.payload.defenderSide.wallAfter").intValue()).isEqualTo(4);
        assertThat((Object) JsonPath.read(report, "$.payload.defenderSide.catapultLevelBefore"))
                .isNull();
        String village = body(defender.village());
        assertThat(number(village, "$.buildings.WALL").intValue()).isEqualTo(4);
        assertThat(number(village, "$.points").intValue()).isEqualTo(expectedPoints(village));
        attacker.troops()
                .andExpect(jsonPath("$.incoming[0].units.AXEMAN").value(15))
                .andExpect(jsonPath("$.incoming[0].units.RAM").value(7));
    }

    @Test
    void catapultsDamageTheChosenBuildingAndItsQueuedOrdersAimLower() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50, UnitType.CATAPULT, 30));
        setLevel(defender, BuildingType.HEADQUARTERS, 3);
        setLevel(defender, BuildingType.BARRACKS, 5);
        Instant now = clock.instant();
        Instant arrival = attack("{\"AXEMAN\":50,\"CATAPULT\":30}", "BARRACKS");
        // still queued at the arrival: the Town Hall first, then the barracks
        int duration = (int) Duration.between(now, arrival).toSeconds() + 3600;
        long hqOrder = insertBuildOrder(defender, BuildingType.HEADQUARTERS, 4, now, duration);
        long barracksOrder =
                insertBuildOrder(
                        defender, BuildingType.BARRACKS, 6, now.plusSeconds(duration), duration);

        arriveAt(arrival);

        // A = 5000, D = 20: no losses; 30 catapults take the barracks 5 → 4 (14) → 3 (12)
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.catapultTarget")).isEqualTo("BARRACKS");
        assertThat(number(report, "$.payload.defenderSide.catapultLevelBefore").intValue())
                .isEqualTo(5);
        assertThat(number(report, "$.payload.defenderSide.catapultLevelAfter").intValue())
                .isEqualTo(3);
        String village = body(defender.village());
        assertThat(number(village, "$.buildings.BARRACKS").intValue()).isEqualTo(3);
        assertThat(number(village, "$.points").intValue()).isEqualTo(expectedPoints(village));
        // the queued barracks upgrade stays and now leads to level 4
        assertThat(JsonPath.<List<Integer>>read(village, "$.buildQueue[*].targetLevel"))
                .containsExactly(4, 4);

        // so cancelling the order in front of it keeps it (it is still the next level)
        defender.cancel(hqOrder).andExpect(status().isNoContent());
        String after = body(defender.village());
        assertThat(JsonPath.<List<Number>>read(after, "$.buildQueue[*].id"))
                .extracting(Number::longValue)
                .containsExactly(barracksOrder);
    }

    @Test
    void catapultsAimAtTheTownHallByDefaultAndStopAtItsMinimumLevel() throws Exception {
        setUnits(attacker, Map.of(UnitType.CATAPULT, 60));
        setLevel(defender, BuildingType.HEADQUARTERS, 3);

        arriveAt(attack("{\"CATAPULT\":60}"));

        // 60 catapults could take 3 → 2 (10) → 1 (8) → 0 (6), but the Town Hall stays at 1
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.catapultTarget"))
                .isEqualTo("HEADQUARTERS");
        assertThat(number(report, "$.payload.defenderSide.catapultLevelAfter").intValue())
                .isEqualTo(1);
        defender.village().andExpect(jsonPath("$.buildings.HEADQUARTERS").value(1));
    }

    // ---- lazy recruitment ----

    @Test
    void unitsRecruitedBeforeTheArrivalDefendAndLaterOnesDont() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 1));
        Instant now = clock.instant();
        Instant arrival = attack("{\"AXEMAN\":1}");
        long travel = Duration.between(now, arrival).toSeconds();
        int secondsPerUnit = (int) (travel / 10); // villages are far apart: ≥ 1 field, ≥ 18 min
        int doneByArrival = (int) (travel / secondsPerUnit);
        insertRecruitOrder(defender, UnitType.SPEARMAN, 30, secondsPerUnit, now);

        arriveAt(arrival);

        String defense = defender.newestReport("DEFENSE");
        assertThat(units(defense, "$.payload.defenderSide.units"))
                .isEqualTo(Map.of("SPEARMAN", doneByArrival));
        assertThat(doneByArrival).isBetween(10, 29);
    }

    // ---- the simulator (M23) ----

    @Test
    void theSimulatorPredictsARealBattleExactly() throws Exception {
        setUnits(
                attacker,
                Map.of(
                        UnitType.AXEMAN,
                        300,
                        UnitType.RAM,
                        12,
                        UnitType.CATAPULT,
                        20,
                        UnitType.SCOUT,
                        6));
        setUnits(
                defender, Map.of(UnitType.SPEARMAN, 90, UnitType.SWORDSMAN, 40, UnitType.SCOUT, 2));
        setLevel(defender, BuildingType.WALL, 4);
        setLevel(defender, BuildingType.BARRACKS, 5);
        String simulation =
                body(
                        attacker.browser
                                .post(
                                        "/api/simulator",
                                        """
                                        {"attacker": {"AXEMAN": 300, "RAM": 12, "CATAPULT": 20, "SCOUT": 6},
                                         "defender": {"SPEARMAN": 90, "SWORDSMAN": 40, "SCOUT": 2},
                                         "wall": 4, "catapultTarget": "BARRACKS", "catapultTargetLevel": 5}
                                        """)
                                .andExpect(status().isOk()));

        arriveAt(attack("{\"AXEMAN\":300,\"RAM\":12,\"CATAPULT\":20,\"SCOUT\":6}", "BARRACKS"));

        // the world has no luck here, so there is one scenario, and it is the battle
        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<List<?>>read(simulation, "$.scenarios")).hasSize(1);
        String scenario = "$.scenarios[0]";
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat(JsonPath.<String>read(simulation, scenario + ".outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat(units(simulation, scenario + ".attackerLosses"))
                .isEqualTo(units(report, "$.payload.attackerLosses"));
        assertThat(units(simulation, scenario + ".defenderLosses"))
                .isEqualTo(units(report, "$.payload.defenderSide.losses"));
        assertThat(number(simulation, scenario + ".wallAfter").intValue())
                .isEqualTo(number(report, "$.payload.defenderSide.wallAfter").intValue());
        assertThat(number(simulation, scenario + ".catapultTargetLevelAfter").intValue())
                .isEqualTo(number(report, "$.payload.defenderSide.catapultLevelAfter").intValue());
        // and the survivors are what walks home
        attacker.troops()
                .andExpect(
                        jsonPath("$.incoming[0].units.AXEMAN")
                                .value(
                                        JsonPath.<Integer>read(
                                                simulation,
                                                scenario + ".attackerSurvivors.AXEMAN")));
    }

    // ---- helpers ----

    /** Sends an attack from the attacker to the defender; returns its arrival. */
    private Instant attack(String unitsJson) throws Exception {
        return attack(unitsJson, null);
    }

    private Instant attack(String unitsJson, String catapultTarget) throws Exception {
        String json =
                "{\"kind\":\"ATTACK\",\"target\":{\"x\":"
                        + defenderAt.x()
                        + ",\"y\":"
                        + defenderAt.y()
                        + "},\"units\":"
                        + unitsJson
                        + (catapultTarget == null
                                ? ""
                                : ",\"catapultTarget\":\"" + catapultTarget + "\"")
                        + "}";
        String sent =
                body(
                        attacker.browser
                                .post(attacker.villagePath() + "/commands", json)
                                .andExpect(status().isCreated()));
        return instant(sent, "$.arriveAt");
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    private void setLevelsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set unit_levels_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    /** A finished (or, with a later {@code endAt}, running) research of a player's unit. */
    private void research(TestPlayer player, UnitType unit, int level, Instant endAt) {
        jdbc.sql(
                        """
                        insert into research_order (player_id, village_id, unit, level, start_at,
                            end_at, cost_wood, cost_clay, cost_iron, created_at)
                        values (?, ?, ?, ?, ?, ?, 0, 0, 0, ?)
                        """)
                .params(
                        player.playerId,
                        player.villageId,
                        unit.name(),
                        level,
                        Timestamp.from(endAt.minusSeconds(100)),
                        Timestamp.from(endAt),
                        Timestamp.from(clock.instant()))
                .update();
    }

    private void setLuckRange(double luckRange) {
        jdbc.sql("update world_settings set luck_range = ? where id = 1").param(luckRange).update();
        worldService.reload();
    }

    /** The home stack has exactly these units. */
    private void setUnits(TestPlayer player, Map<UnitType, Integer> units) {
        for (UnitType type : UnitType.values()) {
            jdbc.sql(
                            "update unit_stack set "
                                    + column("u_", type)
                                    + " = ? where home_village_id = ? and location_village_id = ?")
                    .params(units.getOrDefault(type, 0), player.villageId, player.villageId)
                    .update();
        }
    }

    /** A support stack of {@code from}'s village standing in {@code in}'s village. */
    private void addSupport(TestPlayer from, TestPlayer in, Map<UnitType, Integer> units) {
        long stackId =
                jdbc.sql(
                                "insert into unit_stack (home_village_id, location_village_id)"
                                        + " values (?, ?) returning id")
                        .params(from.villageId, in.villageId)
                        .query(Long.class)
                        .single();
        units.forEach(
                (type, count) ->
                        jdbc.sql(
                                        "update unit_stack set "
                                                + column("u_", type)
                                                + " = ? where id = ?")
                                .params(count, stackId)
                                .update());
    }

    private void setLevel(TestPlayer player, BuildingType building, int level) {
        jdbc.sql("update village set " + column("b_", building) + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setResources(TestPlayer player, int wood, int clay, int iron, Instant anchor) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(wood, clay, iron, Timestamp.from(anchor), player.villageId)
                .update();
    }

    /** A queued order without an event (it never completes here); returns its id. */
    private long insertBuildOrder(
            TestPlayer player,
            BuildingType building,
            int targetLevel,
            Instant startAt,
            int durationSeconds) {
        return jdbc.sql(
                        """
                        insert into build_order (village_id, building, target_level,
                            duration_seconds, start_at, end_at, cost_wood, cost_clay, cost_iron,
                            cost_pop, created_at)
                        values (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?) returning id
                        """)
                .params(
                        player.villageId,
                        building.name(),
                        targetLevel,
                        durationSeconds,
                        Timestamp.from(startAt),
                        Timestamp.from(startAt.plusSeconds(durationSeconds)),
                        Timestamp.from(clock.instant()))
                .query(Long.class)
                .single();
    }

    private void insertRecruitOrder(
            TestPlayer player, UnitType unit, int count, int secondsPerUnit, Instant startAt) {
        jdbc.sql(
                        """
                        insert into recruit_order (village_id, building, unit, count,
                            completed_count, seconds_per_unit, start_at, end_at, unit_cost_wood,
                            unit_cost_clay, unit_cost_iron, unit_pop, created_at)
                        values (?, ?, ?, ?, 0, ?, ?, ?, 0, 0, 0, 1, ?)
                        """)
                .params(
                        player.villageId,
                        gameData.unit(unit).building().name(),
                        unit.name(),
                        count,
                        secondsPerUnit,
                        Timestamp.from(startAt),
                        Timestamp.from(startAt.plusSeconds((long) count * secondsPerUnit)),
                        Timestamp.from(clock.instant()))
                .update();
    }

    /** The column of a building or unit type: {@code b_rally_point}, {@code u_light_cavalry}. */
    private static String column(String prefix, Enum<?> type) {
        return prefix + type.name().toLowerCase(Locale.ROOT);
    }

    /** The points the village's current levels are worth (02-game-rules §4.2). */
    private int expectedPoints(String village) {
        Map<String, Integer> levels = JsonPath.read(village, "$.buildings");
        var byType = new EnumMap<BuildingType, Integer>(BuildingType.class);
        levels.forEach((name, level) -> byType.put(BuildingType.valueOf(name), level));
        return BuildRules.villagePoints(gameData, byType);
    }

    private static int populationUsed(TestPlayer player) throws Exception {
        return number(body(player.village()), "$.population.used").intValue();
    }

    /** A unit map or a resource triple of a JSON body. */
    private static Map<String, Integer> units(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
