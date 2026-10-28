package de.bannerfall.simulator;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/simulator (M23, 02-game-rules §20). The battle numbers are those of {@code Combat} (02
 * §8 vectors); that the answer equals a real battle is tested in {@code BattleFlowTest}.
 */
@IntegrationTest
class SimulatorTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;

    private double luckRangeBefore;
    private TestPlayer player;

    @BeforeEach
    void setUp() throws Exception {
        luckRangeBefore = worldService.settings().luckRange();
        setLuckRange(0.2);
        player = TestPlayer.register(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        setLuckRange(luckRangeBefore);
    }

    @Test
    void showsTheBattleWithTheWorstNoAndTheBestLuck() throws Exception {
        // 02 §8: 20 axemen (A = 800 without luck) against 100 swordsmen (D = 5020)
        String body = simulate("{\"AXEMAN\":20}", "{\"SWORDSMAN\":100}", 0);

        List<Double> lucks = JsonPath.read(body, "$.scenarios[*].luck");
        assertThat(lucks).containsExactly(-0.2, 0.0, 0.2);
        String middle = "$.scenarios[1]";
        assertThat(JsonPath.<String>read(body, middle + ".outcome")).isEqualTo("DEFENDER_WON");
        assertThat(JsonPath.<Double>read(body, middle + ".attack")).isEqualTo(800.0);
        assertThat(JsonPath.<Double>read(body, middle + ".defense")).isEqualTo(5020.0);
        assertThat(units(body, middle + ".attackerLosses")).isEqualTo(Map.of("AXEMAN", 20));
        assertThat(units(body, middle + ".attackerSurvivors")).isEmpty();
        assertThat(units(body, middle + ".defenderLosses")).isEqualTo(Map.of("SWORDSMAN", 6));
        assertThat(units(body, middle + ".defenderSurvivors")).isEqualTo(Map.of("SWORDSMAN", 94));
        // luck moves the attack strength by its share
        assertThat(JsonPath.<Double>read(body, "$.scenarios[0].attack")).isEqualTo(640.0);
        assertThat(JsonPath.<Double>read(body, "$.scenarios[2].attack")).isEqualTo(960.0);
    }

    @Test
    void unitLevelsOnlyCountWhereTheWorldHasThem() throws Exception {
        setLuckRange(0);
        String levels = ",\"attackerLevels\":{\"AXEMAN\":3},\"defenderLevels\":{\"SPEARMAN\":2}";

        // levels off: ignored, A = 800 and D = 770 as without them
        String off = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, levels));
        assertThat(JsonPath.<Double>read(off, "$.scenarios[0].attack")).isEqualTo(800.0);
        assertThat(JsonPath.<Double>read(off, "$.scenarios[0].defense")).isEqualTo(770.0);

        setLevelsEnabled(true);
        try {
            // axemen on level 3 attack with 48, spearmen on level 2 defend with 17
            String on = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, levels));
            assertThat(JsonPath.<Double>read(on, "$.scenarios[0].attack")).isEqualTo(960.0);
            assertThat(JsonPath.<Double>read(on, "$.scenarios[0].defense")).isEqualTo(870.0);
            // no levels given: level 1
            String none = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, ""));
            assertThat(JsonPath.<Double>read(none, "$.scenarios[0].attack")).isEqualTo(800.0);
        } finally {
            setLevelsEnabled(false);
        }
    }

    @Test
    void thePaladinsLevelCountsAlsoWithUnitLevelsOff() throws Exception {
        setLuckRange(0);
        // Hall training (1.4): level 3 is attack 132 and defence 165, the spearmen's level is
        // ignored with unit levels off; 20 is the village's base defence
        String levels =
                ",\"attackerLevels\":{\"PALADIN\":3},"
                        + "\"defenderLevels\":{\"PALADIN\":3,\"SPEARMAN\":3}";
        String body =
                post(request("{\"PALADIN\":2}", "{\"PALADIN\":2,\"SPEARMAN\":10}", 0, levels));
        assertThat(JsonPath.<Double>read(body, "$.scenarios[0].attack")).isEqualTo(264.0);
        assertThat(JsonPath.<Double>read(body, "$.scenarios[0].defense"))
                .isEqualTo(2 * 165 + 10 * 15 + 20.0);
    }

    @Test
    void aLevelOutsideTheRangeIsRefused() throws Exception {
        player.browser
                .post(
                        "/api/simulator",
                        request("{\"AXEMAN\":20}", "{}", 0, ",\"attackerLevels\":{\"AXEMAN\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWorldWithoutLuckHasOneScenario() throws Exception {
        setLuckRange(0);

        String body = simulate("{\"AXEMAN\":20}", "{}", 0);

        assertThat(JsonPath.<List<?>>read(body, "$.scenarios")).hasSize(1);
        // nobody defends: base defence 20, no losses for the attacker (02 §8)
        assertThat(JsonPath.<String>read(body, "$.scenarios[0].outcome")).isEqualTo("ATTACKER_WON");
        assertThat(units(body, "$.scenarios[0].defenderLosses")).isEmpty();
    }

    @Test
    void ramsAndCatapultsAreSimulatedAgainstTheGivenLevels() throws Exception {
        setLuckRange(0);

        // the battle test's vector: Wb = 5 (5 rams can't take level 5), D = 337.5, 5 axemen and 3
        // rams die, the 7 surviving rams take the wall from 5 to 4
        String body = simulate("{\"AXEMAN\":20,\"RAM\":10}", "{}", 5);
        assertThat(number(body, "$.scenarios[0].wallBefore").intValue()).isEqualTo(5);
        assertThat(number(body, "$.scenarios[0].wallAfter").intValue()).isEqualTo(4);
        assertThat(number(body, "$.scenarios[0].effectiveWall").intValue()).isEqualTo(5);
        assertThat((Object) JsonPath.read(body, "$.scenarios[0].catapultTarget")).isNull();

        // catapults aim at the Town Hall unless told otherwise and stop at its minimum level 1
        String hall =
                post(
                        "{\"attacker\":{\"AXEMAN\":500,\"CATAPULT\":60},\"defender\":{},\"wall\":0,"
                                + "\"catapultTargetLevel\":3}");
        assertThat(JsonPath.<String>read(hall, "$.scenarios[0].catapultTarget"))
                .isEqualTo("HEADQUARTERS");
        assertThat(number(hall, "$.scenarios[0].catapultTargetLevelBefore").intValue())
                .isEqualTo(3);
        assertThat(number(hall, "$.scenarios[0].catapultTargetLevelAfter").intValue()).isEqualTo(1);

        // aimed at the wall, the catapults work on the wall level of the request
        String wall =
                post(
                        "{\"attacker\":{\"AXEMAN\":500,\"CATAPULT\":60},\"defender\":{},\"wall\":4,"
                                + "\"catapultTarget\":\"WALL\"}");
        assertThat(number(wall, "$.scenarios[0].catapultTargetLevelBefore").intValue())
                .isEqualTo(4);
        assertThat(number(wall, "$.scenarios[0].wallAfter").intValue())
                .isEqualTo(number(wall, "$.scenarios[0].catapultTargetLevelAfter").intValue());
    }

    @Test
    void scoutsFightInTheirOwnPhase() throws Exception {
        setLuckRange(0);

        // 02 §7.1: 10 scouts against 4 win and lose round(10 × 0.4^1.5) = 3
        String body = simulate("{\"SCOUT\":10}", "{\"SCOUT\":4}", 0);

        assertThat(JsonPath.<String>read(body, "$.scenarios[0].outcome")).isEqualTo("SCOUTS_ONLY");
        assertThat((Object) JsonPath.read(body, "$.scenarios[0].attack")).isNull();
        assertThat(JsonPath.<Boolean>read(body, "$.scenarios[0].scouting.succeeded")).isTrue();
        assertThat(number(body, "$.scenarios[0].scouting.lost").intValue()).isEqualTo(3);
        assertThat(units(body, "$.scenarios[0].attackerSurvivors")).isEqualTo(Map.of("SCOUT", 7));
    }

    @Test
    void survivingNoblesLowerTheLoyaltyByARange() throws Exception {
        setLuckRange(0);

        // 2 nobles take 40 to 70: from 100 the village stays, from 60 it may fall, from 40 it does
        String from100 = simulateWithLoyalty("{\"AXEMAN\":100,\"NOBLE\":2}", 100);
        String loyalty = "$.scenarios[0].loyalty";
        assertThat(number(from100, loyalty + ".nobles").intValue()).isEqualTo(2);
        assertThat(number(from100, loyalty + ".before").intValue()).isEqualTo(100);
        assertThat(number(from100, loyalty + ".highest").intValue()).isEqualTo(60);
        assertThat(number(from100, loyalty + ".lowest").intValue()).isEqualTo(30);
        assertThat(JsonPath.<String>read(from100, loyalty + ".conquered")).isEqualTo("NO");
        String from60 = simulateWithLoyalty("{\"AXEMAN\":100,\"NOBLE\":2}", 60);
        assertThat(JsonPath.<String>read(from60, loyalty + ".conquered")).isEqualTo("POSSIBLE");
        String from40 = simulateWithLoyalty("{\"AXEMAN\":100,\"NOBLE\":2}", 40);
        assertThat(JsonPath.<String>read(from40, loyalty + ".conquered")).isEqualTo("YES");
        assertThat(number(from40, loyalty + ".highest").intValue()).isZero();
        // without loyalty in the request the village is at 100; without nobles there is no outlook
        assertThat((Object) JsonPath.read(simulate("{\"AXEMAN\":100}", "{}", 0), loyalty)).isNull();
    }

    @Test
    void aLostBattleLeavesNothingForTheNobles() throws Exception {
        setLuckRange(0);

        String body = simulate("{\"AXEMAN\":5,\"NOBLE\":2}", "{\"SWORDSMAN\":100}", 0);

        assertThat((Object) JsonPath.read(body, "$.scenarios[0].loyalty")).isNull();
    }

    @Test
    void theLootCapacityIsWhatTheSurvivorsCarry() throws Exception {
        setLuckRange(0);

        // A = 400, D = 20: no losses, 40 spearmen carry 25 each
        String body = simulate("{\"SPEARMAN\":40}", "{}", 0);

        assertThat(number(body, "$.scenarios[0].lootCapacity").intValue()).isEqualTo(1000);
    }

    @Test
    void theLargestRequestIsCalculatedWithoutOverflow() throws Exception {
        // every unit type at the cap on both sides, the highest wall and nobles with the highest
        // loyalty: sums, strengths and the carrying capacity stay inside their types (no 500)
        String everything =
                Arrays.stream(UnitType.values())
                        .map(type -> "\"" + type + "\":" + SimulatorRequest.MAX_UNITS)
                        .collect(Collectors.joining(",", "{", "}"));

        String body =
                post(
                        request(
                                everything,
                                everything,
                                20,
                                ",\"loyalty\":100,\"catapultTarget\":\"WALL\""));

        assertThat(JsonPath.<List<?>>read(body, "$.scenarios")).hasSize(3);
        assertThat(number(body, "$.scenarios[1].lootCapacity").longValue()).isNotNegative();
    }

    // ---- the request is checked ----

    @Test
    void anAttackWithoutUnitsIsRefused() throws Exception {
        for (String attacker : List.of("{}", "{\"AXEMAN\":0}")) {
            player.browser
                    .post("/api/simulator", request(attacker, "{\"SPEARMAN\":5}", 0, ""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMPTY_COMMAND"));
        }
    }

    @Test
    void numbersOutOfRangeAreRefused() throws Exception {
        String catapults = "{\"AXEMAN\":5,\"CATAPULT\":5}";
        String[] bad = {
            request("{\"AXEMAN\":-1}", "{}", 0, ""),
            request("{\"AXEMAN\":1000001}", "{}", 0, ""),
            request("{\"AXEMAN\":5}", "{\"SPEARMAN\":-5}", 0, ""),
            request("{\"AXEMAN\":5}", "{}", -1, ""),
            request("{\"AXEMAN\":5}", "{}", 31, ""),
            request("{\"AXEMAN\":5}", "{}", 21, ""), // the wall stops at level 20
            request("{\"AXEMAN\":5}", "{}", 0, ",\"loyalty\":101"),
            request("{\"AXEMAN\":5}", "{}", 0, ",\"loyalty\":-1"),
            request(catapults, "{}", 0, ",\"catapultTargetLevel\":31"),
            request(catapults, "{}", 0, ",\"catapultTargetLevel\":-1"),
            request(catapults, "{}", 0, ",\"catapultTarget\":\"CASTLE\""),
            // the Hiding Place stops at level 10
            request(
                    catapults,
                    "{}",
                    0,
                    ",\"catapultTarget\":\"HIDING_PLACE\",\"catapultTargetLevel\":11"),
            request("{\"DRAGON\":5}", "{}", 0, ""),
            "{\"attacker\":{\"AXEMAN\":5},\"defender\":{}}", // no wall
            "{\"defender\":{},\"wall\":0}", // no attacker
            "{}",
            "not json",
        };
        for (String body : bad) {
            player.browser
                    .post("/api/simulator", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"));
        }
    }

    // ---- banners (M32c, 02-game-rules §21.2, §21.6.2) ----

    @Test
    void theWarAndTheBulwarkBannerCountLikeInARealBattle() throws Exception {
        setLuckRange(0);
        // 02 §21.2: 100 axemen against 100 spearmen behind wall 5 lose 41, 36 with a tier III War
        // banner, 47 against a tier III Bulwark
        String army = "{\"AXEMAN\":100}";
        String spears = "{\"SPEARMAN\":100}";

        String plain = post(request(army, spears, 5, ""));
        assertThat(units(plain, "$.scenarios[0].attackerLosses")).isEqualTo(Map.of("AXEMAN", 41));
        assertThat(number(plain, "$.attackBonus").doubleValue()).isZero();
        assertThat(number(plain, "$.defenceBonus").doubleValue()).isZero();
        assertThat((Object) JsonPath.read(plain, "$.scenarios[0].theft")).isNull();

        String war = post(request(army, spears, 5, ",\"warTier\":3"));
        assertThat(units(war, "$.scenarios[0].attackerLosses")).isEqualTo(Map.of("AXEMAN", 36));
        assertThat(number(war, "$.attackBonus").doubleValue()).isEqualTo(0.10);

        String bulwark = post(request(army, spears, 5, ",\"bulwarkTier\":3,\"bannerHall\":3"));
        assertThat(units(bulwark, "$.scenarios[0].attackerLosses")).isEqualTo(Map.of("AXEMAN", 47));
        assertThat(number(bulwark, "$.defenceBonus").doubleValue()).isEqualTo(0.10);

        // the hall caps the Bulwark: tier III works as tier I in a level 1 hall, sleeps without one
        String capped = post(request(army, spears, 5, ",\"bulwarkTier\":3,\"bannerHall\":1"));
        assertThat(number(capped, "$.defenceBonus").doubleValue()).isEqualTo(0.03);
        String asleep = post(request(army, spears, 5, ",\"bulwarkTier\":3"));
        assertThat(number(asleep, "$.defenceBonus").doubleValue()).isZero();
        assertThat(units(asleep, "$.scenarios[0].attackerLosses")).isEqualTo(Map.of("AXEMAN", 41));
    }

    /** Code review 1.2, D4: Plunder and Oath count like in a real battle. */
    @Test
    void thePlunderAndTheOathBannerCountLikeInARealBattle() throws Exception {
        setLuckRange(0);

        // 40 spearmen carry 25 each: 1000, a tier III Plunder banner adds 30 %
        String plunder = post(request("{\"SPEARMAN\":40}", "{}", 0, ",\"plunderTier\":3"));
        assertThat(number(plunder, "$.scenarios[0].lootCapacity").intValue()).isEqualTo(1300);
        assertThat(number(plunder, "$.plunderBonus").doubleValue()).isEqualTo(0.30);

        // 2 nobles take 2 × round(20 × 0.88) = 36 to 2 × round(35 × 0.88) = 62 against a tier III
        // Oath: from 100 the loyalty ends between 64 and 38
        String loyalty = "$.scenarios[0].loyalty";
        String oath =
                post(
                        request(
                                "{\"AXEMAN\":100,\"NOBLE\":2}",
                                "{}",
                                0,
                                ",\"oathTier\":3,\"bannerHall\":3"));
        assertThat(number(oath, loyalty + ".highest").intValue()).isEqualTo(64);
        assertThat(number(oath, loyalty + ".lowest").intValue()).isEqualTo(38);
        assertThat(number(oath, "$.oathBonus").doubleValue()).isEqualTo(0.12);
        // from 70 the plain nobles may conquer (lowest 0), behind the Oath they can't (70 − 62 = 8)
        String plain = simulateWithLoyalty("{\"AXEMAN\":100,\"NOBLE\":2}", 70);
        assertThat(JsonPath.<String>read(plain, loyalty + ".conquered")).isEqualTo("POSSIBLE");
        String held =
                post(
                        request(
                                "{\"AXEMAN\":100,\"NOBLE\":2}",
                                "{}",
                                0,
                                ",\"loyalty\":70,\"oathTier\":3,\"bannerHall\":3"));
        assertThat(JsonPath.<String>read(held, loyalty + ".conquered")).isEqualTo("NO");

        // the hall caps the Oath like the Bulwark: asleep without one
        String asleep = post(request("{\"AXEMAN\":100,\"NOBLE\":2}", "{}", 0, ",\"oathTier\":3"));
        assertThat(number(asleep, "$.oathBonus").doubleValue()).isZero();
        assertThat(number(asleep, loyalty + ".lowest").intValue()).isEqualTo(30);
    }

    @Test
    void aVillageFliesOneBanner() throws Exception {
        player.browser
                .post(
                        "/api/simulator",
                        request("{\"AXEMAN\":20}", "{}", 0, ",\"warTier\":1,\"plunderTier\":1"))
                .andExpect(status().isBadRequest());
        player.browser
                .post(
                        "/api/simulator",
                        request(
                                "{\"AXEMAN\":20}",
                                "{}",
                                0,
                                ",\"bulwarkTier\":1,\"oathTier\":1,\"bannerHall\":1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withBannersSwitchedOffTheBannerInputsAreIgnored() throws Exception {
        setLuckRange(0);
        setBanners(false);
        try {
            String body =
                    post(
                            request(
                                    "{\"AXEMAN\":100,\"BANNER_THIEF\":10}",
                                    "{\"SPEARMAN\":100}",
                                    5,
                                    ",\"warTier\":3,\"bulwarkTier\":3,\"bannerHall\":3"));
            assertThat(number(body, "$.attackBonus").doubleValue()).isZero();
            String more =
                    post(
                            request(
                                    "{\"SPEARMAN\":40}",
                                    "{}",
                                    0,
                                    ",\"plunderTier\":3,\"oathTier\":3,\"bannerHall\":3"));
            assertThat(number(more, "$.plunderBonus").doubleValue()).isZero();
            assertThat(number(more, "$.oathBonus").doubleValue()).isZero();
            assertThat(number(more, "$.scenarios[0].lootCapacity").intValue()).isEqualTo(1000);
            assertThat(number(body, "$.defenceBonus").doubleValue()).isZero();
            assertThat((Object) JsonPath.read(body, "$.scenarios[0].theft")).isNull();
        } finally {
            setBanners(true);
        }
    }

    @Test
    void theGuildBannerOfLevelThreeAddsItsDefenceAndOnlyWhileTheWorldHasIt() throws Exception {
        setLuckRange(0);
        String plain = post(request("{\"AXEMAN\":100}", "{\"SPEARMAN\":100}", 5, ""));
        jdbc.sql("update world_settings set guilds_enabled = true, guild_banners_enabled = true")
                .update();
        worldService.reload();
        try {
            String level2 =
                    post(
                            request(
                                    "{\"AXEMAN\":100}",
                                    "{\"SPEARMAN\":100}",
                                    5,
                                    ",\"guildBannerLevel\":2"));
            String level3 =
                    post(
                            request(
                                    "{\"AXEMAN\":100}",
                                    "{\"SPEARMAN\":100}",
                                    5,
                                    ",\"guildBannerLevel\":3"));
            assertThat(number(level2, "$.guildDefenceBonus").doubleValue()).isZero();
            assertThat(number(level3, "$.guildDefenceBonus").doubleValue()).isEqualTo(0.05);
            // D grows by 5 %, the Bulwark's own number stays out of it
            assertThat(number(level3, "$.scenarios[0].defense").doubleValue())
                    .isCloseTo(
                            number(plain, "$.scenarios[0].defense").doubleValue() * 1.05,
                            within(1e-6));
            assertThat(number(level3, "$.defenceBonus").doubleValue()).isZero();
        } finally {
            jdbc.sql(
                            "update world_settings set guilds_enabled = false,"
                                    + " guild_banners_enabled = false")
                    .update();
            worldService.reload();
        }
        // switched off in the world: the field is ignored
        String off =
                post(
                        request(
                                "{\"AXEMAN\":100}",
                                "{\"SPEARMAN\":100}",
                                5,
                                ",\"guildBannerLevel\":3"));
        assertThat(number(off, "$.guildDefenceBonus").doubleValue()).isZero();
    }

    @Test
    void survivingThievesShowTheChanceTheHallLeavesThem() throws Exception {
        setLuckRange(0);

        // nobody defends: all 10 thieves survive, a level 2 hall takes 20 % off
        String won =
                post(request("{\"AXEMAN\":20,\"BANNER_THIEF\":10}", "{}", 0, ",\"bannerHall\":2"));
        Map<String, Object> theft = JsonPath.read(won, "$.scenarios[0].theft");
        assertThat(theft).containsEntry("sent", 10).containsEntry("survived", 10);
        assertThat(((Number) theft.get("chance")).doubleValue())
                .isCloseTo((1 - Math.pow(0.97, 10)) * 0.8, within(1e-9));

        // a lost battle: no thief survives, no chance
        String lost =
                post(
                        request(
                                "{\"AXEMAN\":20,\"BANNER_THIEF\":10}",
                                "{\"SWORDSMAN\":100}",
                                0,
                                ",\"bannerHall\":2"));
        Map<String, Object> none = JsonPath.read(lost, "$.scenarios[0].theft");
        assertThat(none).containsEntry("survived", 0);
        assertThat(((Number) none.get("chance")).doubleValue()).isZero();
    }

    @Test
    void theThiefLevelRaisesTheChancePerThief() throws Exception {
        setLuckRange(0);

        // thief level 3 (Hall training, 1.4): 5 % per thief instead of 3 %, unit levels off
        String body =
                post(
                        request(
                                "{\"AXEMAN\":20,\"BANNER_THIEF\":10}",
                                "{}",
                                0,
                                ",\"bannerHall\":2,\"attackerLevels\":{\"BANNER_THIEF\":3}"));
        assertThat(number(body, "$.scenarios[0].theft.chance").doubleValue())
                .isCloseTo((1 - Math.pow(0.95, 10)) * 0.8, within(1e-9));
    }

    @Test
    void catapultsOnTheBannerHallMakeTheTheftEasier() throws Exception {
        setLuckRange(0);

        String body =
                post(
                        request(
                                "{\"AXEMAN\":50,\"CATAPULT\":40,\"BANNER_THIEF\":10}",
                                "{}",
                                0,
                                ",\"catapultTarget\":\"BANNER_HALL\",\"catapultTargetLevel\":0,"
                                        + "\"bannerHall\":3"));
        // the hall's level is the target's level, whatever catapultTargetLevel says
        assertThat(number(body, "$.scenarios[0].catapultTargetLevelBefore").intValue())
                .isEqualTo(3);
        int after = number(body, "$.scenarios[0].catapultTargetLevelAfter").intValue();
        assertThat(after).isLessThan(3);
        assertThat(number(body, "$.scenarios[0].theft.chance").doubleValue())
                .isCloseTo((1 - Math.pow(0.97, 10)) * (1 - 0.1 * after), within(1e-9));
    }

    @Test
    void aBannerTierOutsideTheRangeIsRefused() throws Exception {
        player.browser
                .post("/api/simulator", request("{\"AXEMAN\":20}", "{}", 0, ",\"warTier\":4"))
                .andExpect(status().isBadRequest());
        player.browser
                .post("/api/simulator", request("{\"AXEMAN\":20}", "{}", 0, ",\"bannerHall\":4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ---- paladin standards (1.4, 02-game-rules §21.18) ----

    @Test
    void theAttackersStandardNeedsAPaladinAndWorksUpToHisLevel() throws Exception {
        setLuckRange(0);
        String axe = ",\"attackerStandard\":\"AXE_STANDARD\",\"attackerStandardTier\":3";

        // without a paladin nobody carries it
        String alone = post(request("{\"AXEMAN\":100}", "{}", 0, axe));
        assertThat((Object) JsonPath.read(alone, "$.attackerStandard")).isNull();
        double plain = number(alone, "$.scenarios[0].attack").doubleValue();

        // a level 1 paladin: tier III works as tier I (5 %)
        String levelOne = post(request("{\"AXEMAN\":100,\"PALADIN\":1}", "{}", 0, axe));
        assertThat(JsonPath.<Map<String, Object>>read(levelOne, "$.attackerStandard"))
                .containsEntry("type", "AXE_STANDARD")
                .containsEntry("tier", 3)
                .containsEntry("effectiveTier", 1)
                .containsEntry("bonus", 0.05);
        double paladin =
                number(
                                post(request("{\"AXEMAN\":100,\"PALADIN\":1}", "{}", 0, "")),
                                "$.scenarios[0].attack")
                        .doubleValue();
        assertThat(number(levelOne, "$.scenarios[0].attack").doubleValue())
                .isCloseTo(paladin + 4000 * 0.05, within(1e-6));
        assertThat(paladin).isGreaterThan(plain);

        // a level 3 paladin: the full 15 %
        String levelThree =
                post(
                        request(
                                "{\"AXEMAN\":100,\"PALADIN\":1}",
                                "{}",
                                0,
                                axe + ",\"attackerLevels\":{\"PALADIN\":3}"));
        assertThat(number(levelThree, "$.attackerStandard.bonus").doubleValue()).isEqualTo(0.15);
    }

    @Test
    void theDefendersStandardSleepsWithoutAHall() throws Exception {
        setLuckRange(0);
        String spear = ",\"defenderStandard\":\"SPEAR_STANDARD\",\"defenderStandardTier\":1";
        String defender = "{\"SPEARMAN\":100,\"PALADIN\":1}";

        String asleep = post(request("{\"AXEMAN\":100}", defender, 0, spear));
        assertThat(number(asleep, "$.defenderStandard.effectiveTier").intValue()).isZero();
        assertThat(number(asleep, "$.defenderStandard.bonus").doubleValue()).isZero();

        String awake = post(request("{\"AXEMAN\":100}", defender, 0, spear + ",\"bannerHall\":1"));
        assertThat(number(awake, "$.defenderStandard.bonus").doubleValue()).isEqualTo(0.05);
        // 100 spearmen 1 500 against infantry: 75 more
        assertThat(number(awake, "$.scenarios[0].defense").doubleValue())
                .isCloseTo(
                        number(asleep, "$.scenarios[0].defense").doubleValue() + 75, within(1e-6));
    }

    @Test
    void theDefendersThievesMayTakeTheStandardOfALostAttack() throws Exception {
        setLuckRange(0);
        String axe = ",\"attackerStandard\":\"AXE_STANDARD\",\"attackerStandardTier\":1";

        String lost =
                post(
                        request(
                                "{\"AXEMAN\":10,\"PALADIN\":1}",
                                "{\"SPEARMAN\":100,\"BANNER_THIEF\":10}",
                                0,
                                axe + ",\"bannerHall\":3"));
        Map<String, Object> theft = JsonPath.read(lost, "$.scenarios[0].standardTheft");
        int survived = ((Number) theft.get("survived")).intValue();
        assertThat(theft).containsEntry("sent", 10);
        assertThat(survived).isBetween(1, 9);
        // no hall reduction: the hall is the defender's own
        assertThat(((Number) theft.get("chance")).doubleValue())
                .isCloseTo(1 - Math.pow(0.97, survived), within(1e-9));

        // a won attack keeps its standard
        String won =
                post(request("{\"AXEMAN\":100,\"PALADIN\":1}", "{\"BANNER_THIEF\":1}", 0, axe));
        assertThat((Object) JsonPath.read(won, "$.scenarios[0].standardTheft")).isNull();
    }

    @Test
    void aVillageBannerIsNoStandard() throws Exception {
        player.browser
                .post(
                        "/api/simulator",
                        request(
                                "{\"AXEMAN\":20,\"PALADIN\":1}",
                                "{}",
                                0,
                                ",\"attackerStandard\":\"WAR\",\"attackerStandardTier\":1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void theNightBonusDoublesTheDefenceWhenTheWorldHasIt() throws Exception {
        // 20 axemen (A = 800) against 50 spearmen (D = 770): won by day, lost at night (1540)
        String night = ",\"night\":true";
        setNightBonus(false);
        String off = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, night));
        assertThat(JsonPath.<Double>read(off, "$.nightFactor")).isEqualTo(1.0);
        assertThat(JsonPath.<Double>read(off, "$.scenarios[1].defense")).isEqualTo(770.0);

        setNightBonus(true);
        try {
            String day = simulate("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0);
            assertThat(JsonPath.<Double>read(day, "$.nightFactor")).isEqualTo(1.0);
            assertThat(JsonPath.<String>read(day, "$.scenarios[1].outcome"))
                    .isEqualTo("ATTACKER_WON");

            String atNight = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, night));
            assertThat(JsonPath.<Double>read(atNight, "$.nightFactor")).isEqualTo(2.0);
            assertThat(JsonPath.<Double>read(atNight, "$.scenarios[1].defense")).isEqualTo(1540.0);
            assertThat(JsonPath.<String>read(atNight, "$.scenarios[1].outcome"))
                    .isEqualTo("DEFENDER_WON");
        } finally {
            setNightBonus(false);
        }
    }

    @Test
    void archersAttackAsTheirOwnClassLikeInARealBattle() throws Exception {
        // 1.6 (02 §5.6, §8): 50 mounted archers (A = 5500) meet the spearmen's defence against
        // archers, 100 × 20 + 20 = 2020; light cavalry would meet 40 each. Archers that exist
        // fight in every world, so the simulator takes them whatever the setting.
        setLuckRange(0);
        String body = simulate("{\"MOUNTED_ARCHER\":50}", "{\"SPEARMAN\":100}", 0);
        assertThat(JsonPath.<Double>read(body, "$.scenarios[0].attack")).isEqualTo(5500.0);
        assertThat(JsonPath.<Double>read(body, "$.scenarios[0].defense")).isEqualTo(2020.0);
        assertThat(units(body, "$.scenarios[0].attackerLosses"))
                .isEqualTo(Map.of("MOUNTED_ARCHER", 11));
        assertThat(units(body, "$.scenarios[0].defenderLosses")).isEqualTo(Map.of("SPEARMAN", 100));

        // with unit levels: the mounted archer's level 2 attacks with 121, the spearman's level 2
        // defends with 22 against archers
        setLevelsEnabled(true);
        try {
            String levels =
                    ",\"attackerLevels\":{\"MOUNTED_ARCHER\":2},\"defenderLevels\":{\"SPEARMAN\":2}";
            String leveled =
                    post(request("{\"MOUNTED_ARCHER\":50}", "{\"SPEARMAN\":100}", 0, levels));
            assertThat(JsonPath.<Double>read(leveled, "$.scenarios[0].attack")).isEqualTo(6050.0);
            assertThat(JsonPath.<Double>read(leveled, "$.scenarios[0].defense")).isEqualTo(2220.0);
        } finally {
            setLevelsEnabled(false);
        }
    }

    @Test
    void moraleWeakensOnlyTheAttackWhenTheWorldHasIt() throws Exception {
        // 20 axemen (A = 800) against 50 spearmen (D = 770): won at full strength, lost at 90 %
        String morale = ",\"morale\":90";
        String full = simulate("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0);
        assertThat(JsonPath.<Double>read(full, "$.morale")).isEqualTo(1.0);
        assertThat(JsonPath.<String>read(full, "$.scenarios[1].outcome")).isEqualTo("ATTACKER_WON");

        String weak = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, morale));
        assertThat(JsonPath.<Double>read(weak, "$.morale")).isEqualTo(0.9);
        assertThat(JsonPath.<Double>read(weak, "$.scenarios[1].attack")).isEqualTo(720.0);
        assertThat(JsonPath.<Double>read(weak, "$.scenarios[1].defense")).isEqualTo(770.0);
        assertThat(JsonPath.<String>read(weak, "$.scenarios[1].outcome")).isEqualTo("DEFENDER_WON");

        // below the world's floor it counts as the floor, like in a real battle
        jdbc.sql("update world_settings set morale_floor = 95").update();
        worldService.reload();
        try {
            String floored = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, morale));
            assertThat(JsonPath.<Double>read(floored, "$.morale")).isEqualTo(0.95);
            assertThat(JsonPath.<Double>read(floored, "$.scenarios[1].attack")).isEqualTo(760.0);
        } finally {
            jdbc.sql("update world_settings set morale_floor = 30").update();
            worldService.reload();
        }

        setMorale(false);
        try {
            String off = post(request("{\"AXEMAN\":20}", "{\"SPEARMAN\":50}", 0, morale));
            assertThat(JsonPath.<Double>read(off, "$.morale")).isEqualTo(1.0);
        } finally {
            setMorale(true);
        }

        player.browser
                .post("/api/simulator", request("{\"AXEMAN\":20}", "{}", 0, ",\"morale\":29"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itNeedsALoginAndTheCsrfToken() throws Exception {
        String valid = request("{\"AXEMAN\":5}", "{}", 0, "");
        Browser.open(mockMvc).post("/api/simulator", valid).andExpect(status().isUnauthorized());
        player.browser
                .postWithoutCsrfHeader("/api/simulator", valid)
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private String simulate(String attacker, String defender, int wall) throws Exception {
        return post(request(attacker, defender, wall, ""));
    }

    private String simulateWithLoyalty(String attacker, int loyalty) throws Exception {
        return post(request(attacker, "{}", 0, ",\"loyalty\":" + loyalty));
    }

    private String post(String json) throws Exception {
        return body(player.browser.post("/api/simulator", json).andExpect(status().isOk()));
    }

    private static String request(String attacker, String defender, int wall, String more) {
        return "{\"attacker\":"
                + attacker
                + ",\"defender\":"
                + defender
                + ",\"wall\":"
                + wall
                + more
                + "}";
    }

    private static Map<String, Integer> units(String json, String path) {
        return JsonPath.read(json, path);
    }

    private void setLevelsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set unit_levels_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void setBanners(boolean enabled) {
        jdbc.sql("update world_settings set banners_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }

    private void setNightBonus(boolean enabled) {
        jdbc.sql("update world_settings set night_bonus_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void setMorale(boolean enabled) {
        jdbc.sql("update world_settings set morale_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void setLuckRange(double luckRange) {
        jdbc.sql("update world_settings set luck_range = ?").param(luckRange).update();
        worldService.reload();
    }
}
