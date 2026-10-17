package de.bannerfall.market;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.rules.MarketExchange;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Trading with the market itself (v1.3, 02-game-rules §12.1). The village gets a level 5 market (a
 * cap of 5000 × speed per resource and day), a level 20 warehouse and 5000 of each resource; the
 * clock is moved by hand, so the village produces nothing between two calls.
 */
@IntegrationTest
class MarketExchangeFlowTest {

    private static final ZoneId DAY_ZONE = ZoneId.of("Europe/Berlin");

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;

    private TestPlayer alice;
    private int cap;

    @BeforeEach
    void setUp() throws Exception {
        alice = TestPlayer.register(mockMvc);
        jdbc.sql("update village set b_market = 5, b_warehouse = 20 where id = ?")
                .params(alice.villageId)
                .update();
        setStock(5000, 5000, 5000);
        cap = MarketExchange.dailyCap(5, worldService.settings().speed());
    }

    @AfterEach
    void restoreTheRatio() {
        setRatio(1.5);
    }

    @Test
    void theMarketShowsTheRatioTheCapAndWhenTheDayEnds() throws Exception {
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchange.ratio").value(1.5))
                .andExpect(jsonPath("$.exchange.dailyCap").value(cap))
                .andExpect(jsonPath("$.exchange.received.wood").value(0))
                .andExpect(jsonPath("$.exchange.received.iron").value(0))
                .andExpect(
                        jsonPath("$.exchange.resetsAt")
                                .value(
                                        MarketExchange.nextReset(clock.instant(), DAY_ZONE)
                                                .toString()));
    }

    @Test
    void aTradeTakesOneAndAHalfAndGivesTheRestAtOnce() throws Exception {
        exchange("WOOD", "IRON", 1000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received.iron").value(1000))
                .andExpect(jsonPath("$.received.wood").value(0));

        assertThat(stock("wood")).isEqualTo(3500);
        assertThat(stock("iron")).isEqualTo(6000);
        assertThat(stock("clay")).isEqualTo(5000);
        // an odd amount rounds the cost up
        exchange("CLAY", "IRON", 1).andExpect(status().isOk());
        assertThat(stock("clay")).isEqualTo(4998);
    }

    @Test
    void theCapCountsWhatTheVillageGetsPerResource() throws Exception {
        setStock(50_000, 1000, 0);
        exchange("WOOD", "IRON", cap - 100).andExpect(status().isOk());

        exchange("CLAY", "IRON", 101)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCHANGE_LIMIT"));
        exchange("CLAY", "IRON", 100)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received.iron").value(cap));
        // the other resources have their own cap
        exchange("WOOD", "CLAY", cap).andExpect(status().isOk());
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.exchange.received.iron").value(cap))
                .andExpect(jsonPath("$.exchange.received.clay").value(cap))
                .andExpect(jsonPath("$.exchange.received.wood").value(0));
    }

    @Test
    void aNewDayStartsAtMidnight() throws Exception {
        setStock(50_000, 50_000, 0);
        exchange("WOOD", "IRON", cap).andExpect(status().isOk());
        Instant reset = MarketExchange.nextReset(clock.instant(), DAY_ZONE);

        clock.set(reset.minusSeconds(1));
        exchange("WOOD", "IRON", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCHANGE_LIMIT"));

        clock.set(reset);
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.exchange.received.iron").value(0));
        exchange("WOOD", "IRON", cap).andExpect(status().isOk());
        // the trade of the day before is gone
        assertThat(
                        jdbc.sql("select count(*) from market_exchange where village_id = ?")
                                .param(alice.villageId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void withoutMarketThereIsNothingToGet() throws Exception {
        jdbc.sql("update village set b_market = 0 where id = ?").param(alice.villageId).update();
        exchange("WOOD", "IRON", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCHANGE_LIMIT"));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.exchange.dailyCap").value(0));
    }

    @Test
    void theTradeNeedsTheResourcesAndRoomInTheWarehouse() throws Exception {
        setStock(1499, 5000, 5000);
        exchange("WOOD", "IRON", 1000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));

        int capacity = number(body(alice.village()), "$.storageCapacity").intValue();
        setStock(50_000, 50_000, capacity - 10);
        exchange("WOOD", "IRON", 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_FULL"));
        exchange("WOOD", "IRON", 10).andExpect(status().isOk());
        // nothing was paid for the refused trades
        assertThat(stock("wood")).isEqualTo(Math.min(50_000, capacity) - 15);
    }

    @Test
    void sameResourcesOffWorldsAndBadAmountsAreRefused() throws Exception {
        exchange("WOOD", "WOOD", 100)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_RESOURCE"));
        exchange("WOOD", "IRON", 0).andExpect(status().isBadRequest());

        setRatio(0);
        exchange("WOOD", "IRON", 100)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCHANGE_OFF"));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.exchange.ratio").value(0.0));
        assertThat(stock("wood")).isEqualTo(5000);
    }

    @Test
    void theRatioComesFromTheWorld() throws Exception {
        setRatio(2);
        alice.browser
                .get("/api/world")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketExchangeRatio").value(2.0));
        exchange("CLAY", "WOOD", 500).andExpect(status().isOk());
        assertThat(stock("clay")).isEqualTo(4000);
        assertThat(stock("wood")).isEqualTo(5500);
    }

    @Test
    void aForeignVillageCantTrade() throws Exception {
        TestPlayer bob = TestPlayer.register(mockMvc);
        bob.browser
                .post(alice.villagePath() + "/market/exchange", request("WOOD", "IRON", 100))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    private ResultActions exchange(String give, String receive, int amount) throws Exception {
        return alice.browser.post(
                alice.villagePath() + "/market/exchange", request(give, receive, amount));
    }

    private static String request(String give, String receive, int amount) {
        return """
        {"give":"%s","receive":"%s","amount":%d}
        """
                .formatted(give, receive, amount);
    }

    private void setRatio(double ratio) {
        jdbc.sql("update world_settings set market_exchange_ratio = ? where id = 1")
                .param(ratio)
                .update();
        worldService.reload();
    }

    private void setStock(double wood, double clay, double iron) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?,"
                                + " resources_updated_at = ? where id = ?")
                .params(wood, clay, iron, java.sql.Timestamp.from(clock.instant()), alice.villageId)
                .update();
    }

    private int stock(String resource) throws Exception {
        return number(body(alice.village()), "$.resources." + resource).intValue();
    }
}
