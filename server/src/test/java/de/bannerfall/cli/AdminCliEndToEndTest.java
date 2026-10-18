package de.bannerfall.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.world.AdminWorldSettingsDto;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettingsUpdateRequest;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The CLI against the real server on its random port (08-host-console §9): the same HTTP calls
 * {@code docker compose exec app bannerfall-admin …} makes.
 */
@IntegrationTest
class AdminCliEndToEndTest {

    @LocalServerPort int port;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired WorldService worldService;

    private StringWriter out;
    private StringWriter err;

    private int cli(String input, boolean interactive, String... args) {
        out = new StringWriter();
        err = new StringWriter();
        return AdminCli.run(
                args,
                Map.of(
                        "BANNERFALL_ADMIN_TOKEN",
                        IntegrationTest.ADMIN_TOKEN,
                        "BANNERFALL_URL",
                        "http://localhost:" + port),
                new BufferedReader(new StringReader(input)),
                new PrintWriter(out),
                new PrintWriter(err),
                interactive);
    }

    private int cli(String... args) {
        return cli("", false, args);
    }

    @Test
    void statusPrintsTheServerState() {
        assertThat(cli("status")).isZero();
        assertThat(out.toString())
                .startsWith("Bannerfall ")
                .contains("database ok", "CHEATS OFF", "Players ", "Events: ");

        assertThat(cli("--json", "status")).isZero();
        assertThat(out.toString()).contains("\"databaseOk\" : true");
        // after the command too, as the README writes it
        assertThat(cli("status", "--json")).isZero();
        assertThat(out.toString()).contains("\"databaseOk\" : true");
        assertThat(cli("npc", "difficulty", "--json")).isZero();
        assertThat(out.toString()).contains("\"difficulty\" : ");
    }

    @Test
    void playersAreListedAndShownWithTheirVillages() throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        String name = name(player);

        assertThat(cli("players")).isZero();
        assertThat(out.toString()).contains(name).contains("PLAYER");

        assertThat(cli("player", name.toLowerCase())).isZero();
        assertThat(out.toString())
                .contains(name + " – PLAYER")
                .contains(" " + player.villageId + " ");

        assertThat(cli("player", "nobody-like-this")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("No human player named 'nobody-like-this'");
    }

    @Test
    void theInviteCodeIsChangedFromTheCommandLine() {
        AdminWorldSettingsDto before = worldService.adminSettings();
        try {
            assertThat(cli("world", "set", "invite-code", "summer26")).isZero();
            assertThat(out.toString()).contains("invite-code = summer26");
            assertThat(worldService.settings().inviteCode()).isEqualTo("summer26");

            assertThat(cli("world")).isZero();
            assertThat(out.toString()).contains("invite-code").contains("summer26");
        } finally {
            worldService.updateSettings(request(before));
        }
    }

    @Test
    void theBarbarianWallSettingsAreChangedFromTheCommandLine() {
        AdminWorldSettingsDto before = worldService.adminSettings();
        try {
            assertThat(cli("world", "set", "barbarian-walls-enabled", "true")).isZero();
            assertThat(cli("world", "set", "barbarian-wall-start-days", "5")).isZero();
            assertThat(cli("world", "set", "barbarian-wall-max-level", "8")).isZero();
            assertThat(cli("world", "set", "barbarian-walls-rebuild", "true")).isZero();

            var world = worldService.settings();
            assertThat(world.barbarianWallsEnabled()).isTrue();
            assertThat(world.barbarianWallStartDays()).isEqualTo(5);
            assertThat(world.barbarianWallMaxLevel()).isEqualTo(8);
            assertThat(world.barbarianWallsRebuild()).isTrue();
        } finally {
            worldService.updateSettings(request(before));
        }
    }

    @Test
    void unitLevelsAreSwitchedOnFromTheCommandLine() {
        AdminWorldSettingsDto before = worldService.adminSettings();
        try {
            assertThat(cli("world", "set", "unit-levels-enabled", "true")).isZero();

            assertThat(worldService.settings().unitLevelsEnabled()).isTrue();
        } finally {
            worldService.updateSettings(request(before));
        }
    }

    @Test
    void theBannerSettingsAreChangedFromTheCommandLine() {
        AdminWorldSettingsDto before = worldService.adminSettings();
        try {
            assertThat(cli("world", "set", "banners-enabled", "false")).isZero();
            assertThat(cli("world", "set", "banner-drop-factor", "2")).isZero();

            var world = worldService.settings();
            assertThat(world.bannersEnabled()).isFalse();
            assertThat(world.bannerDropFactor()).isEqualTo(2.0);
        } finally {
            worldService.updateSettings(request(before));
        }
    }

