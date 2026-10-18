package de.bannerfall.combat;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
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
 * The night bonus in real battles (1.4, 02-game-rules §8.1). Luck is off. Each test sends the
 * attack first and then puts the night window around its arrival (or away from it): the battle
 * reads the settings at the arrival, like a host changing them while the attack walks.
 */
@IntegrationTest
class NightBonusFlowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final int BARBARIAN_ROW = TestWorldConfiguration.MAP_SIZE - 17;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired WorldService worldService;

    private WorldSettings before;
    private TestPlayer attacker;
    private TestPlayer defender;
    private final List<Long> barbarianVillages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        before = worldService.settings();
        jdbc.sql("update world_settings set luck_range = 0").update();
        worldService.reload();
        attacker = TestPlayer.register(mockMvc);
        defender = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), defender.playerId)
                .update();
    }

    @AfterEach
    void cleanUp() {
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
        jdbc.sql(
                        """
                        update world_settings set luck_range = ?, night_bonus_enabled = ?,
                            night_bonus_start_hour = ?, night_bonus_end_hour = ?,
                            night_bonus_factor = ?
                        """)
                .params(
                        before.luckRange(),
                        before.nightBonusEnabled(),
                        before.nightBonusStartHour(),
                        before.nightBonusEndHour(),
                        before.nightBonusFactor())
                .update();
        worldService.reload();
    }

    @Test
    void aNightAttackMeetsTheDoubledDefenceAndBothReportsSaySo() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 50));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        // A = 2000; D = 1500 + 20 = 1520 by day, 3040 at night
        Instant arrival = attack(coordinates(defender), "{\"AXEMAN\":50}");
        nightAround(arrival, true);

        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("DEFENDER_WON");
        assertThat(JsonPath.<Double>read(report, "$.payload.nightBonus")).isEqualTo(2.0);
        String defense = defender.newestReport("DEFENSE");
        assertThat(JsonPath.<Double>read(defense, "$.payload.nightBonus")).isEqualTo(2.0);
        // (2000 / 3040)^1.5 = 0.5337 of the spearmen die
        assertThat(JsonPath.<Integer>read(defense, "$.payload.defenderSide.losses.SPEARMAN"))
                .isEqualTo(53);
    }

    @Test
    void byDayAndWithTheSwitchOffNothingChanges() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 100));
        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        Coordinates target = coordinates(defender);

        Instant byDay = attack(target, "{\"AXEMAN\":50}");
        nightAway(byDay);
        arriveAt(byDay);
        String day = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(day, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(day, "$.payload.nightBonus")).isNull();

        setUnits(defender, Map.of(UnitType.SPEARMAN, 100));
        Instant switchedOff = attack(target, "{\"AXEMAN\":50}");
        nightAround(switchedOff, false);
        arriveAt(switchedOff);
        String off = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(off, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(off, "$.payload.nightBonus")).isNull();
    }

    @Test
    void barbarianVillagesNeverGetIt() throws Exception {
        setUnits(attacker, Map.of(UnitType.AXEMAN, 10));
        long barbarian = barbarianVillage();
        // A = 40 against the base defence 20: a tie (the defender's) if it were doubled
        Instant arrival = attack(coordinatesOf(barbarian), "{\"AXEMAN\":1}");
        nightAround(arrival, true);

        arriveAt(arrival);

        String report = attacker.newestReport("ATTACK");
        assertThat(JsonPath.<String>read(report, "$.payload.outcome")).isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(report, "$.payload.nightBonus")).isNull();
    }

    @Test
    void scoutsAloneFightNoMainBattleAndShowNoNightBonus() throws Exception {
        setUnits(attacker, Map.of(UnitType.SCOUT, 5));
        Instant arrival = attack(coordinates(defender), "{\"SCOUT\":5}");
        nightAround(arrival, true);

        arriveAt(arrival);

        String report = attacker.newestReport("SCOUT");
        assertThat((Object) JsonPath.read(report, "$.payload.nightBonus")).isNull();
    }

    // ---- helpers ----

    /** The night window is the hour of {@code arrival} (German time). */
    private void nightAround(Instant arrival, boolean enabled) {
        int hour = arrival.atZone(BERLIN).getHour();
        setNight(enabled, hour, (hour + 1) % 24);
    }

    /** The night window is two hours after the hour of {@code arrival}, switched on. */
    private void nightAway(Instant arrival) {
        int hour = arrival.atZone(BERLIN).getHour();
        setNight(true, (hour + 2) % 24, (hour + 3) % 24);
    }

    private void setNight(boolean enabled, int start, int end) {
        jdbc.sql(
                        """
                        update world_settings set night_bonus_enabled = ?,
                            night_bonus_start_hour = ?, night_bonus_end_hour = ?,
                            night_bonus_factor = 2.0
                        """)
                .params(enabled, start, end)
                .update();
        worldService.reload();
    }

    private Instant attack(Coordinates target, String unitsJson) throws Exception {
        String sent =
                body(attacker.command("ATTACK", target, unitsJson).andExpect(status().isCreated()));
        return Instant.parse(JsonPath.read(sent, "$.arriveAt"));
    }

    private void arriveAt(Instant at) {
        clock.set(at);
        processor.processDue(at);
    }

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
                jdbc.sql("update village set b_warehouse = 1, b_headquarters = 1 where id = ?")
                        .param(id)
                        .update();
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

    /** The home stack has exactly these units. */
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

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }
}
