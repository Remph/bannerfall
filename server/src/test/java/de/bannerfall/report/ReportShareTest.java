package de.bannerfall.report;

import static de.bannerfall.TestPlayer.body;
import static de.bannerfall.TestPlayer.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.SentMessages;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.village.VillageRefDto;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sending a report to other players (M40, 02-game-rules §14): the copies, who sent them and whose
 * view they show, and the rules of private messages (§17) that apply to it.
 */
@IntegrationTest
class ReportShareTest {

    private static final VillageRefDto ANNA = new VillageRefDto(1, "Annastadt", 48, 52, 7L, "Anna");
    private static final VillageRefDto TOM = new VillageRefDto(2, "Tomburg", 50, 50, 8L, "Tom");

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired ReportService reportService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcClient jdbc;
    @Autowired SentMessages sent;

    private TestPlayer anna;
    private TestPlayer ben;
    private TestPlayer cleo;

    @BeforeEach
    void setUp() throws Exception {
        anna = TestPlayer.register(mockMvc);
        ben = TestPlayer.register(mockMvc);
        cleo = TestPlayer.register(mockMvc);
    }

    @Test
    void eachRecipientGetsAnUnreadCopyThatSaysWhoSentIt() throws Exception {
        Instant happened = clock.instant().minusSeconds(600);
        long reportId = create(ReportKind.ATTACK, happened, anna);

        // names ignore case and a name given twice counts once
        share(anna, reportId, name(ben).toUpperCase(), name(cleo), name(ben))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo", contains(name(ben), name(cleo))));

        for (TestPlayer recipient : new TestPlayer[] {ben, cleo}) {
            recipient
                    .reports()
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.unread").value(1))
                    .andExpect(jsonPath("$.items[0].kind").value("ATTACK"))
                    .andExpect(jsonPath("$.items[0].read").value(false))
                    .andExpect(jsonPath("$.items[0].sharedBy.name").value(name(anna)))
                    .andExpect(jsonPath("$.items[0].summary.from.name").value("Annastadt"));
            long copyId = newestId(recipient);
            assertThat(copyId).isNotEqualTo(reportId);
            recipient
                    .report(copyId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.happenedAt").value(happened.toString()))
                    .andExpect(jsonPath("$.payload.attackerUnits.AXEMAN").value(100))
                    .andExpect(jsonPath("$.sharedBy.playerId").value(anna.playerId))
                    .andExpect(jsonPath("$.sharedBy.viewOfPlayerId").value(anna.playerId))
                    .andExpect(jsonPath("$.sharedBy.viewOfName").value(name(anna)));
            assertThat(sent.toPlayer(recipient.playerId))
                    .anyMatch(
                            json ->
                                    json.contains("\"REPORT_CREATED\"")
                                            && json.contains("\"sharedBy\":\"" + name(anna)));
        }
        // the sender's own report is unchanged and not a copy
        anna.reports()
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(reportId))
                .andExpect(jsonPath("$.items[0].sharedBy").doesNotExist());
        // deleting the original leaves the copies
        anna.browser.delete("/api/reports/" + reportId).andExpect(status().isNoContent());
        ben.reports().andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void aCopySentOnKeepsWhoseReportItWas() throws Exception {
        long reportId = create(ReportKind.DEFENSE, clock.instant(), anna);
        share(anna, reportId, name(ben)).andExpect(status().isOk());
        tick();

        share(ben, newestId(ben), name(cleo)).andExpect(status().isOk());

        cleo.report(newestId(cleo))
                .andExpect(jsonPath("$.kind").value("DEFENSE"))
                .andExpect(jsonPath("$.sharedBy.playerId").value(ben.playerId))
                .andExpect(jsonPath("$.sharedBy.name").value(name(ben)))
                .andExpect(jsonPath("$.sharedBy.viewOfPlayerId").value(anna.playerId))
                .andExpect(jsonPath("$.sharedBy.viewOfName").value(name(anna)));
    }

    @Test
    void aCopyIsListedFromWhenItArrivedNotFromWhenItHappened() throws Exception {
        Instant happened = clock.instant().minus(Duration.ofHours(5));
        long old = create(ReportKind.ATTACK, happened, anna);
        Instant bensTime = clock.instant().minus(Duration.ofHours(1));
        long bensOwn = create(ReportKind.DEFENSE, bensTime, ben);
        tick();

        share(anna, old, name(ben)).andExpect(status().isOk());

        long copyId = newestId(ben);
        ben.reports()
                .andExpect(jsonPath("$.items[0].id").value(copyId))
                // the line still shows when it happened; the day headings go by when it arrived
                .andExpect(jsonPath("$.items[0].happenedAt").value(happened.toString()))
                .andExpect(jsonPath("$.items[0].listedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.items[1].id").value(bensOwn))
                .andExpect(jsonPath("$.items[1].listedAt").value(bensTime.toString()));
    }

    @Test
    void oneNameThatCantGetItStopsTheWholeSend() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);

