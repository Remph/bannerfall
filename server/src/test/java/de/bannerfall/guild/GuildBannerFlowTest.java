package de.bannerfall.guild;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.event.EventProcessor;
import de.bannerfall.rules.Travel;
import de.bannerfall.world.WorldService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The guild banner (02-game-rules §16.4): donating, the price per member, the 24 hour lock after a
 * member left or was removed, and the three effects (production, support travel time, defence).
 * Prices of the shipped {@code guild-banner.yml}: 30,000 per member for level 1.
 */
@IntegrationTest
class GuildBannerFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JdbcClient jdbc;
    @Autowired GuildService guildService;
    @Autowired WorldService worldService;
    @Autowired EventProcessor processor;

    private TestPlayer leader;
    private TestPlayer member;
    private Instant start;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("delete from game_event").update();
        start = clock.instant();
        setWorld(true, true);
        guildService.setInviteInterval(Duration.ZERO);
        leader = TestPlayer.register(mockMvc);
        member = TestPlayer.register(mockMvc);
        found(leader);
        join(leader, member);
        richVillage(leader);
        richVillage(member);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("delete from game_event").update();
        jdbc.sql("delete from guild").update(); // banner, donations, members: on delete cascade
        setWorld(false, false);
        guildService.setInviteInterval(Duration.ofMinutes(1));
        clock.set(start);
    }

    // ---- donating and raising ----

    @Test
    void donationsFillTheFundAndTheRaiseNeedsThePriceOfTheMembersNow() throws Exception {
        // two members: level 1 costs 2 × 30,000
        member.browser
                .get("/api/guilds/mine/banner")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.price").value(60_000))
                .andExpect(jsonPath("$.missing").value(60_000))
                .andExpect(jsonPath("$.members").value(2));

        donate(member, 10_000, 5_000, 5_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fund").value(20_000))
                .andExpect(jsonPath("$.missing").value(40_000))
                .andExpect(jsonPath("$.donors[0].name").value(nameOf(member)))
                .andExpect(jsonPath("$.donors[0].amount").value(20_000));
        // the village paid
        assertThat(stock(member, "wood")).isEqualTo(990_000);
        assertThat(stock(member, "iron")).isEqualTo(995_000);
        raise(leader).andExpect(status().isConflict());
        assertThat(codeOf(body(raise(leader)))).isEqualTo("BANNER_FUND_LOW");

        donate(leader, 40_000, 0, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missing").value(0))
                .andExpect(jsonPath("$.donors[0].name").value(nameOf(leader)));
        raise(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.fund").value(0))
                .andExpect(jsonPath("$.price").value(180_000)); // level 2: 2 × 90,000
        // the public page shows the level
        String guildPage = body(leader.browser.get("/api/guilds/mine").andExpect(status().isOk()));
        assertThat(JsonPath.<Integer>read(guildPage, "$.guild.bannerLevel")).isEqualTo(1);
    }

    @Test
    void aDonationIsRefusedWhenItIsTooLargeNothingIsGivenOrTheVillageIsNotYours() throws Exception {
        assertThat(codeOf(body(donate(member, 60_001, 0, 0)))).isEqualTo("DONATION_TOO_LARGE");
        assertThat(codeOf(body(donate(member, 0, 0, 0)))).isEqualTo("VALIDATION");
        jdbc.sql("update village set wood = 100 where id = ?").param(member.villageId).update();
        assertThat(codeOf(body(donate(member, 500, 0, 0)))).isEqualTo("NOT_ENOUGH_RESOURCES");
        member.browser
                .post(
                        "/api/guilds/mine/banner/donations",
                        "{\"villageId\":" + leader.villageId + ",\"wood\":1,\"clay\":0,\"iron\":0}")
                .andExpect(status().isNotFound());
        // nothing was taken in the end
        member.browser.get("/api/guilds/mine/banner").andExpect(jsonPath("$.fund").value(0));
    }

    @Test
    void withoutTheSwitchOrAGuildThereIsNothingToDonateTo() throws Exception {
        TestPlayer loner = TestPlayer.register(mockMvc);
        richVillage(loner);
        assertThat(codeOf(body(donate(loner, 1, 0, 0)))).isEqualTo("NOT_IN_GUILD");
        setWorld(true, false);
        assertThat(codeOf(body(donate(member, 1, 0, 0)))).isEqualTo("GUILD_BANNERS_DISABLED");
        assertThat(codeOf(body(raise(member)))).isEqualTo("GUILD_BANNERS_DISABLED");
        member.browser.get("/api/guilds/mine/banner").andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void theLastLevelIsTheTopOne() throws Exception {
        jdbc.sql("insert into guild_banner (guild_id, level, fund) values (?, 3, 5)")
                .param(guildId())
                .update();
        assertThat(codeOf(body(donate(member, 1, 0, 0)))).isEqualTo("BANNER_MAX_LEVEL");
        assertThat(codeOf(body(raise(member)))).isEqualTo("BANNER_MAX_LEVEL");
        member.browser
                .get("/api/guilds/mine/banner")
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.missing").value(0));
    }

    // ---- the 24 hour lock ----

    @Test
    void whenAMemberLeavesNoLevelCanBeRaisedForADayAndThePriceFalls() throws Exception {
        TestPlayer third = TestPlayer.register(mockMvc);
        join(leader, third);
        // three members: 90,000 for level 1
        donate(leader, 90_000, 0, 0).andExpect(status().isOk());
        raise(leader).andExpect(status().isOk()).andExpect(jsonPath("$.level").value(1));
        donate(leader, 100_000, 80_000, 0).andExpect(status().isOk()); // level 2: 3 × 90,000 = 270k
        donate(member, 90_000, 0, 0).andExpect(status().isOk());

        third.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());

        // two members: 180,000 would do and the fund holds 270,000, but not for a day
        member.browser
                .get("/api/guilds/mine/banner")
                .andExpect(jsonPath("$.price").value(180_000))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
        assertThat(codeOf(body(raise(member)))).isEqualTo("GUILD_BANNER_LOCKED");
        clock.advance(Duration.ofHours(23));
        assertThat(codeOf(body(raise(member)))).isEqualTo("GUILD_BANNER_LOCKED");
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        raise(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.fund").value(90_000))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    @Test
    void aKickLocksTheBannerTooAndAnotherDepartureStartsTheDayAgain() throws Exception {
        TestPlayer third = TestPlayer.register(mockMvc);
        TestPlayer fourth = TestPlayer.register(mockMvc);
        join(leader, third);
        join(leader, fourth);
        donate(leader, 120_000, 0, 0).andExpect(status().isOk()); // four members: 120,000

        leader.browser
                .delete("/api/guilds/mine/members/" + third.playerId)
                .andExpect(status().isOk());
        assertThat(codeOf(body(raise(leader)))).isEqualTo("GUILD_BANNER_LOCKED");
        clock.advance(Duration.ofHours(20));
        fourth.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());
        clock.advance(Duration.ofHours(20)); // 40 h after the kick, 20 h after the leaving
        assertThat(codeOf(body(raise(leader)))).isEqualTo("GUILD_BANNER_LOCKED");
        clock.advance(Duration.ofHours(5));
        raise(leader).andExpect(status().isOk()).andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void aMemberTheHostRemovesLocksTheBannerLikeALeaving() throws Exception {
        TestPlayer third = TestPlayer.register(mockMvc);
        join(leader, third);
        donate(leader, 60_000, 0, 0).andExpect(status().isOk()); // two members would do: 60,000
        guildService.removeBecauseGone(third.playerId);
        assertThat(codeOf(body(raise(leader)))).isEqualTo("GUILD_BANNER_LOCKED");
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        raise(leader).andExpect(status().isOk()).andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void aMemberWhoJoinsRaisesThePriceButNeverLocks() throws Exception {
        donate(leader, 60_000, 0, 0).andExpect(status().isOk());
        TestPlayer third = TestPlayer.register(mockMvc);
        join(leader, third);
        // 90,000 now: the fund of 60,000 is short, and no lock was set
        member.browser
                .get("/api/guilds/mine/banner")
                .andExpect(jsonPath("$.price").value(90_000))
                .andExpect(jsonPath("$.missing").value(30_000))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
    }

    // ---- the effects ----

    @Test
    void levelOneRaisesProductionInEveryMemberVillageUntilHeLeaves() throws Exception {
        double before = woodPerHour(member);
        setLevel(1);
        assertThat(woodPerHour(member))
                .isEqualTo(before * 1.05, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(woodPerHour(leader))
                .isEqualTo(before * 1.05, org.assertj.core.data.Offset.offset(1e-9));

        member.browser.post("/api/guilds/mine/leave", "").andExpect(status().isOk());
        assertThat(woodPerHour(member))
                .isEqualTo(before, org.assertj.core.data.Offset.offset(1e-9));
        // switched off in the world: no effect for those who stay
        setWorld(true, false);
        assertThat(woodPerHour(leader))
                .isEqualTo(before, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void theRaiseToLevelOneBringsTheVillagesUpToDateAtTheOldRate() throws Exception {
        donate(leader, 60_000, 0, 0).andExpect(status().isOk());
        double perHour = woodPerHour(member);
        jdbc.sql("update village set wood = 0, resources_updated_at = ? where id = ?")
                .params(Timestamp.from(clock.instant()), member.villageId)
                .update();
        clock.advance(Duration.ofHours(10));

        raise(leader).andExpect(status().isOk()).andExpect(jsonPath("$.level").value(1));

        // the ten hours before the raise were worked at the rate without the guild banner
        double wood =
                jdbc.sql("select wood from village where id = ?")
                        .param(member.villageId)
                        .query(Double.class)
                        .single();
        assertThat(wood).isEqualTo(perHour * 10, org.assertj.core.data.Offset.offset(1.0));
        assertThat(woodPerHour(member))
                .isEqualTo(perHour * 1.05, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void levelTwoShortensASupportToAGuildMateOnly() throws Exception {
        TestPlayer outsider = TestPlayer.register(mockMvc);
        setUnits(leader, 50);
        Coordinates toMember = coordinates(member);
        Coordinates toOutsider = coordinates(outsider);
        long plain = travel(leader, toMember);
        long plainOutsider = travel(leader, toOutsider);
        assertThat(plain).isEqualTo(expectedSeconds(leader, toMember, 0));

        setLevel(1);
        assertThat(travel(leader, toMember)).isEqualTo(plain); // level 1 doesn't shorten anything
        setLevel(2);
        assertThat(travel(leader, toMember)).isEqualTo(expectedSeconds(leader, toMember, 0.10));
        assertThat(travel(leader, toMember)).isLessThan(plain);
        assertThat(travel(leader, toOutsider)).isEqualTo(plainOutsider);
    }

    @Test
    void levelThreeAddsItsDefenceToTheBattleAndTheReport() throws Exception {
        TestPlayer attacker = TestPlayer.register(mockMvc);
        jdbc.sql("update player set protected_until = ? where id = ?")
                .params(Timestamp.from(clock.instant().minusSeconds(1)), member.playerId)
                .update();
        setUnits(attacker, 100);
        setLevel(3);

        Instant arrival =
                JsonPath.<String>read(
                                body(
                                        attacker.command(
                                                        "ATTACK",
                                                        coordinates(member),
                                                        "{\"SPEARMAN\":30}")
                                                .andExpect(status().isCreated())),
                                "$.arriveAt")
                        .transform(Instant::parse);
        clock.set(arrival);
        processor.processDue(arrival);

        String defense = member.newestReport("DEFENSE");
        assertThat(JsonPath.<Double>read(defense, "$.payload.banners.guildDefenceBonus"))
                .isEqualTo(0.05);
        assertThat((Object) JsonPath.read(defense, "$.payload.banners.defenceBonus")).isNull();
        // the attacker saw only a battle: no scouting, no guild banner
        String report = attacker.newestReport("ATTACK");
        assertThat((Object) JsonPath.read(report, "$.payload.banners")).isNull();
    }

    // ---- helpers ----

    private void setLevel(int level) {
        jdbc.sql(
                        """
                        insert into guild_banner (guild_id, level) values (?, ?)
                        on conflict (guild_id) do update set level = excluded.level
                        """)
                .params(guildId(), level)
                .update();
    }

    private long guildId() {
        return jdbc.sql("select guild_id from guild_member where player_id = ?")
                .param(leader.playerId)
                .query(Long.class)
                .single();
    }

    private void setWorld(boolean guilds, boolean banners) {
        jdbc.sql("update world_settings set guilds_enabled = ?, guild_banners_enabled = ?")
                .params(guilds, banners)
                .update();
        worldService.reload();
    }

    private ResultActions donate(TestPlayer by, int wood, int clay, int iron) throws Exception {
        return by.browser.post(
                "/api/guilds/mine/banner/donations",
                "{\"villageId\":"
                        + by.villageId
                        + ",\"wood\":"
                        + wood
                        + ",\"clay\":"
                        + clay
                        + ",\"iron\":"
                        + iron
                        + "}");
    }

    private ResultActions raise(TestPlayer by) throws Exception {
        return by.browser.post("/api/guilds/mine/banner/raise", "");
    }

    private static String codeOf(String json) {
        return JsonPath.read(json, "$.code");
    }

    private void richVillage(TestPlayer player) {
        jdbc.sql(
                        "update village set wood = 1000000, clay = 1000000, iron = 1000000,"
                                + " resources_updated_at = ? where id = ?")
                .params(Timestamp.from(clock.instant()), player.villageId)
                .update();
    }

    private double stock(TestPlayer player, String resource) throws Exception {
        return JsonPath.<Number>read(body(player.village()), "$.resources." + resource)
                .doubleValue();
    }

    private double woodPerHour(TestPlayer player) throws Exception {
        return JsonPath.<Number>read(body(player.village()), "$.production.wood").doubleValue();
    }

    private String nameOf(TestPlayer player) {
        return jdbc.sql("select name from player where id = ?")
                .param(player.playerId)
                .query(String.class)
                .single();
    }

    private void setUnits(TestPlayer player, int spearmen) {
        jdbc.sql(
                        "update unit_stack set u_spearman = ? where home_village_id = ? and"
                                + " location_village_id = ?")
                .params(spearmen, player.villageId, player.villageId)
                .update();
    }

    private Coordinates coordinates(TestPlayer player) throws Exception {
        String village = body(player.village());
        return new Coordinates(
                JsonPath.<Number>read(village, "$.x").intValue(),
                JsonPath.<Number>read(village, "$.y").intValue());
    }

    /** The travel time the preview of a support of one spearman gives. */
    private long travel(TestPlayer from, Coordinates to) throws Exception {
        String preview =
                body(
                        from.previewCommand("SUPPORT", to, "{\"SPEARMAN\":1}")
                                .andExpect(status().isOk()));
        return JsonPath.<Number>read(preview, "$.travelSeconds").longValue();
    }

    private long expectedSeconds(TestPlayer from, Coordinates to, double haste) throws Exception {
        Coordinates at = coordinates(from);
        return Travel.withHaste(
                Travel.travelSeconds(
                        Travel.distance(at, to),
                        Travel.supportMinutesPerField(
                                de.bannerfall.TestGameData.load(),
                                java.util.Map.of(de.bannerfall.gamedata.UnitType.SPEARMAN, 1)),
                        worldService.settings()),
                haste);
    }

    private long found(TestPlayer founder) throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String body =
                body(
                        founder.browser
                                .post(
                                        "/api/guilds",
                                        "{\"name\":\"Banner "
                                                + suffix
                                                + "\",\"tag\":\""
                                                + suffix.substring(suffix.length() - 4)
                                                + "\"}")
                                .andExpect(status().isCreated()));
        return JsonPath.<Number>read(body, "$.guild.id").longValue();
    }

    private void join(TestPlayer owner, TestPlayer joiner) throws Exception {
        owner.browser
                .post("/api/guilds/mine/invitations", "{\"playerName\":\"" + nameOf(joiner) + "\"}")
                .andExpect(status().isCreated());
        String mine = body(joiner.browser.get("/api/guilds/mine").andExpect(status().isOk()));
        long invitation = JsonPath.<Number>read(mine, "$.invitations[0].id").longValue();
        joiner.browser
                .post("/api/guilds/invitations/" + invitation + "/accept", "")
                .andExpect(status().isOk());
    }
}
