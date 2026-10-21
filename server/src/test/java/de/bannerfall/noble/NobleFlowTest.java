package de.bannerfall.noble;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.TestWorldConfiguration;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
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
 * The rising noble price (1.6, 02-game-rules §5.7): the rank, what the Academy offers and charges,
 * the refund of a cancel, and the world setting. Villages of a player beyond the first are inserted
 * by SQL; nobles are put into the home stack by SQL where the test doesn't recruit them.
 */
@IntegrationTest
class NobleFlowTest {

    private static final int MAP_ROW = TestWorldConfiguration.MAP_SIZE - 16;
    private static final int STOCK = 300_000;

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;

    private TestPlayer player;
    private final List<Long> extraVillages = new ArrayList<>();

    @BeforeEach
    void registerPlayer() throws Exception {
        setStep(0.25);
        player = TestPlayer.register(mockMvc);
        // the Academy and what it needs, a farm for a dozen nobles, and a stock the warehouse holds
        setLevel(BuildingType.HEADQUARTERS, 20);
        setLevel(BuildingType.SMITHY, 20);
        setLevel(BuildingType.MARKET, 10);
        setLevel(BuildingType.ACADEMY, 1);
        setLevel(BuildingType.FARM, 30);
        setLevel(BuildingType.WAREHOUSE, 30);
        setStock(STOCK);
    }

    @AfterEach
    void restoreTheWorld() {
        for (long id : extraVillages) {
            jdbc.sql("delete from village where id = ?").param(id).update();
        }
        extraVillages.clear();
        setStep(0.25);
    }

