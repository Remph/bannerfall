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
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        anonymous.post("/api/reports/delete-all?upTo=1", "").andExpect(status().isUnauthorized());
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

    private static SupportReport support() {
        return new SupportReport(TOM, BEN, Map.of(UnitType.SPEARMAN, 10));
    }
}