        share(anna, reportId, name(ben), "nobody-by-this-name")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.name").value("nobody-by-this-name"));
        share(anna, reportId, name(ben), " " + name(anna) + " ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_PLAYER"))
                .andExpect(jsonPath("$.name").value(name(anna)));

        ben.reports().andExpect(jsonPath("$.total").value(0));
        assertThat(sent.toPlayer(ben.playerId)).noneMatch(j -> j.contains("REPORT_CREATED"));
    }

    @Test
    void onlyTheOwnReportsCanBeSent() throws Exception {
        long bensReport = create(ReportKind.ATTACK, clock.instant(), ben);

        share(anna, bensReport, name(cleo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        share(anna, 999_999_999L, name(cleo)).andExpect(status().isNotFound());
        cleo.reports().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void theNamesAreChecked() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);

        share(anna, reportId).andExpect(status().isBadRequest());
        share(anna, reportId, " ").andExpect(status().isBadRequest());
        String[] eleven = new String[11];
        Arrays.fill(eleven, name(ben));
        share(anna, reportId, eleven)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        anna.browser
                .post("/api/reports/" + reportId + "/share", "{}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReportThatCantBeReadIsNotSent() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);
        jdbc.sql("update report set payload = cast(:json as jsonb) where id = :id")
                .param("json", "{\"type\":\"GONE\"}")
                .param("id", reportId)
                .update();

        share(anna, reportId, name(ben))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_UNREADABLE"));
    }

    @Test
    void sendingTakesTheMessageSlot() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);
        share(anna, reportId, name(ben)).andExpect(status().isOk());

        share(anna, reportId, name(cleo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MESSAGE_RATE_LIMIT"));
        anna.browser
                .post("/api/messages/with/" + cleo.playerId, "{\"text\":\"hi\"}")
                .andExpect(status().isTooManyRequests());
        tick();
        share(anna, reportId, name(cleo)).andExpect(status().isOk());
    }

    @Test
    void aRecipientHasAtMost30UnreadCopiesFromOneSender() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);
        for (int i = 0; i < 30; i++) {
            tick();
            share(anna, reportId, name(ben)).andExpect(status().isOk());
        }
        tick();
        share(anna, reportId, name(cleo), name(ben))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INBOX_FULL"))
                .andExpect(jsonPath("$.name").value(name(ben)));
        cleo.reports().andExpect(jsonPath("$.total").value(0));

        // reading one makes room for one
        ben.report(newestId(ben)).andExpect(status().isOk());
        tick();
        share(anna, reportId, name(ben)).andExpect(status().isOk());
        tick();
        share(anna, reportId, name(ben)).andExpect(jsonPath("$.code").value("INBOX_FULL"));
    }

    @Test
    void aBlockedSenderIsToldNothingAndHisCopiesStayHidden() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);
        share(anna, reportId, name(ben)).andExpect(status().isOk());
        assertThat(unread(ben)).isEqualTo(1);
        tick();

        ben.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        // the copy that came before the block goes out of sight too
        assertThat(unread(ben)).isZero();
        ben.reports().andExpect(jsonPath("$.total").value(0));

        long notifiedBefore = reportNotifications(ben);
        tick();
        share(anna, reportId, name(ben))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo", contains(name(ben))));
        assertThat(reportNotifications(ben)).isEqualTo(notifiedBefore);
        ben.reports()
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.unread").value(0));
        // the game's own reports still show
        create(ReportKind.DEFENSE, clock.instant(), ben);
        ben.reports().andExpect(jsonPath("$.total").value(1));

        // the same limit as for a Ben who never reads, counted from the block
        for (int i = 0; i < 29; i++) {
            tick();
            share(anna, reportId, name(ben)).andExpect(status().isOk());
        }
        tick();
        share(anna, reportId, name(ben)).andExpect(jsonPath("$.code").value("INBOX_FULL"));

        // Ben can't send to someone he blocked
        long bensReport = newestId(ben);
        tick();
        share(ben, bensReport, name(anna))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_BLOCKED"));

        // unblocking brings the copies back: the one from before unread, the others read
        ben.browser.delete("/api/blocks/" + anna.playerId).andExpect(status().isNoContent());
        ben.reports()
                .andExpect(jsonPath("$.total").value(32))
                .andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void theLastThreePlayersSentToByNameAreRemembered() throws Exception {
        TestPlayer dora = TestPlayer.register(mockMvc);
        TestPlayer emil = TestPlayer.register(mockMvc);
        recent(anna).andExpect(status().isOk()).andExpect(jsonPath("$.names", empty()));
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);

        for (TestPlayer recipient : new TestPlayer[] {ben, cleo, dora, emil}) {
            tick();
            share(anna, reportId, name(recipient)).andExpect(status().isOk());
        }
        recent(anna).andExpect(jsonPath("$.names", contains(name(emil), name(dora), name(cleo))));

        // a new send moves a player back to the front; the recipients don't remember the sender
        tick();
        share(anna, reportId, name(ben)).andExpect(status().isOk());
        recent(anna).andExpect(jsonPath("$.names", contains(name(ben), name(emil), name(dora))));
        recent(ben).andExpect(jsonPath("$.names", empty()));

        // a recipient deleting the copy or blocking the sender changes nothing for him
        ben.browser.delete("/api/reports/" + newestId(ben)).andExpect(status().isNoContent());
        ben.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        recent(anna).andExpect(jsonPath("$.names", contains(name(ben), name(emil), name(dora))));

        // a player the sender blocked is left out, and the next one moves up
        anna.browser.put("/api/blocks/" + emil.playerId, "").andExpect(status().isNoContent());
        recent(anna).andExpect(jsonPath("$.names", contains(name(ben), name(dora), name(cleo))));
    }

    @Test
    void aSendThatFailsIsNotRemembered() throws Exception {
        long reportId = create(ReportKind.ATTACK, clock.instant(), anna);
        share(anna, reportId, name(ben), "nobody-" + System.nanoTime())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        recent(anna).andExpect(jsonPath("$.names", empty()));
    }

    @Test
    void sendingNeedsALogin() throws Exception {
        Browser.open(mockMvc)
                .post("/api/reports/1/share", "{\"names\":[\"x\"]}")
                .andExpect(status().isUnauthorized());
        Browser.open(mockMvc)
                .get("/api/reports/recent-recipients")
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private static ResultActions share(TestPlayer from, long reportId, String... names)
            throws Exception {
        String json =
                Arrays.stream(names)
                        .map(name -> "\"" + name + "\"")
                        .collect(Collectors.joining(",", "{\"names\":[", "]}"));
        return from.browser.post("/api/reports/" + reportId + "/share", json);
    }

    private static ResultActions recent(TestPlayer player) throws Exception {
        return player.browser.get("/api/reports/recent-recipients");
    }

    /** Two seconds later, so the next send passes the one-per-second limit. */
    private void tick() {
        clock.advance(Duration.ofSeconds(2));
    }

    private String name(TestPlayer player) {
        return jdbc.sql("select name from player where id = :id")
                .param("id", player.playerId)
                .query(String.class)
                .single();
    }

    private long unread(TestPlayer player) throws Exception {
        return number(body(player.browser.get("/api/reports/unread")), "$.unread").longValue();
    }

    private long reportNotifications(TestPlayer player) {
        return sent.toPlayer(player.playerId).stream()
                .filter(json -> json.contains("REPORT_CREATED"))
                .count();
    }

    /** A battle report for the player; its id. */
    private long create(ReportKind kind, Instant happenedAt, TestPlayer player) throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                reportService.create(kind, happenedAt, battle(), player.playerId));
        return newestId(player);
    }

    private static long newestId(TestPlayer player) throws Exception {
        return number(body(player.reports()), "$.newestId").longValue();
    }

    private static BattleReport battle() {
        return new BattleReport(
                ANNA,
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
}