    @Test
    void theFirstFourNoblesCostTheBasePrice() throws Exception {
        player.browser
                .get("/api/noble")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villages").value(1))
                .andExpect(jsonPath("$.nobles").value(0))
                .andExpect(jsonPath("$.freeRanks").value(4))
                .andExpect(jsonPath("$.rank").value(0))
                .andExpect(jsonPath("$.step").value(0.25))
                .andExpect(jsonPath("$.maxFactor").value(7.0))
                .andExpect(jsonPath("$.factor").value(1.0))
                .andExpect(jsonPath("$.baseCost.clay").value(50_000))
                .andExpect(jsonPath("$.cost.clay").value(50_000))
                .andExpect(jsonPath("$.costAfter.clay").value(50_000));
        assertThat(academyNoble("cost")).containsEntry("clay", 50_000);

        player.recruitOk(UnitType.NOBLE, 4);

        assertThat(stock("clay")).isEqualTo(STOCK - 4 * 50_000);
        // a fifth would be rank 1: a quarter dearer
        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.nobles").value(4))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.factor").value(1.25))
                .andExpect(jsonPath("$.cost.clay").value(62_500))
                .andExpect(jsonPath("$.costAfter.clay").value(75_000));
        assertThat(academyNoble("cost")).containsEntry("clay", 62_500);
    }

    @Test
    void everyVillageBeyondTheFirstMakesTheNextNobleDearer() throws Exception {
        setHomeNobles(3);
        addVillages(4);

        // 5 villages + 3 nobles − 4 = rank 4: twice the price
        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.villages").value(5))
                .andExpect(jsonPath("$.rank").value(4))
                .andExpect(jsonPath("$.cost.wood").value(80_000))
                .andExpect(jsonPath("$.cost.clay").value(100_000))
                .andExpect(jsonPath("$.cost.iron").value(100_000));

        player.recruitOk(UnitType.NOBLE, 1);

        assertThat(stock("wood")).isEqualTo(STOCK - 80_000);
        assertThat(stock("clay")).isEqualTo(STOCK - 100_000);
    }

    @Test
    void anOrderOfSeveralPaysTheAverageAndACancelRefundsIt() throws Exception {
        setHomeNobles(4);

        // ranks 1 and 2: 62,500 and 75,000 clay, 68,750 each
        long order = player.recruitOk(UnitType.NOBLE, 2);

        assertThat(stock("clay")).isEqualTo(STOCK - 2 * 68_750);
        player.browser.get("/api/noble").andExpect(jsonPath("$.rank").value(3));

        player.cancelRecruit(order).andExpect(status().isNoContent());

        // refunded in full, and the rank is back
        assertThat(stock("clay")).isEqualTo(STOCK);
        player.browser.get("/api/noble").andExpect(jsonPath("$.rank").value(1));
    }

    @Test
    void theStockMustPayTheRisingPrice() throws Exception {
        setHomeNobles(4);
        // three at ranks 1 … 3 need 62,500 + 75,000 + 87,500 = 225,000 clay
        setStock(224_999);

        assertThat(academyNobleNumber("maxAffordable")).isEqualTo(2);
        player.recruit(UnitType.NOBLE, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        // an absurd count is refused the same way, not worked out
        player.recruit(UnitType.NOBLE, 30_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        assertThat(stock("clay")).isEqualTo(224_999);

        player.recruitOk(UnitType.NOBLE, 2);
    }

    @Test
    void aDeadNobleMakesTheNextOneCheaperAgain() throws Exception {
        setHomeNobles(6);
        player.browser.get("/api/noble").andExpect(jsonPath("$.rank").value(3));

        setHomeNobles(2);

        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.rank").value(0))
                .andExpect(jsonPath("$.cost.clay").value(50_000));
    }

    @Test
    void aLostVillageMakesTheNextNobleCheaperAgain() throws Exception {
        setHomeNobles(3);
        addVillages(3);
        player.browser.get("/api/noble").andExpect(jsonPath("$.rank").value(3));

        // taken by someone else (here: left to the barbarians)
        jdbc.sql("update village set player_id = null where id = ?")
                .param(extraVillages.getFirst())
                .update();

        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.villages").value(3))
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.cost.clay").value(75_000));
    }

    @Test
    void stepZeroIsTheFlatPrice() throws Exception {
        setStep(0);
        setHomeNobles(10);
        addVillages(3);

        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.rank").value(10))
                .andExpect(jsonPath("$.factor").value(1.0))
                .andExpect(jsonPath("$.cost.clay").value(50_000));
        player.recruitOk(UnitType.NOBLE, 2);
        assertThat(stock("clay")).isEqualTo(STOCK - 2 * 50_000);
    }

    @Test
    void thePriceStopsAtSevenTimes() throws Exception {
        setStep(1);
        setHomeNobles(20);

        player.browser
                .get("/api/noble")
                .andExpect(jsonPath("$.factor").value(7.0))
                .andExpect(jsonPath("$.cost.clay").value(350_000))
                .andExpect(jsonPath("$.costAfter.clay").value(350_000));
    }

    @Test
    void theWorldSettingsShowTheStep() throws Exception {
        player.browser.get("/api/world").andExpect(jsonPath("$.noblePriceStep").value(0.25));
    }

    // ---- helpers ----

    private Map<String, Integer> academyNoble(String field) throws Exception {
        List<Map<String, Integer>> values =
                JsonPath.read(body(player.recruitment()), academyNoblePath(field));
        return values.getFirst();
    }

    private int academyNobleNumber(String field) throws Exception {
        List<Integer> values = JsonPath.read(body(player.recruitment()), academyNoblePath(field));
        return values.getFirst();
    }

    private static String academyNoblePath(String field) {
        return "$[?(@.building == 'ACADEMY')].units[?(@.unit == 'NOBLE')]." + field;
    }

    private void setStep(double step) {
        jdbc.sql("update world_settings set noble_price_step = ?").param(step).update();
        worldService.reload();
    }

    private void setLevel(BuildingType building, int level) {
        jdbc.sql(
                        "update village set b_"
                                + building.name().toLowerCase(Locale.ROOT)
                                + " = ? where id = ?")
                .params(level, player.villageId)
                .update();
    }

    private void setStock(int amount) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?, resources_updated_at = ?"
                                + " where id = ?")
                .params(amount, amount, amount, Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private int stock(String resource) {
        return jdbc.sql("select floor(" + resource + ")::int from village where id = ?")
                .param(player.villageId)
                .query(Integer.class)
                .single();
    }

    private void setHomeNobles(int count) {
        jdbc.sql(
                        "update unit_stack set u_noble = ? where home_village_id = ?"
                                + " and location_village_id = ?")
                .params(count, player.villageId, player.villageId)
                .update();
    }

    /** Gives the player more villages in a row near the bottom of the map. */
    private void addVillages(int count) {
        int added = 0;
        for (int x = 20; added < count && x < TestWorldConfiguration.MAP_SIZE; x++) {
            Long id =
                    jdbc.sql(
                                    """
                                    insert into village (player_id, name, x, y, wood, clay, iron,
                                                         resources_updated_at, loyalty_updated_at,
                                                         points, created_at)
                                    values (:player, 'extra', :x, :y, 0, 0, 0, now(), now(), 0, now())
                                    on conflict (x, y) do nothing
                                    returning id
                                    """)
                            .param("player", player.playerId)
                            .param("x", x)
                            .param("y", MAP_ROW)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            if (id != null) {
                added++;
                extraVillages.add(id);
            }
        }
        assertThat(added).isEqualTo(count);
    }
}
