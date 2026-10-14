package de.bannerfall.market;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.barbarian.BarbarianService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventCleanupJob;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.rules.Merchants;
import de.bannerfall.rules.Travel;
import de.bannerfall.village.OwnerChange;
import de.bannerfall.world.WorldService;
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
 * The market (M13a) between two registered players: merchants, sending resources, offers and
 * accepting them. Both villages get a level 5 market (5 merchants), a level 10 warehouse (6397 per
 * resource) and 5000 of each resource; the clock is moved by hand.
 */
@IntegrationTest
class MarketFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired EventProcessor processor;
    @Autowired EventCleanupJob cleanup;
    @Autowired WorldService worldService;
    @Autowired OwnerChange ownerChange;
    @Autowired BarbarianService barbarians;
    @Autowired PlatformTransactionManager transactionManager;

    private TestPlayer alice;
    private TestPlayer bob;
    private Coordinates aliceAt;
    private Coordinates bobAt;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from trade_offer").update();
        jdbc.sql("delete from trade_transport").update();
        alice = TestPlayer.register(mockMvc);
        bob = TestPlayer.register(mockMvc);
        aliceAt = coordinates(alice);
        bobAt = coordinates(bob);
        prepare(alice);
        prepare(bob);
    }

    @AfterEach
    void leaveNoEventsBehind() {
        jdbc.sql("delete from game_event").update();
        // trade_transport has no cascade: other test classes that delete villages must not trip
        jdbc.sql("delete from trade_offer").update();
        jdbc.sql("delete from trade_transport").update();
    }

    // ---- merchants ----

    @Test
    void theMarketShowsMerchantsOfTheMarketLevel() throws Exception {
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchants.total").value(5))
                .andExpect(jsonPath("$.merchants.free").value(5))
                .andExpect(jsonPath("$.outgoing", hasSize(0)))
                .andExpect(jsonPath("$.incoming", hasSize(0)))
                .andExpect(jsonPath("$.offers", hasSize(0)));
        jdbc.sql("update village set b_market = 0 where id = ?").params(alice.villageId).update();
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.total").value(0))
                .andExpect(jsonPath("$.merchants.free").value(0));
    }

    @Test
    void aForeignVillageHasNoMarketForYou() throws Exception {
        alice.browser
                .get(bob.villagePath() + "/market")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
    }

    // ---- sending ----

    @Test
    void sendingTakesTheResourcesAtOnceAndBusiesTheMerchantsUntilTheyAreBack() throws Exception {
        Instant now = clock.instant();
        long seconds = travelSeconds();

        String sent =
                send(alice, bobAt, 1200, 0, 600).andReturn().getResponse().getContentAsString();

        assertThat(number(sent, "$.merchants").intValue()).isEqualTo(Merchants.needed(1800));
        assertThat(instant(sent, "$.arriveAt")).isEqualTo(now.plusSeconds(seconds));
        assertThat(instant(sent, "$.returnAt")).isEqualTo(now.plusSeconds(2 * seconds));
        assertThat(stock(alice, "wood")).isEqualTo(3800);
        assertThat(stock(alice, "iron")).isEqualTo(4400);
        bob.browser
                .get(bob.villagePath() + "/market")
                .andExpect(jsonPath("$.incoming", hasSize(1)))
                .andExpect(jsonPath("$.incoming[0].resources.wood").value(1200));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(3))
                .andExpect(jsonPath("$.outgoing", hasSize(1)));

        // arrival: the resources are there, a report, merchants still away
        clock.set(now.plusSeconds(seconds));
        processor.processDue(clock.instant());
        assertStockAbout(bob, "wood", 6200);
        assertStockAbout(bob, "iron", 5600);
        String report = bob.newestReport("TRADE_ARRIVED");
        assertThat(number(report, "$.payload.resources.wood").intValue()).isEqualTo(1200);
        assertThat(number(report, "$.payload.from.id").longValue()).isEqualTo(alice.villageId);
        // production while the merchants walked may have eaten some of the room: a little is lost
        assertThat(number(report, "$.payload.lost.wood").intValue())
                .isBetween(0, productionOverALeg(bob, "wood"));
        assertThat(alice.reportKinds()).doesNotContain("TRADE_ARRIVED");
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(3))
                .andExpect(jsonPath("$.outgoing[0].arrived").value(true));
        bob.browser
                .get(bob.villagePath() + "/market")
                .andExpect(jsonPath("$.incoming", hasSize(0)));

        // one second before they are home: still busy; at returnAt: free
        clock.set(now.plusSeconds(2 * seconds - 1));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(3));
        clock.set(now.plusSeconds(2 * seconds));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(5))
                .andExpect(jsonPath("$.outgoing", hasSize(0)));
    }

    @Test
    void whatDoesNotFitIntoTheWarehouseIsLost() throws Exception {
        setStock(bob, 6000, 6000, 6000);
        send(alice, bobAt, 1000, 0, 0).andExpect(status().isCreated());
        clock.advance(java.time.Duration.ofSeconds(travelSeconds()));
        processor.processDue(clock.instant());
        // capacity of a level 10 warehouse is 6397; production in between is at most a little
        assertThat(stock(bob, "wood")).isEqualTo(6397);
        // the report says what was lost: 1000 minus the room that was left (at most 397)
        String report = bob.newestReport("TRADE_ARRIVED");
        assertThat(number(report, "$.payload.lost.wood").intValue()).isBetween(603, 1000);
        assertThat(number(report, "$.payload.lost.clay").intValue()).isZero();
    }

    @Test
    void sendingChecksMerchantsResourcesAndTarget() throws Exception {
        // 6 merchants' worth with 5 merchants
        setStock(alice, 6397, 6397, 6397);
        send(alice, bobAt, 3000, 3000, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_MERCHANTS"));
        setStock(alice, 100, 0, 0);
        send(alice, bobAt, 101, 0, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        send(alice, bobAt, 0, 0, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_TRANSPORT"));
        send(alice, aliceAt, 10, 0, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_VILLAGE"));
        send(alice, freeField(), 10, 0, 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_FOUND"));
        send(alice, bobAt, -1, 0, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        // nothing was paid for the refused ones
        assertThat(stock(alice, "wood")).isEqualTo(100);
    }

    @Test
    void sendingToABarbarianVillageIsAllowed() throws Exception {
        Coordinates free = freeField();
        barbarians.spawnAt(free);
        long transportId = id(body(send(alice, free, 100, 0, 0).andExpect(status().isCreated())));
        clock.advance(java.time.Duration.ofDays(1));
        processor.processDue(clock.instant());
        assertThat(
                        jdbc.sql("select arrived from trade_transport where id = ?")
                                .params(transportId)
                                .query(Boolean.class)
                                .single())
                .isTrue();
        assertThat(alice.reportKinds()).doesNotContain("TRADE_ARRIVED");
    }

    @Test
    void whoOwnsTheTargetWhenTheMerchantsArriveGetsTheReport() throws Exception {
        TestPlayer carol = TestPlayer.register(mockMvc);
        send(alice, bobAt, 100, 0, 0).andExpect(status().isCreated());
        // bob's village changes hands while the merchants are on the way
        jdbc.sql("update village set player_id = ? where id = ?")
                .params(carol.playerId, bob.villageId)
                .update();
        clock.advance(java.time.Duration.ofSeconds(travelSeconds()));
        processor.processDue(clock.instant());
        String report = carol.newestReport("TRADE_ARRIVED");
        assertThat(number(report, "$.payload.to.id").longValue()).isEqualTo(bob.villageId);
        assertThat(bob.reportKinds()).doesNotContain("TRADE_ARRIVED");
    }

    // ---- offers ----

    @Test
    void anOfferTakesItsResourcesAndReservesItsMerchants() throws Exception {
        // 2 × (1500 wood → two merchants each) = 4 merchants, 3000 wood
        String offer = createOffer(alice, "WOOD", 1500, "IRON", 1000, 2);

        assertThat(number(offer, "$.remainingCount").intValue()).isEqualTo(2);
        assertThat(stock(alice, "wood")).isEqualTo(2000);
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(1))
                .andExpect(jsonPath("$.offers", hasSize(1)))
                .andExpect(jsonPath("$.offers[0].wantResource").value("IRON"));
        // a third unit needs 2 more merchants than the 1 that are free
        offerRequest(alice, "WOOD", 1500, "IRON", 1000, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_MERCHANTS"));
    }

    @Test
    void anOfferNeedsAnotherResourceAndAReasonableRatio() throws Exception {
        offerRequest(alice, "WOOD", 100, "WOOD", 100, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_RESOURCE"));
        offerRequest(alice, "WOOD", 100, "IRON", 301, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RATIO"));
        offerRequest(alice, "WOOD", 301, "IRON", 100, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RATIO"));
        offerRequest(alice, "WOOD", 100, "IRON", 300, 1).andExpect(status().isCreated());
        offerRequest(alice, "WOOD", 100, "IRON", 100, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        setStock(alice, 99, 0, 0);
        offerRequest(alice, "WOOD", 100, "IRON", 100, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
    }

    @Test
    void cancellingRefundsTheRestAndFreesTheMerchants() throws Exception {
        long offerId = id(createOffer(alice, "WOOD", 1000, "CLAY", 1000, 3));
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 1))
                .andExpect(status().isCreated());
        assertThat(stock(alice, "wood")).isEqualTo(2000);

        alice.browser.delete("/api/offers/" + offerId).andExpect(status().isNoContent());

        // 2 of 3 units were left: 2000 wood back
        assertThat(stock(alice, "wood")).isEqualTo(4000);
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.offers", hasSize(0)))
                // 1 merchant is still away with the accepted unit
                .andExpect(jsonPath("$.merchants.free").value(4));
        alice.browser
                .delete("/api/offers/" + offerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    @Test
    void aRefundIsCappedAtTheWarehouse() throws Exception {
        long offerId = id(createOffer(alice, "WOOD", 1000, "CLAY", 1000, 3));
        setStock(alice, 6000, 0, 0);
        alice.browser.delete("/api/offers/" + offerId).andExpect(status().isNoContent());
        assertThat(stock(alice, "wood")).isEqualTo(6397);
    }

    @Test
    void nobodyCancelsAForeignOffer() throws Exception {
        long offerId = id(createOffer(alice, "WOOD", 100, "CLAY", 100, 1));
        bob.browser
                .delete("/api/offers/" + offerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.offers", hasSize(1)));
    }

    @Test
    void theOfferListShowsOthersOffersNearestFirstWithTravelTime() throws Exception {
        TestPlayer carol = TestPlayer.register(mockMvc);
        prepare(carol);
        createOffer(alice, "WOOD", 100, "CLAY", 100, 1);
        createOffer(carol, "IRON", 100, "CLAY", 100, 1);
        createOffer(bob, "CLAY", 100, "WOOD", 100, 1);

        String list = body(bob.browser.get("/api/offers?fromVillageId=" + bob.villageId));

        List<Number> villages = JsonPath.read(list, "$[*].village.id");
        assertThat(villages)
                .extracting(Number::longValue)
                .containsExactlyInAnyOrder(alice.villageId, carol.villageId);
        List<Double> distances = JsonPath.read(list, "$[*].distance");
        assertThat(distances).isSorted();
        double first = distances.getFirst();
        List<Number> seconds = JsonPath.read(list, "$[*].travelSeconds");
        assertThat(seconds.getFirst().longValue())
                .isEqualTo(Merchants.travelSeconds(first, worldService.settings()));
        // someone else's village as the origin is not allowed
        bob.browser
                .get("/api/offers?fromVillageId=" + alice.villageId)
                .andExpect(status().isNotFound());
    }

    // ---- accepting ----

    @Test
    void acceptingPartOfAnOfferStartsTwoTransports() throws Exception {
        Instant now = clock.instant();
        long offerId = id(createOffer(alice, "WOOD", 1000, "IRON", 800, 3));

        String result =
                body(
                        bob.browser
                                .post("/api/offers/" + offerId + "/accept", accept(bob, 2))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$", hasSize(2))));

        // [0]: alice → bob with 2 × 1000 wood (2 merchants of the offer), [1]: bob → alice
        assertThat(number(result, "$[0].sourceVillage.id").longValue()).isEqualTo(alice.villageId);
        assertThat(number(result, "$[0].resources.wood").intValue()).isEqualTo(2000);
        assertThat(number(result, "$[0].merchants").intValue()).isEqualTo(2);
        assertThat(number(result, "$[1].sourceVillage.id").longValue()).isEqualTo(bob.villageId);
        assertThat(number(result, "$[1].resources.iron").intValue()).isEqualTo(1600);
        assertThat(number(result, "$[1].merchants").intValue()).isEqualTo(2);
        // bob paid at once; the offer has 1 left
        assertThat(stock(bob, "iron")).isEqualTo(3400);
        bob.browser
                .get(bob.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(3));
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.offers[0].remainingCount").value(1))
                // 2 away + 1 reserved by what is left of the offer
                .andExpect(jsonPath("$.merchants.free").value(2));

        clock.set(now.plusSeconds(travelSeconds()));
        processor.processDue(clock.instant());
        assertThat(stock(bob, "wood")).isEqualTo(6397);
        assertThat(stock(alice, "iron")).isEqualTo(6397);
        assertThat(alice.reportKinds()).contains("TRADE_ARRIVED");
        assertThat(bob.reportKinds()).contains("TRADE_ARRIVED");
    }

    @Test
    void anOfferAcceptedToTheEndIsGone() throws Exception {
        long offerId = id(createOffer(alice, "WOOD", 1000, "IRON", 1000, 2));
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 2))
                .andExpect(status().isCreated());
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.offers", hasSize(0)));
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    @Test
    void acceptingChecksOwnerCountResourcesAndMerchants() throws Exception {
        long offerId = id(createOffer(alice, "WOOD", 1000, "IRON", 1000, 2));
        alice.browser
                .post("/api/offers/" + offerId + "/accept", accept(alice, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_OFFER"));
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_EXHAUSTED"));
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 0))
                .andExpect(status().isBadRequest());
        // a village that is not bob's
        bob.browser
                .post(
                        "/api/offers/" + offerId + "/accept",
                        "{\"villageId\":" + alice.villageId + ",\"count\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VILLAGE_NOT_FOUND"));
        setStock(bob, 0, 0, 999);
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_RESOURCES"));
        setStock(bob, 0, 0, 5000);
        jdbc.sql("update village set b_market = 0 where id = ?").params(bob.villageId).update();
        bob.browser
                .post("/api/offers/" + offerId + "/accept", accept(bob, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_MERCHANTS"));
        // nothing happened to the offer
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.offers[0].remainingCount").value(2));
    }

    @Test
    void aConqueredVillagesOffersAreGoneAndItsMerchantsAreFree() throws Exception {
        createOffer(alice, "WOOD", 1000, "IRON", 1000, 2);
        send(alice, bobAt, 100, 0, 0).andExpect(status().isCreated());

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            ownerChange.dropOrders(alice.villageId, clock.instant());
                        });

        assertThat(
                        jdbc.sql("select count(*) from trade_offer where village_id = ?")
                                .params(alice.villageId)
                                .query(Long.class)
                                .single())
                .isZero();
        // its merchants were the old owner's: no longer busy here, no longer listed (D8)
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(5))
                .andExpect(jsonPath("$.outgoing", hasSize(0)));
        // the transport on its way still arrives
        clock.advance(java.time.Duration.ofSeconds(travelSeconds()));
        processor.processDue(clock.instant());
        assertStockAbout(bob, "wood", 5100);
    }

    @Test
    void theNightlyCleanupDeletesTransportsOnceTheMerchantsAreBack() throws Exception {
        send(alice, bobAt, 100, 0, 0).andExpect(status().isCreated());
        clock.advance(java.time.Duration.ofSeconds(travelSeconds()));
        processor.processDue(clock.instant());
        assertThat(cleanup.deleteFinishedTransports()).isZero();
        clock.advance(java.time.Duration.ofSeconds(travelSeconds()));
        assertThat(cleanup.deleteFinishedTransports()).isGreaterThanOrEqualTo(1);
        alice.browser
                .get(alice.villagePath() + "/market")
                .andExpect(jsonPath("$.merchants.free").value(5));
    }

    // ---- helpers ----

    private ResultActions send(TestPlayer from, Coordinates to, int wood, int clay, int iron)
            throws Exception {
        return from.browser.post(
                from.villagePath() + "/transports",
                "{\"target\":{\"x\":%d,\"y\":%d},\"wood\":%d,\"clay\":%d,\"iron\":%d}"
                        .formatted(to.x(), to.y(), wood, clay, iron));
    }

    private ResultActions offerRequest(
            TestPlayer from,
            String offerResource,
            int offerAmount,
            String wantResource,
            int wantAmount,
            int count)
            throws Exception {
        return from.browser.post(
                from.villagePath() + "/offers",
                ("{\"offerResource\":\"%s\",\"offerAmount\":%d,\"wantResource\":\"%s\","
                                + "\"wantAmount\":%d,\"count\":%d}")
                        .formatted(offerResource, offerAmount, wantResource, wantAmount, count));
    }

    private String createOffer(
            TestPlayer from,
            String offerResource,
            int offerAmount,
            String wantResource,
            int wantAmount,
            int count)
            throws Exception {
        return body(
                offerRequest(from, offerResource, offerAmount, wantResource, wantAmount, count)
                        .andExpect(status().isCreated()));
    }

    private static String accept(TestPlayer who, int count) {
        return "{\"villageId\":%d,\"count\":%d}".formatted(who.villageId, count);
    }

    /** Market 5, warehouse 10, 5000 of each resource, the stock anchored at the clock. */
    private void prepare(TestPlayer player) {
        jdbc.sql("update village set b_market = 5, b_warehouse = 10 where id = ?")
                .params(player.villageId)
                .update();
        setStock(player, 5000, 5000, 5000);
    }

    private void setStock(TestPlayer player, double wood, double clay, double iron) {
        jdbc.sql(
                        "update village set wood = ?, clay = ?, iron = ?,"
                                + " resources_updated_at = ? where id = ?")
                .params(
                        wood,
                        clay,
                        iron,
                        java.sql.Timestamp.from(clock.instant()),
                        player.villageId)
                .update();
    }

    /** The stock as the API shows it (whole amounts). */
    private int stock(TestPlayer player, String resource) throws Exception {
        return number(body(player.village()), "$.resources." + resource).intValue();
    }

    /**
     * Production while the merchants walked adds to the stock: between the amount and one leg of
     * production more. How much that is depends on the distance, and the test villages are placed
     * at random, so the margin is taken from the village's own production rate, never a constant.
     */
    private void assertStockAbout(TestPlayer player, String resource, int expected)
            throws Exception {
        assertThat(stock(player, resource))
                .isBetween(expected, expected + productionOverALeg(player, resource));
    }

    /** What the village produces of the resource in one trip of the merchants, rounded up. */
    private int productionOverALeg(TestPlayer player, String resource) throws Exception {
        double perHour = number(body(player.village()), "$.production." + resource).doubleValue();
        return (int) Math.ceil(perHour * travelSeconds() / 3600.0) + 1;
    }

    private long travelSeconds() {
        return Merchants.travelSeconds(Travel.distance(aliceAt, bobAt), worldService.settings());
    }

    private Coordinates freeField() {
        for (int x = 99; x > 0; x--) {
            Long taken =
                    jdbc.sql("select count(*) from village where x = ? and y = 99")
                            .params(x)
                            .query(Long.class)
                            .single();
            if (taken == 0) {
                return new Coordinates(x, 99);
            }
        }
        throw new IllegalStateException("no free field");
    }

    private static Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                number(village, "$.x").intValue(), number(village, "$.y").intValue());
    }

    private static long id(String json) {
        return number(json, "$.id").longValue();
    }

    private static Instant instant(String json, String path) {
        return Instant.parse(JsonPath.read(json, path));
    }
}
