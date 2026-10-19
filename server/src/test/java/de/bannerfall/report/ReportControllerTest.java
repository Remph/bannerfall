package de.bannerfall.report;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.report.BattleReport.BannerReport;
import de.bannerfall.report.BattleReport.ScoutedBanner;
import de.bannerfall.report.BattleReport.TheftReport;
import de.bannerfall.village.VillageRefDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The report endpoints (04-api <i>Reports</i>): list, open, delete, read all, the unread counter;
 * only the player's own reports. The reports are created through {@link ReportService} directly;
 * battles and supports that create them are tested in BattleFlowTest and CommandFlowTest.
 */
@IntegrationTest
class ReportControllerTest {

    private static final VillageRefDto BEN = new VillageRefDto(1, "Benhausen", 48, 52, 7L, "Ben");
    private static final VillageRefDto TOM = new VillageRefDto(2, "Tomburg", 50, 50, 8L, "Tom");
    private static final VillageRefDto BARBARIANS =
            new VillageRefDto(3, "Barbarendorf", 54, 65, null, null);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired ReportService reportService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcClient jdbc;

    private TestPlayer player;
    private TestPlayer other;

    @BeforeEach
    void setUp() throws Exception {
        player = TestPlayer.register(mockMvc);
        other = TestPlayer.register(mockMvc);
    }

    @Test
    void theListShowsTheNewestFirstWithSummariesAndCounts() throws Exception {
        Instant now = clock.instant();
        create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(300), support(), player);
        create(ReportKind.ATTACK, now.minusSeconds(100), battle(), player);
        create(ReportKind.DEFENSE, now.minusSeconds(200), battle(), player, other);

        String list = body(player.reports().andExpect(status().isOk()));

        assertThat(JsonPath.<List<String>>read(list, "$.items[*].kind"))
                .containsExactly("ATTACK", "DEFENSE", "SUPPORT_ARRIVED");
        assertThat(number(list, "$.total").intValue()).isEqualTo(3);
        assertThat(number(list, "$.unread").intValue()).isEqualTo(3);
        assertThat(number(list, "$.page").intValue()).isZero();
        assertThat(number(list, "$.size").intValue()).isEqualTo(25);
        assertThat(JsonPath.<Boolean>read(list, "$.items[0].read")).isFalse();
        assertThat(Instant.parse(JsonPath.read(list, "$.items[0].happenedAt")))
                .isEqualTo(now.minusSeconds(100));
        assertThat(JsonPath.<String>read(list, "$.items[0].summary.from.name"))
                .isEqualTo("Benhausen");
        assertThat(JsonPath.<String>read(list, "$.items[0].summary.to.playerName"))
                .isEqualTo("Tom");
        assertThat(JsonPath.<String>read(list, "$.items[0].summary.outcome"))
                .isEqualTo("ATTACKER_WON");
        assertThat((Object) JsonPath.read(list, "$.items[2].summary.outcome")).isNull();
        // no payloads in the list
        assertThat(JsonPath.<Map<String, Object>>read(list, "$.items[0]"))
                .doesNotContainKey("payload");

        other.reports()
                .andExpect(jsonPath("$.items[*].kind", contains("DEFENSE")))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aFilterShowsOnlyItsKindsAndEveryFilterIsCounted() throws Exception {
        Instant now = clock.instant();
        create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(400), support(), player);
        create(ReportKind.ATTACK, now.minusSeconds(300), battle(), player);
        create(ReportKind.SCOUT, now.minusSeconds(200), battle(), player);
        create(ReportKind.CONQUEST, now.minusSeconds(100), conquest(), player);

        String attacks = body(player.browser.get("/api/reports?filter=ATTACKS"));
        String all = body(player.reports());