    @Test
    void bannersAreGrantedListedAndRevokedFromTheCommandLine() throws Exception {
        TestPlayer friend = TestPlayer.register(mockMvc);
        String name = name(friend);

        assertThat(cli("banner", "grant", name, "TREASURY", "2", "--reason", "for the test"))
                .isZero();
        assertThat(out.toString()).contains("got banner").contains("TREASURY, tier 2");
        long id =
                jdbc.sql("select id from banner where owner_id = ?")
                        .param(friend.playerId)
                        .query(Long.class)
                        .single();

        assertThat(cli("banner", "list", name)).isZero();
        assertThat(out.toString()).contains("TREASURY").contains("(stash)");
        assertThat(cli("banner", "revoke", String.valueOf(id), "--yes")).isZero();
        assertThat(out.toString()).contains("is gone");
        assertThat(
                        jdbc.sql("select count(*) from banner where owner_id = ?")
                                .param(friend.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
        // a banner that is gone can't be taken away again
        assertThat(cli("banner", "revoke", String.valueOf(id), "--yes"))
                .isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("BANNER_NOT_FOUND");
        // not a tier of the rulebook
        assertThat(cli("banner", "grant", name, "WAR", "4")).isEqualTo(AdminCli.EXIT_REFUSED);
    }

    @Test
    void thePaladinSettingsAreChangedFromTheCommandLine() {
        AdminWorldSettingsDto before = worldService.adminSettings();
        try {
            assertThat(cli("world", "set", "paladin-villages-per-slot", "6")).isZero();
            assertThat(cli("world", "set", "paladin-max-slots", "3")).isZero();

            var world = worldService.settings();
            assertThat(world.paladinVillagesPerSlot()).isEqualTo(6);
            assertThat(world.paladinMaxSlots()).isEqualTo(3);
        } finally {
            worldService.updateSettings(request(before));
        }
    }

    @Test
    void lordsArePlacedInLordGuildsFromTheCommandLine() {
        boolean guildsWereOn = worldService.settings().guildsEnabled();
        try {
            assertThat(cli("npc", "spawn", "balanced")).isZero();
            assertThat(cli("npc", "spawn", "defensive")).isZero();
            List<String> lords =
                    jdbc.sql("select name from player where npc order by id desc limit 2")
                            .query(String.class)
                            .list();
            String first = lords.get(1);
            String second = lords.get(0);
            assertThat(cli("npc", "guild", first, "found")).isEqualTo(AdminCli.EXIT_REFUSED);
            assertThat(err.toString()).contains("GUILDS_DISABLED");

            jdbc.sql("update world_settings set guilds_enabled = true, npc_guilds_enabled = true")
                    .update();
            worldService.reload();
            assertThat(cli("npc", "guild", first, "found")).isZero();
            assertThat(out.toString()).startsWith(first + ": Ordo ").contains("(placed)");
            String tag =
                    jdbc.sql(
                                    """
                                    select g.tag from guild g
                                    join guild_member m on m.guild_id = g.id
                                    join player p on p.id = m.player_id where p.name = ?
                                    """)
                            .param(first)
                            .query(String.class)
                            .single();

            assertThat(cli("npc", "guild", second, "join")).isEqualTo(AdminCli.EXIT_REFUSED);
            assertThat(err.toString()).contains("join needs the tag of a lord guild");
            assertThat(cli("npc", "guild", second, "join", "nope"))
                    .isEqualTo(AdminCli.EXIT_REFUSED);
            assertThat(err.toString()).contains("No lord guild nope");
            assertThat(cli("npc", "guild", second, "join", tag.toLowerCase())).isZero();
            assertThat(cli("npc", "list")).isZero();
            assertThat(out.toString()).contains(tag + " (placed)");

            assertThat(cli("npc", "guild", second, "auto")).isZero();
            assertThat(out.toString()).contains("(chooses on its own)");
            assertThat(cli("npc", "guild", first, "leave")).isZero();
            assertThat(out.toString()).contains(first + ": no guild (placed)");
            assertThat(cli("npc", "guild", "Nobody Like This", "leave"))
                    .isEqualTo(AdminCli.EXIT_REFUSED);
            assertThat(err.toString()).contains("No NPC lord called Nobody Like This");
        } finally {
            jdbc.sql("delete from game_event").update();
            String lordVillages =
                    "select id from village where player_id in (select id from player where npc)";
            jdbc.sql(
                            "delete from unit_stack where home_village_id in ("
                                    + lordVillages
                                    + ") or location_village_id in ("
                                    + lordVillages
                                    + ")")
                    .update();
            jdbc.sql("delete from village where player_id in (select id from player where npc)")
                    .update();
            jdbc.sql(
                            "delete from guild where id in (select guild_id from guild_member"
                                    + " where player_id in (select id from player where npc))")
                    .update();
            jdbc.sql("delete from player where npc").update();
            jdbc.sql("update world_settings set npc_guilds_enabled = false, guilds_enabled = ?")
                    .param(guildsWereOn)
                    .update();
            worldService.reload();
        }
    }

    @Test
    void aRefusalPrintsTheServersCodeAndIsExitCode1() {
        assertThat(cli("world", "set", "luck-range", "2")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString())
                .contains("400 VALIDATION")
                .contains("luckRange: must be less than or equal to 1");

        assertThat(cli("events", "retry", "999999999")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("404 EVENT_NOT_FOUND");

        assertThat(cli("cheats", "finish-orders", "1")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("The cheats are off on this server");
    }

    @Test
    void aWrongTokenIsExitCode1WithAHint() {
        out = new StringWriter();
        err = new StringWriter();
        int code =
                AdminCli.run(
                        new String[] {"status"},
                        Map.of(
                                "BANNERFALL_ADMIN_TOKEN",
                                "wrong",
                                "BANNERFALL_URL",
                                "http://localhost:" + port),
                        new BufferedReader(new StringReader("")),
                        new PrintWriter(out),
                        new PrintWriter(err),
                        false);
        assertThat(code).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString())
                .contains("401 UNAUTHENTICATED")
                .contains("wrong BANNERFALL_ADMIN_TOKEN");
    }

    @Test
    void aFriendsPasswordIsResetAfterTypingHisName() throws Exception {
        TestPlayer friend = TestPlayer.register(mockMvc);
        String name = name(friend);

        // no terminal, no --yes: refused before anything changes
        assertThat(cli("player", name, "reset-password")).isEqualTo(AdminCli.EXIT_USAGE);
        // a typo when asked: nothing changes either
        assertThat(cli("someone else\n", true, "player", name, "reset-password"))
                .isEqualTo(AdminCli.EXIT_REFUSED);
        Browser.open(mockMvc)
                .login(name, Browser.PASSWORD)
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

        assertThat(
                        cli(
                                name + "\n",
                                true,
                                "player",
                                name,
                                "reset-password",
                                "--password",
                                "new secret 1"))
                .isZero();
        assertThat(out.toString()).contains("The password of " + name + " is changed.");
        Browser browser = Browser.open(mockMvc);
        browser.login(name, Browser.PASSWORD)
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));
        browser.login(name, "new secret 1")
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

        assertThat(cli("player", name, "reset-password", "--yes")).isZero();
        assertThat(out.toString())
                .contains("New password for " + name + ": ")
                .contains("(shown only now)");
    }

    @Test
    void theMenuRunsAChoiceAndQuits() {
        assertThat(cli("1\n0\n", true)).isZero();
        assertThat(out.toString())
                .contains("Bannerfall host console – ")
                .contains("1  Status")
                .contains("database ok");
    }

    private String name(TestPlayer player) {
        return jdbc.sql("select name from player where id = ?")
                .param(player.playerId)
                .query(String.class)
                .single();
    }

    private static WorldSettingsUpdateRequest request(AdminWorldSettingsDto s) {
        return new WorldSettingsUpdateRequest(
                s.name(),
                s.speed(),
                s.unitSpeed(),
                s.startResources(),
                s.luckRange(),
                s.beginnerProtectionHours(),
                s.nobleMaxDistance(),
                s.loyaltyRegenPerHour(),
                s.buildQueueSize(),
                s.buildQueueMax(),
                s.recruitQueueSize(),
                s.cancelWindowSeconds(),
                s.barbariansPerNewPlayer(),
                s.barbarianGrowthEnabled(),
                s.tradeMaxRatio(),
                s.inviteCode(),
                s.npcThinkMinutes(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                s.barbarianWallsEnabled(),
                s.barbarianWallStartDays(),
                s.barbarianWallMaxLevel(),
                s.barbarianWallsRebuild(),
                s.unitLevelsEnabled(),
                s.paladinVillagesPerSlot(),
                s.paladinMaxSlots(),
                s.bannersEnabled(),
                s.bannerDropFactor(),
                s.standardTheftEnabled(),
                s.npcBannersEnabled(),
                s.marketExchangeRatio(),
                s.victoryMode(),
                s.dominationPercent(),
                s.victoryHoldHours(),
                s.keepCount(),
                s.keepPercent(),
                s.villageNameLanguage(),
                s.nightBonusEnabled(),
                s.nightBonusStartHour(),
                s.nightBonusEndHour(),
                s.nightBonusFactor());
    }
}
