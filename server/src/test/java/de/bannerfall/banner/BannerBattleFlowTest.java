package de.bannerfall.banner;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.FixedRandomSource;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestRandomSource;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * Banners in battle (M32b, 02-game-rules §21.2, §21.5–§21.7): War, Bulwark, Haste, Plunder and
 * Oath, the drops with their filters, the banner thief and theft, and the report blocks. Luck is
 * off, the dice are fixed per test: {@code 0.0} makes every roll succeed (a drop is then a tier I
 * Harvest banner), {@code 0.99} makes every roll fail and the nobles hit 35.
 */
@IntegrationTest
class BannerBattleFlowTest {

    private static final double ALWAYS = 0.0;
    private static final double NEVER = 0.99;
    private static final int BARBARIAN_ROW = TestWorldConfiguration.MAP_SIZE - 14;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;
    @Autowired TestRandomSource dice;
    @Autowired SentMessages sent;

    private double luckRangeBefore;
    private int nobleMaxDistanceBefore;
    private TestPlayer attacker;
    private TestPlayer defender;
    private final List<Long> barbarianVillages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        luckRangeBefore = worldService.settings().luckRange();
        nobleMaxDistanceBefore = worldService.settings().nobleMaxDistance();
        jdbc.sql("update world_settings set luck_range = 0, noble_max_distance = 1000").update();
        setBanners(true, 1.0);
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        endProtection(defender);
        endProtection(attacker);
        dice.use(FixedRandomSource.constant(NEVER));
    }

    @AfterEach
    void cleanUp() {
        dice.reset();
        jdbc.sql("delete from game_event").update();
        for (long id : barbarianVillages) {
            jdbc.sql(
                            "delete from movement where home_village_id = :id or"
                                    + " source_village_id = :id or target_village_id = :id")
                    .param("id", id)
                    .update();
            jdbc.sql(
                            "delete from unit_stack where home_village_id = :id"
                                    + " or location_village_id = :id")
                    .param("id", id)
                    .update();
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        barbarianVillages.clear();
        jdbc.sql("update world_settings set luck_range = ?, noble_max_distance = ?")
                .params(luckRangeBefore, nobleMaxDistanceBefore)
                .update();
        setBanners(true, 1.0);
    }

    // ---- War and Bulwark (§21.2) ----

    @Test
    void warRaisesTheAttackAndIsTakenAlongWhenTheAttackLeaves() throws Exception {
        // 02 §21.2: 100 axemen against 100 spearmen behind wall 5 lose 41 without a banner and 36
        // with a tier III War banner
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setLevel(defender.villageId, BuildingType.WALL, 5);
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 3);
        long war = flyBanner(attacker, attacker.villageId, BannerType.WAR, 3);

        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":100}");
        // swapped out while the army walks: the attack keeps what it took along
        jdbc.sql("update banner set equipped_village_id = null where id = ?").param(war).update();
        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 36));
        assertThat(JsonPath.<Double>read(report, "$.payload.banners.attackBonus")).isEqualTo(0.10);
        // no scouts: the attacker learns nothing about the defender's banner
        assertThat((Object) JsonPath.read(report, "$.payload.banners.defenceBonus")).isNull();
        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Double>read(defense, "$.payload.banners.attackBonus")).isEqualTo(0.10);
    }

    @Test
    void theHallCapsTheWarBanner() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setLevel(defender.villageId, BuildingType.WALL, 5);
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 1);
        flyBanner(attacker, attacker.villageId, BannerType.WAR, 3);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        String report = attacker.newestReport("ATTACK");
        // tier I numbers: A = 4000 × 1.03, D = 2212.5 → (2212.5 / 4120)^1.5 × 100 = 39.4
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 39));
        assertThat(JsonPath.<Double>read(report, "$.payload.banners.attackBonus")).isEqualTo(0.03);
    }

    @Test
    void bulwarkRaisesTheDefenceOfEverybodyInTheVillage() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setLevel(defender.villageId, BuildingType.WALL, 5);
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 3);
        flyBanner(defender, defender.villageId, BannerType.BULWARK, 3);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        // 02 §21.2: the same fight costs the attacker 47 instead of 41
        String report = attacker.newestReport("ATTACK");
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 47));
        assertThat((Object) JsonPath.read(report, "$.payload.banners")).isNull();
        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Double>read(defense, "$.payload.banners.defenceBonus"))
                .isEqualTo(0.10);
    }

    @Test
    void aSuccessfulScoutSeesTheBulwark() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.SCOUT, 5));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 10));
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 2);
        flyBanner(defender, defender.villageId, BannerType.BULWARK, 3);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"SCOUT\":5}"));

        String report = attacker.newestReport("ATTACK");
        // the hall caps it at tier II
        assertThat(JsonPath.<Double>read(report, "$.payload.banners.defenceBonus")).isEqualTo(0.06);
    }

    @Test
    void aDormantBannerDoesNothingInBattle() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setLevel(defender.villageId, BuildingType.WALL, 5);
        flyBanner(defender, defender.villageId, BannerType.BULWARK, 3); // no hall: asleep

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        assertThat(units(attacker.newestReport("ATTACK"), "$.payload.attackerLosses"))
                .isEqualTo(Map.of("AXEMAN", 41));
    }

    @Test
    void withBannersSwitchedOffTheBattleBannersDoNothing() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        setLevel(defender.villageId, BuildingType.WALL, 5);
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 3);
        flyBanner(attacker, attacker.villageId, BannerType.WAR, 3);

        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":100}");
        setBanners(false, 1.0); // the host switches them off while the army walks
        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(units(report, "$.payload.attackerLosses")).isEqualTo(Map.of("AXEMAN", 41));
        assertThat((Object) JsonPath.read(report, "$.payload.banners")).isNull();
    }

    // ---- Haste and Plunder ----

    @Test
    void hasteShortensTheWayThereAndBack() throws Exception {
        setUnits(attacker, Map.of(UnitType.SPEARMAN, 40));
        Coordinates target = coordinates(defender);
        long plain = travelSeconds(attacker, target, "{\"SPEARMAN\":40}");
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 3);
        flyBanner(attacker, attacker.villageId, BannerType.HASTE, 3);
        long hasted = Math.max(1, Math.round(plain * 0.85));

        assertThat(travelSeconds(attacker, target, "{\"SPEARMAN\":40}")).isEqualTo(hasted);
        Instant sentAt = clock.instant();
        Instant arrival = attack(attacker, defender, "{\"SPEARMAN\":40}");
        assertThat(arrival).isEqualTo(sentAt.plusSeconds(hasted));
        arriveAt(arrival);

        // A = 400, D = 20: nobody dies, so the survivors walk at the same pace, hasted again
        String troops = body(attacker.troops());
        assertThat(JsonPath.<String>read(troops, "$.incoming[0].kind")).isEqualTo("RETURN");
        assertThat(Instant.parse(JsonPath.read(troops, "$.incoming[0].arriveAt")))
                .isEqualTo(arrival.plusSeconds(hasted));
    }

    @Test
    void hasteAlsoSpeedsUpSupportsButNotAWithdrawal() throws Exception {
        setUnits(attacker, Map.of(UnitType.SPEARMAN, 10));
        Coordinates target = coordinates(defender);
        long plain = travelSeconds(attacker, target, "{\"SPEARMAN\":10}", "SUPPORT");
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 2);
        flyBanner(attacker, attacker.villageId, BannerType.HASTE, 3);

        Instant sentAt = clock.instant();
        String support =
                body(
                        attacker.command("SUPPORT", target, "{\"SPEARMAN\":10}")
                                .andExpect(status().isCreated()));
        Instant arrival = Instant.parse(JsonPath.read(support, "$.arriveAt"));
        // tier II (hall 2): −10 %
        assertThat(arrival).isEqualTo(sentAt.plusSeconds(Math.round(plain * 0.9)));
        arriveAt(arrival);

        long stackId =
                jdbc.sql(
                                "select id from unit_stack where home_village_id = ? and"
                                        + " location_village_id = ?")
                        .params(attacker.villageId, defender.villageId)
                        .query(Long.class)
                        .single();
        String back =
                body(
                        attacker.returnStack(stackId, "{\"SPEARMAN\":10}")
                                .andExpect(status().isCreated()));
        assertThat(Instant.parse(JsonPath.read(back, "$.arriveAt")))
                .isEqualTo(arrival.plusSeconds(plain));
    }

    @Test
    void plunderLetsTheSurvivorsCarryMore() throws Exception {
        setUnits(attacker, Map.of(UnitType.SPEARMAN, 40));
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 3);
        flyBanner(attacker, attacker.villageId, BannerType.PLUNDER, 3);
        setLevel(defender.villageId, BuildingType.HIDING_PLACE, 0);
        setLevel(defender.villageId, BuildingType.WAREHOUSE, 20);

        Instant arrival = attack(attacker, defender, "{\"SPEARMAN\":40}");
        setResources(defender, 5000, arrival);
        arriveAt(arrival);

        // 40 × 25 = 1000, +30 % = 1300
        Map<String, Integer> loot = units(attacker.newestReport("ATTACK"), "$.payload.loot");
        assertThat(loot.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1300);
    }

    // ---- Oath ----

    @Test
    void oathSoftensTheNoblesHit() throws Exception {
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 3);
        flyBanner(defender, defender.villageId, BannerType.OATH, 3);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.NOBLE, 1));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"NOBLE\":1}"));

        // the dice say 35; tier III takes 12 % off: round(35 × 0.88) = 31
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                attacker.newestReport("ATTACK"), "$.payload.loyalty"))
                .isEqualTo(Map.of("before", 100, "after", 69, "conquered", false));
    }

    // ---- drops (§21.7) ----

    @Test
    void theWinnerOfABigEnoughBattleFindsABannerOncePerPairAndDay() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 300));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30)); // 30 population: just enough

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<Map<String, Object>>read(report, "$.payload.banners.found"))
                .isEqualTo(Map.of("type", "HARVEST", "tier", 1));
        assertThat((Object) JsonPath.read(defender.newestReport("DEFENSE"), "$.payload.banners"))
                .isNull();
        assertThat(bannersOf(attacker)).containsExactly("HARVEST:1:FOUND");
        assertThat(
                        count(
                                "select count(*) from banner_event e join banner b on b.id ="
                                        + " e.banner_id where b.owner_id = ? and e.kind = 'FOUND'"
                                        + " and e.village_id = ?",
                                attacker.playerId,
                                defender.villageId))
                .isEqualTo(1);
        assertThat(sent.toPlayer(attacker.playerId))
                .anyMatch(json -> json.contains("\"type\":\"BANNER_FOUND\""));

        // the same pair again within a day: no roll
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        assertThat(bannersOf(attacker)).hasSize(1);

        // a real day later it rolls again
        clock.advance(Duration.ofHours(24));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        assertThat(bannersOf(attacker)).hasSize(2);
    }

    @Test
    void beatingASmallArmyFindsNothing() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 200));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 29));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        assertThat(bannersOf(attacker)).isEmpty();
        // and no roll was used up: a bigger army the same day still rolls
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        assertThat(bannersOf(attacker)).hasSize(1);
    }

    @Test
    void theOwnerOfAVillageThatHoldsRollsToo() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(attacker, Map.of(UnitType.SPEARMAN, 30));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 200));

        arriveAt(attack(attacker, defender, "{\"SPEARMAN\":30}"));

        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("DEFENSE"), "$.payload.banners.found"))
                .isEqualTo(Map.of("type", "HARVEST", "tier", 1));
        assertThat(bannersOf(defender)).containsExactly("HARVEST:1:FOUND");
        assertThat(bannersOf(attacker)).isEmpty();
    }

    @Test
    void barbariansDropRarelyAndOnlyForABigEnoughArmy() throws Exception {
        dice.use(FixedRandomSource.constant(0.014)); // below 1.5 %
        long barbarian = barbarianVillage();
        Coordinates at = coordinatesOf(barbarian);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));

        arriveAt(attack(attacker, at, "{\"AXEMAN\":29}"));
        assertThat(bannersOf(attacker)).isEmpty();

        arriveAt(attack(attacker, at, "{\"AXEMAN\":30}"));
        // tier from 0.014 × 100 = 1.4 → I; type index floor(0.014 × 9) = 0 → Harvest
        assertThat(bannersOf(attacker)).containsExactly("HARVEST:1:FOUND");

        dice.use(FixedRandomSource.constant(0.016)); // above 1.5 %: a day later, nothing
        clock.advance(Duration.ofHours(24));
        arriveAt(attack(attacker, at, "{\"AXEMAN\":30}"));
        assertThat(bannersOf(attacker)).hasSize(1);
    }

    @Test
    void eightDropsADayAreTheMost() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        for (int i = 0; i < 8; i++) {
            jdbc.sql(
                            "insert into banner_event (banner_id, kind, to_player_id, happened_at)"
                                    + " values (0, 'FOUND', ?, ?)")
                    .params(attacker.playerId, Timestamp.from(clock.instant().minusSeconds(60)))
                    .update();
        }
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));

        assertThat(bannersOf(attacker)).isEmpty();
    }

    @Test
    void theHostCanSwitchTheDropsOff() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 300));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        setBanners(true, 0);

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        assertThat(bannersOf(attacker)).isEmpty();

        setBanners(false, 1.0);
        setUnits(defender, Map.of(UnitType.SPEARMAN, 30));
        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100}"));
        assertThat(bannersOf(attacker)).isEmpty();
    }

    // ---- the banner thief (§21.5) ----

    @Test
    void theBannerHallTrainsThieves() throws Exception {
        setLevel(attacker.villageId, BuildingType.BANNER_HALL, 1);
        setLevel(attacker.villageId, BuildingType.FARM, 10);
        setStock(attacker, 5000);

        String recruitment = body(attacker.recruitment().andExpect(status().isOk()));
        assertThat(
                        JsonPath.<List<String>>read(
                                recruitment, "$[?(@.building == 'BANNER_HALL')].units[*].unit"))
                .containsExactly("BANNER_THIEF");
        assertThat(
                        JsonPath.<List<Boolean>>read(
                                recruitment,
                                "$[?(@.building == 'BANNER_HALL')].units[0].available"))
                .containsExactly(true);
        attacker.recruit(UnitType.BANNER_THIEF, 2).andExpect(status().isCreated());

        setBanners(false, 1.0);
        assertThat(JsonPath.<List<String>>read(body(attacker.recruitment()), "$[*].building"))
                .doesNotContain("BANNER_HALL");
        attacker.recruit(UnitType.BANNER_THIEF, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANNERS_DISABLED"));
        assertThat(JsonPath.<List<String>>read(body(attacker.smithy()), "$.units[*].unit"))
                .doesNotContain("BANNER_THIEF");
    }

    @Test
    void thievesNeedAHall() throws Exception {
        setStock(attacker, 5000);
        attacker.recruit(UnitType.BANNER_THIEF, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUIREMENTS_NOT_MET"));
    }

    // ---- theft (§21.6.2) ----

    @Test
    void survivingThievesStealTheBannerAfterAWonBattle() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 1);
        long banner = flyBanner(defender, defender.villageId, BannerType.HARVEST, 2);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 10));

        Instant arrival = attack(attacker, defender, "{\"AXEMAN\":100,\"BANNER_THIEF\":10}");
        arriveAt(arrival);

        assertThat(
                        jdbc.sql(
                                        "select owner_id || ':' ||"
                                                + " coalesce(equipped_village_id::text, '-')"
                                                + " from banner where id = ?")
                                .param(banner)
                                .query(String.class)
                                .single())
                .isEqualTo(attacker.playerId + ":-");
        assertThat(
                        count(
                                "select count(*) from banner_event where banner_id = ? and kind ="
                                        + " 'STOLEN' and from_player_id = ? and to_player_id = ?",
                                banner,
                                defender.playerId,
                                attacker.playerId))
                .isEqualTo(1);

        String report = attacker.newestReport("ATTACK");
        Map<String, Object> theft = JsonPath.read(report, "$.payload.banners.theft");
        assertThat(theft)
                .containsEntry("sent", 10)
                .containsEntry("survived", 10)
                .containsEntry("result", "STOLEN")
                .containsEntry("banner", Map.of("type", "HARVEST", "tier", 2));
        // (1 − 0.97^10) × (1 − 0.1 × hall 1)
        assertThat(((Number) theft.get("chance")).doubleValue())
                .isCloseTo(
                        (1 - Math.pow(0.97, 10)) * 0.9, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                defender.newestReport("DEFENSE"), "$.payload.banners.stolen"))
                .isEqualTo(Map.of("type", "HARVEST", "tier", 2));
        assertThat(sent.toPlayer(defender.playerId))
                .anyMatch(
                        json ->
                                json.contains("\"type\":\"BANNER_STOLEN\"")
                                        && json.contains("\"lost\":true"));
        assertThat(sent.toPlayer(attacker.playerId))
                .anyMatch(
                        json ->
                                json.contains("\"type\":\"BANNER_STOLEN\"")
                                        && json.contains("\"lost\":false"));
        // the thieves are not used up: they walk home with the others
        String troops = body(attacker.troops());
        assertThat(JsonPath.<Integer>read(troops, "$.incoming[0].units.BANNER_THIEF"))
                .isEqualTo(10);
    }

    @Test
    void aFailedRollLeavesTheBannerAndTellsTheChance() throws Exception {
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 3);
        long banner = flyBanner(defender, defender.villageId, BannerType.WAR, 1);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 50));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"BANNER_THIEF\":50}"));

        Map<String, Object> theft =
                JsonPath.read(attacker.newestReport("ATTACK"), "$.payload.banners.theft");
        assertThat(theft)
                .containsEntry("result", "FAILED")
                .containsEntry("banner", Map.of("type", "WAR", "tier", 1));
        // 02 §21.6.2 table: 50 thieves against hall 3 → 55 %
        assertThat(((Number) theft.get("chance")).doubleValue())
                .isCloseTo(0.547, org.assertj.core.data.Offset.offset(0.001));
        assertThat(
                        jdbc.sql("select equipped_village_id from banner where id = ?")
                                .param(banner)
                                .query(Long.class)
                                .single())
                .isEqualTo(defender.villageId);
        assertThat((Object) JsonPath.read(defender.newestReport("DEFENSE"), "$.payload.banners"))
                .isNull();
    }

    @Test
    void thievesOfALostBattleRollNothing() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 1);
        long banner = flyBanner(defender, defender.villageId, BannerType.WAR, 1);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10, UnitType.BANNER_THIEF, 5));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":10,\"BANNER_THIEF\":5}"));

        Map<String, Object> theft =
                JsonPath.read(attacker.newestReport("ATTACK"), "$.payload.banners.theft");
        assertThat(theft).containsEntry("result", "BATTLE_LOST").containsEntry("survived", 0);
        assertThat(theft.get("chance")).isNull();
        assertThat(
                        jdbc.sql("select owner_id from banner where id = ?")
                                .param(banner)
                                .query(Long.class)
                                .single())
                .isEqualTo(defender.playerId);
    }

    @Test
    void aVillageWithoutBannerHasNothingToSteal() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 5));

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"BANNER_THIEF\":5}"));

        assertThat(
                        JsonPath.<String>read(
                                attacker.newestReport("ATTACK"), "$.payload.banners.theft.result"))
                .isEqualTo("NO_BANNER");
    }

    @Test
    void supportersDontLearnTheOwnersBanner() throws Exception {
        // the owner's banner is his business (02 §21.1): a supporter's DEFENSE copy names neither
        // the banner the thieves took nor one the owner found
        dice.use(FixedRandomSource.constant(ALWAYS));
        TestPlayer supporter = TestPlayer.register(mockMvc);
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 1);
        flyBanner(defender, defender.villageId, BannerType.HARVEST, 2);
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100, UnitType.BANNER_THIEF, 10));
        jdbc.sql(
                        "insert into unit_stack (home_village_id, location_village_id, u_spearman)"
                                + " values (?, ?, 5)")
                .params(supporter.villageId, defender.villageId)
                .update();

        arriveAt(attack(attacker, defender, "{\"AXEMAN\":100,\"BANNER_THIEF\":10}"));

        assertThat(
                        JsonPath.<String>read(
                                defender.newestReport("DEFENSE"), "$.payload.banners.stolen.type"))
                .isEqualTo("HARVEST");
        String theirs = supporter.newestReport("DEFENSE");
        assertThat((Object) JsonPath.read(theirs, "$.payload.banners")).isNull();
    }

    @Test
    void thievesAloneFightNoBattleAndComeBack() throws Exception {
        dice.use(FixedRandomSource.constant(ALWAYS));
        setLevel(defender.villageId, BuildingType.BANNER_HALL, 1);
        flyBanner(defender, defender.villageId, BannerType.WAR, 1);
        setUnits(attacker, Map.of(UnitType.BANNER_THIEF, 5));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 50));

        arriveAt(attack(attacker, defender, "{\"BANNER_THIEF\":5}"));

        // attack 0: no main battle, like scouts without scouting (02 §21.5)
        String report = attacker.newestReport("SCOUT");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("SCOUTS_ONLY");
        assertThat(JsonPath.<String>read(report, "$.payload.banners.theft.result"))
                .isEqualTo("BATTLE_LOST");
        assertThat(
                        JsonPath.<Integer>read(
                                body(attacker.troops()), "$.incoming[0].units.BANNER_THIEF"))
                .isEqualTo(5);
    }

    @Test
    void aFarmTemplateTakesNoThieves() throws Exception {
        Coordinates target = coordinates(defender);
        attacker.browser
                .post(
                        attacker.villagePath() + "/farm-targets",
                        "{\"target\":{\"x\":"
                                + target.x()
                                + ",\"y\":"
                                + target.y()
                                + "},\"units\":{\"BANNER_THIEF\":1}}")
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    /** Puts the units at home first (on top of what is there), then sends them. */
    private Instant attack(TestPlayer from, TestPlayer to, String unitsJson) throws Exception {
        return attack(from, coordinates(to), unitsJson);
    }

    private Instant attack(TestPlayer from, Coordinates target, String unitsJson) throws Exception {
        String sentCommand =
                body(from.command("ATTACK", target, unitsJson).andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sentCommand, "$.arriveAt"));
    }

    private long travelSeconds(TestPlayer from, Coordinates target, String unitsJson)
            throws Exception {
        return travelSeconds(from, target, unitsJson, "ATTACK");
    }

    private long travelSeconds(TestPlayer from, Coordinates target, String unitsJson, String kind)
            throws Exception {
        String preview =
                body(from.previewCommand(kind, target, unitsJson).andExpect(status().isOk()));
        return number(preview, "$.travelSeconds").longValue();
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

    /** "TYPE:tier:origin" of every banner the player owns, oldest first. */
    private List<String> bannersOf(TestPlayer player) {
        return jdbc.sql(
                        "select type || ':' || tier || ':' || origin from banner where owner_id ="
                                + " ? order by id")
                .param(player.playerId)
                .query(String.class)
                .list();
    }

    private long flyBanner(TestPlayer owner, long villageId, BannerType type, int tier) {
        return jdbc.sql(
                        """
                        insert into banner (owner_id, type, tier, equipped_village_id, equipped_at,
                                            origin, created_at)
                        values (?, ?, ?, ?, now(), 'GRANTED', now()) returning id
                        """)
                .params(owner.playerId, type.name(), tier, villageId)
                .query(Long.class)
                .single();
    }

    /** A barbarian village with nothing in it, somewhere free. */
    private long barbarianVillage() {
        for (int x = 10; x < TestWorldConfiguration.MAP_SIZE; x += 3) {
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
                            .param("y", BARBARIAN_ROW)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                barbarianVillages.add(id);
                jdbc.sql(
                                "insert into unit_stack (home_village_id, location_village_id)"
                                        + " values (?, ?)")
                        .params(id, id)
                        .update();
                setLevel(id, BuildingType.WAREHOUSE, 1);
                setLevel(id, BuildingType.HEADQUARTERS, 1);
                return id;
            }
        }
        throw new IllegalStateException("no free field for a barbarian village");
    }

    private Coordinates coordinatesOf(long villageId) {
        return jdbc.sql("select x, y from village where id = ?")
                .param(villageId)
                .query((rs, n) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                .single();
    }

    private void setBanners(boolean enabled, double dropFactor) {
        jdbc.sql("update world_settings set banners_enabled = ?, banner_drop_factor = ?")
                .params(enabled, dropFactor)
                .update();
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

    private void setLevel(long villageId, BuildingType building, int level) {
        jdbc.sql("update village set " + column("b_", building) + " = ? where id = ?")
                .params(level, villageId)
                .update();
    }

    private void setStock(TestPlayer player, int amount) {
        setResources(player, amount, clock.instant());
    }

    private void setResources(TestPlayer player, int amount, Instant anchor) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(anchor), player.villageId)
                .update();
    }

    private void endProtection(TestPlayer player) {
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), player.playerId)
                .update();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    private static String column(String prefix, Enum<?> type) {
        return prefix + type.name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Integer> units(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
