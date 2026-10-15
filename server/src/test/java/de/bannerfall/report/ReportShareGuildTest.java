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
import de.bannerfall.world.WorldService;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
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
 * Sending a report to the whole guild (02-game-rules §14): every other member gets a copy, without
 * typing a name; members a named send would refuse are skipped instead of stopping the send.
 */
@IntegrationTest
class ReportShareGuildTest {

    private static final VillageRefDto ANNA = new VillageRefDto(1, "Annastadt", 48, 52, 7L, "Anna");
    private static final VillageRefDto TOM = new VillageRefDto(2, "Tomburg", 50, 50, 8L, "Tom");

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired ReportService reportService;
    @Autowired WorldService worldService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcClient jdbc;
    @Autowired SentMessages sent;

    private TestPlayer anna;
    private TestPlayer ben;
    private TestPlayer cleo;
    private TestPlayer outsider;
    private long guildId;

    @BeforeEach
    void setUp() throws Exception {
        setGuildsEnabled(true);
        anna = TestPlayer.register(mockMvc);
        ben = TestPlayer.register(mockMvc);
        cleo = TestPlayer.register(mockMvc);
        outsider = TestPlayer.register(mockMvc);
        String suffix = Long.toString(System.nanoTime(), 36);
        guildId =
                jdbc.sql(
                                "insert into guild (name, tag, created_at) values (?, ?, now())"
                                        + " returning id")
                        .param("Reporters " + suffix)
                        .param(suffix.substring(suffix.length() - 4))
                        .query(Long.class)
                        .single();
        addMember(anna, "MEMBER");
        addMember(ben, "LEADER");
        addMember(cleo, "MEMBER");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from guild where id = ?").param(guildId).update();
        setGuildsEnabled(false);
    }

    @Test
    void everyOtherMemberGetsACopy() throws Exception {
        long reportId = create(anna);

        shareWithGuild(anna, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo", contains(byName(ben, cleo))));

        for (TestPlayer member : new TestPlayer[] {ben, cleo}) {
            member.reports()
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.unread").value(1))
                    .andExpect(jsonPath("$.items[0].sharedBy.name").value(name(anna)));
            assertThat(sent.toPlayer(member.playerId))
                    .anyMatch(
                            json ->
                                    json.contains("\"REPORT_CREATED\"")
                                            && json.contains("\"sharedBy\":\"" + name(anna)));
        }
        outsider.reports().andExpect(jsonPath("$.total").value(0));
        // the sender gets no copy of his own report
        anna.reports()
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(reportId));
    }

    @Test
    void membersANamedSendWouldRefuseAreSkipped() throws Exception {
        TestPlayer dora = TestPlayer.register(mockMvc);
        TestPlayer emil = TestPlayer.register(mockMvc);
        TestPlayer fritz = TestPlayer.register(mockMvc);
        addMember(dora, "MEMBER");
        addMember(emil, "MEMBER");
        addMember(fritz, "MEMBER");
        long reportId = create(anna);
        // Anna blocked Ben, Cleo has 30 unread copies from Anna, Dora's account is deleted
        anna.browser.put("/api/blocks/" + ben.playerId, "").andExpect(status().isNoContent());
        for (int i = 0; i < ReportShareService.MAX_UNREAD_FROM_ONE; i++) {
            tick();
            share(anna, reportId, name(cleo)).andExpect(status().isOk());
        }
        jdbc.sql(
                        "update account set disabled = true"
                                + " where id = (select account_id from player where id = ?)")
                .param(dora.playerId)
                .update();
        // Emil blocked Anna: he gets the hidden copy, and Anna can't tell
        emil.browser.put("/api/blocks/" + anna.playerId, "").andExpect(status().isNoContent());
        long emilNotified = reportNotifications(emil);
        tick();

        shareWithGuild(anna, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo", contains(byName(emil, fritz))));

        ben.reports().andExpect(jsonPath("$.total").value(0));
        cleo.reports().andExpect(jsonPath("$.total").value(30));
        fritz.reports().andExpect(jsonPath("$.total").value(1));
        emil.reports().andExpect(jsonPath("$.total").value(0));
        assertThat(reportNotifications(emil)).isEqualTo(emilNotified);
        assertThat(
                        jdbc.sql(
                                        "select count(*) from report where player_id = ? and"
                                                + " shared_by_id = ?")
                                .param(emil.playerId)
                                .param(anna.playerId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    void aMemberAloneInHisGuildSendsToNobody() throws Exception {
        jdbc.sql("delete from guild_member where player_id in (?, ?)")
                .param(ben.playerId)
                .param(cleo.playerId)
                .update();
        long reportId = create(anna);

        shareWithGuild(anna, reportId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo", empty()));
    }

    @Test
    void itNeedsAGuild() throws Exception {
        long reportId = create(outsider);

        shareWithGuild(outsider, reportId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_GUILD"));

        setGuildsEnabled(false);
        long annasReport = create(anna);
        shareWithGuild(anna, annasReport)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUILDS_DISABLED"));
        ben.reports().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void onlyTheOwnReportsCanBeSent() throws Exception {
        long bensReport = create(ben);

        shareWithGuild(anna, bensReport)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        cleo.reports().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void sendingTakesTheMessageSlot() throws Exception {
        long reportId = create(anna);
        share(anna, reportId, name(outsider)).andExpect(status().isOk());

        shareWithGuild(anna, reportId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MESSAGE_RATE_LIMIT"));
        tick();
        shareWithGuild(anna, reportId).andExpect(status().isOk());
        tick();
        share(anna, reportId, name(outsider)).andExpect(status().isOk());
    }

    @Test
    void sendingNeedsALogin() throws Exception {
        Browser.open(mockMvc)
                .post("/api/reports/1/share-guild", "")
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private static ResultActions shareWithGuild(TestPlayer from, long reportId) throws Exception {
        return from.browser.post("/api/reports/" + reportId + "/share-guild", "");
    }

    private static ResultActions share(TestPlayer from, long reportId, String name)
            throws Exception {
        return from.browser.post(
                "/api/reports/" + reportId + "/share", "{\"names\":[\"" + name + "\"]}");
    }

    private void setGuildsEnabled(boolean enabled) {
        jdbc.sql("update world_settings set guilds_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private void addMember(TestPlayer player, String role) {
        jdbc.sql(
                        "insert into guild_member (player_id, guild_id, role, joined_at) values (?,"
                                + " ?, ?, now())")
                .param(player.playerId)
                .param(guildId)
                .param(role)
                .update();
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

    /** The players' names in the order the answer lists them. */
    private String[] byName(TestPlayer... players) {
        return Stream.of(players)
                .map(this::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toArray(String[]::new);
    }

    private long reportNotifications(TestPlayer player) {
        return sent.toPlayer(player.playerId).stream()
                .filter(json -> json.contains("REPORT_CREATED"))
                .count();
    }

    /** A battle report for the player; its id. */
    private long create(TestPlayer player) throws Exception {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                reportService.create(
                                        ReportKind.ATTACK,
                                        clock.instant(),
                                        battle(),
                                        player.playerId));
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