        assertThat(JsonPath.<List<String>>read(attacks, "$.items[*].kind"))
                .containsExactly("CONQUEST", "ATTACK");
        assertThat(number(attacks, "$.total").intValue()).isEqualTo(2);
        assertThat(number(all, "$.total").intValue()).isEqualTo(4);
        for (String list : List.of(attacks, all)) {
            assertThat(JsonPath.<Map<String, Integer>>read(list, "$.counts"))
                    .containsExactlyInAnyOrderEntriesOf(
                            Map.of(
                                    "ATTACKS", 2,
                                    "DEFENSE", 0,
                                    "SCOUTING", 1,
                                    "SUPPORT", 1,
                                    "TRADE", 0));
        }
        // the list's summary carries the loot and the reader's losses (1.3)
        assertThat(JsonPath.<Integer>read(attacks, "$.items[1].summary.loot.clay")).isEqualTo(425);
        assertThat(JsonPath.<Integer>read(attacks, "$.items[1].summary.losses.AXEMAN"))
                .isEqualTo(3);
        assertThat(JsonPath.<Boolean>read(attacks, "$.items[1].summary.fullHaul")).isFalse();
        player.browser.get("/api/reports?filter=NONSENSE").andExpect(status().isBadRequest());
    }

    @Test
    void theListComesInPages() throws Exception {
        Instant now = clock.instant();
        for (int i = 0; i < 5; i++) {
            create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(i), support(), player);
        }
        // equal times: the later row first
        create(ReportKind.ATTACK, now, battle(), player);

        String first = body(player.browser.get("/api/reports?page=0&size=2"));
        String third = body(player.browser.get("/api/reports?page=2&size=2"));
        String beyond = body(player.browser.get("/api/reports?page=3&size=2"));

        assertThat(JsonPath.<List<String>>read(first, "$.items[*].kind"))
                .containsExactly("ATTACK", "SUPPORT_ARRIVED");
        assertThat(JsonPath.<List<?>>read(third, "$.items")).hasSize(2);
        assertThat(JsonPath.<List<?>>read(beyond, "$.items")).isEmpty();
        assertThat(number(beyond, "$.total").intValue()).isEqualTo(6);
        assertThat(number(third, "$.size").intValue()).isEqualTo(2);
    }

    @Test
    void openingAReportShowsItsPayloadAndMarksItRead() throws Exception {
        create(ReportKind.ATTACK, clock.instant(), battle(), player);
        long id = newestId(player);

        player.report(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.kind").value("ATTACK"))
                .andExpect(jsonPath("$.payload.type").value("BATTLE"))
                .andExpect(jsonPath("$.payload.attacker.name").value("Benhausen"))
                .andExpect(jsonPath("$.payload.attackerUnits.AXEMAN").value(100))
                .andExpect(jsonPath("$.payload.loot.clay").value(425))
                .andExpect(jsonPath("$.payload.defenderSide").value(nullValue()));

        player.reports()
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void aStoredPayloadThatCantBeReadDoesNotBreakTheList() throws Exception {
        Instant now = clock.instant();
        create(ReportKind.ATTACK, now.minusSeconds(100), battle(), player);
        create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(200), support(), player);
        long newest = newestId(player);
        // a type name that doesn't exist (any more): what a renamed record would leave behind
        jdbc.sql("update report set payload = cast(:json as jsonb) where id = :id")
                .param("json", "{\"type\":\"GONE\"}")
                .param("id", newest)
                .update();

        String list = body(player.reports().andExpect(status().isOk()));

        assertThat(JsonPath.<List<String>>read(list, "$.items[*].kind"))
                .containsExactly("ATTACK", "SUPPORT_ARRIVED");
        assertThat(JsonPath.<Object>read(list, "$.items[0].summary")).isNull();
        assertThat(JsonPath.<Object>read(list, "$.items[1].summary")).isNotNull();
        player.report(newest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("ATTACK"))
                .andExpect(jsonPath("$.payload").value(nullValue()));
        player.browser.delete("/api/reports/" + newest).andExpect(status().isNoContent());
    }

    @Test
    void barbariansGetNothingAndAPlayerNamedTwiceGetsOneReport() throws Exception {
        Long[] recipients = {null, player.playerId, other.playerId, player.playerId, null};
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                reportService.create(
                                        ReportKind.DEFENSE, clock.instant(), battle(), recipients));
        // only nulls: nothing, and no error
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                reportService.create(
                                        ReportKind.DEFENSE,
                                        clock.instant(),
                                        battle(),
                                        (Long) null));

        player.reports().andExpect(jsonPath("$.total").value(1));
        other.reports().andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void someoneElsesReportIsNotFound() throws Exception {
        create(ReportKind.ATTACK, clock.instant(), battle(), player);
        long id = newestId(player);

        other.report(id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        other.browser
                .delete("/api/reports/" + id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        other.report(Long.MAX_VALUE).andExpect(status().isNotFound());

        // still there and unread for its owner
        player.reports()
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void aDeletedReportIsGone() throws Exception {
        create(ReportKind.ATTACK, clock.instant(), battle(), player);
        long id = newestId(player);

        player.browser.delete("/api/reports/" + id).andExpect(status().isNoContent());

        player.report(id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        player.browser.delete("/api/reports/" + id).andExpect(status().isNotFound());
        player.reports().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void deleteChosenDeletesOnlyThoseThatAreTheOwnReports() throws Exception {
        Instant now = clock.instant();
        create(ReportKind.ATTACK, now.minusSeconds(100), battle(), player);
        create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(200), support(), player);
        create(ReportKind.DEFENSE, now.minusSeconds(300), battle(), player);
        create(ReportKind.ATTACK, now.minusSeconds(400), battle(), other);
        List<Integer> ids = JsonPath.read(body(player.reports()), "$.items[*].id");
        long othersId = newestId(other);

        // someone else's report and one that is gone already are skipped
        player.browser
                .post(
                        "/api/reports/delete",
                        "{\"ids\": [%d, %d, %d, 999999]}"
                                .formatted(ids.get(0), ids.get(2), othersId))
                .andExpect(status().isNoContent());

        player.reports()
                .andExpect(jsonPath("$.items[*].kind", contains("SUPPORT_ARRIVED")))
                .andExpect(jsonPath("$.total").value(1));
        other.reports().andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deleteChosenNeedsAtLeastOneAndAtMostAPageOfIds() throws Exception {
        player.browser
                .post("/api/reports/delete", "{\"ids\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.browser.post("/api/reports/delete", "{}").andExpect(status().isBadRequest());
        String tooMany =
                LongStream.rangeClosed(1, ReportController.MAX_PAGE_SIZE + 1)
                        .mapToObj(Long::toString)
                        .collect(Collectors.joining(", ", "{\"ids\": [", "]}"));
        player.browser.post("/api/reports/delete", tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void deleteAllDeletesTheOwnReportsUpToTheNewestTheListShowed() throws Exception {
        Instant now = clock.instant();
        create(ReportKind.ATTACK, now.minusSeconds(100), battle(), player, other);
        create(ReportKind.SUPPORT_ARRIVED, now.minusSeconds(200), support(), player);
        player.report(newestId(player)).andExpect(status().isOk()); // read or not: both go
        String seen = body(player.reports());
        long upTo = number(seen, "$.newestId").longValue();
        assertThat(upTo)
                .isEqualTo(
                        JsonPath.<List<Integer>>read(seen, "$.items[*].id").stream()
                                .mapToLong(Integer::longValue)
                                .max()
                                .orElseThrow());
        // arrives while the player confirms: older in game time, but not seen yet
        create(ReportKind.DEFENSE, now.minusSeconds(300), battle(), player);

        player.browser
                .post("/api/reports/delete-all?upTo=" + upTo, "")
                .andExpect(status().isNoContent());

        player.reports()
                .andExpect(jsonPath("$.items[*].kind", contains("DEFENSE")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.unread").value(1));
        other.reports().andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deleteAllWithoutReportsOrWithABadLimit() throws Exception {
        String empty = body(player.reports());
        assertThat(number(empty, "$.newestId").longValue()).isZero();
        player.browser.post("/api/reports/delete-all?upTo=0", "").andExpect(status().isNoContent());
        player.browser
                .post("/api/reports/delete-all", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.browser
                .post("/api/reports/delete-all?upTo=-1", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("upTo"));
    }

    @Test
    void deleteBarbarianDeletesTheOwnAttacksAndScoutsOnBarbariansWithoutANewBanner()
            throws Exception {
        Instant now = clock.instant();
        ScoutedBanner banner = new ScoutedBanner(BannerType.HARVEST, 1);
        // these go: an attack, a scout, a failed theft, read or not
        create(ReportKind.ATTACK, now, battleOn(BARBARIANS, null), player, other);
        create(ReportKind.SCOUT, now, battleOn(BARBARIANS, null), player);
        TheftReport failed = new TheftReport(5, 5, 0.3, TheftResult.FAILED, null);
        create(
                ReportKind.ATTACK,
                now,
                battleOn(BARBARIANS, new BannerReport(null, null, null, failed, null)),
                player);
        player.report(newestId(player)).andExpect(status().isOk());
        // these stay: a found banner, a stolen one, a player's village, other kinds
        create(
                ReportKind.ATTACK,
                now,
                battleOn(BARBARIANS, new BannerReport(null, null, banner, null, null)),
                player);
        TheftReport stolen = new TheftReport(5, 5, 0.3, TheftResult.STOLEN, banner);
        create(
                ReportKind.ATTACK,
                now,
                battleOn(BARBARIANS, new BannerReport(null, null, null, stolen, null)),
                player);
        create(ReportKind.ATTACK, now, battle(), player);
        create(ReportKind.DEFENSE, now, battle(), player);
        create(ReportKind.SUPPORT_ARRIVED, now, support(), player);
        // an attack with nobles stays too (1.4.1)
        create(ReportKind.ATTACK, now, noblesOn(BARBARIANS), player);
        // a copy another player sent stays too
        long othersReport = newestId(other);
        other.browser
                .post(
                        "/api/reports/" + othersReport + "/share",
                        "{\"names\":[\"" + name(player) + "\"]}")
                .andExpect(status().isOk());
        String seen = body(player.reports());
        assertThat(number(seen, "$.total").intValue()).isEqualTo(10);
        assertThat(number(seen, "$.barbarian").intValue()).isEqualTo(3);
        long upTo = number(seen, "$.newestId").longValue();
        // arrives while the player confirms
        create(ReportKind.ATTACK, now, battleOn(BARBARIANS, null), player);

        player.browser
                .post("/api/reports/delete-barbarian?upTo=" + upTo, "")
                .andExpect(status().isNoContent());

        String after = body(player.reports());
        assertThat(number(after, "$.total").intValue()).isEqualTo(8);
        assertThat(number(after, "$.barbarian").intValue()).isEqualTo(1);
        // the late one, found, stolen, nobles, the copy; the player's village twice; the support
        assertThat(JsonPath.<List<String>>read(after, "$.items[*].summary.to.name"))
                .filteredOn("Barbarendorf"::equals)
                .hasSize(5);
        assertThat(JsonPath.<List<Boolean>>read(after, "$.items[*].summary.nobles"))
                .filteredOn(Boolean::booleanValue)
                .hasSize(1);
        assertThat(JsonPath.<List<Object>>read(after, "$.items[*].sharedBy.name")).hasSize(1);
        assertThat(JsonPath.<List<Object>>read(after, "$.items[*].summary.bannersGained[0]"))
                .hasSize(2);
        other.reports()
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.barbarian").value(1));
    }

    @Test
    void deleteBarbarianWithoutReportsOrWithABadLimit() throws Exception {
        player.reports().andExpect(jsonPath("$.barbarian").value(0));
        player.browser
                .post("/api/reports/delete-barbarian?upTo=0", "")
                .andExpect(status().isNoContent());
        player.browser
                .post("/api/reports/delete-barbarian", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        player.browser
                .post("/api/reports/delete-barbarian?upTo=-1", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("upTo"));
    }

    @Test
    void readAllMarksOnlyTheOwnReportsRead() throws Exception {
        create(ReportKind.ATTACK, clock.instant(), battle(), player, other);
        create(ReportKind.SUPPORT_ARRIVED, clock.instant(), support(), player);

        player.browser.post("/api/reports/read-all", "").andExpect(status().isNoContent());

        player.reports()
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items[*].read", contains(true, true)));
        other.reports().andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void theUnreadCounterCountsOnlyTheOwnUnreadReports() throws Exception {
        unread(player).andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(0));
        create(ReportKind.ATTACK, clock.instant(), battle(), player, other);
        create(ReportKind.SUPPORT_ARRIVED, clock.instant(), support(), player);

        unread(player).andExpect(jsonPath("$.unread").value(2));
        unread(other).andExpect(jsonPath("$.unread").value(1));

        player.report(newestId(player)).andExpect(status().isOk());
        unread(player).andExpect(jsonPath("$.unread").value(1));

        player.browser.post("/api/reports/read-all", "").andExpect(status().isNoContent());
        unread(player).andExpect(jsonPath("$.unread").value(0));
        unread(other).andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void pageAndSizeAreChecked() throws Exception {
        player.browser
                .get("/api/reports?size=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        player.browser
                .get("/api/reports?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        player.browser
                .get("/api/reports?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        player.browser.get("/api/reports?page=10001").andExpect(status().isBadRequest());
        player.browser.get("/api/reports?size=100").andExpect(status().isOk());
    }

    @Test
    void reportsNeedALogin() throws Exception {
        Browser anonymous = Browser.open(mockMvc);

        anonymous
                .get("/api/reports")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        anonymous.get("/api/reports/1").andExpect(status().isUnauthorized());
        anonymous.get("/api/reports/unread").andExpect(status().isUnauthorized());
        anonymous.post("/api/reports/read-all", "").andExpect(status().isUnauthorized());
        anonymous
                .post("/api/reports/delete", "{\"ids\": [1]}")
                .andExpect(status().isUnauthorized());
        anonymous.post("/api/reports/delete-all?upTo=1", "").andExpect(status().isUnauthorized());
        anonymous
                .post("/api/reports/delete-barbarian?upTo=1", "")
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private void create(
            ReportKind kind, Instant happenedAt, ReportPayload payload, TestPlayer... players) {
        Long[] recipients = new Long[players.length];
        for (int i = 0; i < players.length; i++) {
            recipients[i] = players[i].playerId;
        }
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> reportService.create(kind, happenedAt, payload, recipients));
    }

    private static ResultActions unread(TestPlayer player) throws Exception {
        return player.browser.get("/api/reports/unread");
    }

    private static long newestId(TestPlayer player) throws Exception {
        return number(body(player.reports()), "$.items[0].id").longValue();
    }

    private static BattleReport battle() {
        return new BattleReport(
                BEN,
                TOM,
                BattleOutcome.ATTACKER_WON,
                0.05,
                Map.of(UnitType.AXEMAN, 100),
                Map.of(UnitType.AXEMAN, 3),
                null,
                null,
                null,
                new ResourceAmounts(150, 425, 425),
                null);
    }

    private String name(TestPlayer player) {
        return jdbc.sql("select name from player where id = :id")
                .param("id", player.playerId)
                .query(String.class)
                .single();
    }

    /** The player's attack on {@code defender}, won, with {@code banners} (null: none). */
    private static BattleReport battleOn(VillageRefDto defender, BannerReport banners) {
        return new BattleReport(
                BEN,
                defender,
                BattleOutcome.ATTACKER_WON,
                0.05,
                Map.of(UnitType.AXEMAN, 100),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                new ResourceAmounts(150, 425, 425),
                null,
                banners);
    }

    private static BattleReport noblesOn(VillageRefDto defender) {
        return new BattleReport(
                BEN,
                defender,
                BattleOutcome.ATTACKER_WON,
                0.05,
                Map.of(UnitType.AXEMAN, 100, UnitType.NOBLE, 1),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                ResourceAmounts.ZERO,
                new BattleReport.LoyaltyReport(100, 76, false),
                null);
    }

    private static ConquestReport conquest() {
        return new ConquestReport(BEN, TOM, "Tomburg", null);
    }

    private static SupportReport support() {
        return new SupportReport(TOM, BEN, Map.of(UnitType.SPEARMAN, 10));
    }
}
