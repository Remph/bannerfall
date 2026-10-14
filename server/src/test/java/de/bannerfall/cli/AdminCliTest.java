package de.bannerfall.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The CLI without a server: argument parsing, usage errors, the token check, {@code logs}. */
class AdminCliTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(Map<String, String> env, String... args) {
        return AdminCli.run(
                args,
                env,
                new BufferedReader(new StringReader("")),
                new PrintWriter(out),
                new PrintWriter(err),
                false);
    }

    private int run(String... args) {
        // a port nothing listens on: commands that get past the parsing fail with exit 1
        return run(
                Map.of("BANNERFALL_ADMIN_TOKEN", "t", "BANNERFALL_URL", "http://127.0.0.1:9"),
                args);
    }

    @Test
    void helpListsTheCommands() {
        assertThat(run("--help")).isZero();
        assertThat(out.toString())
                .contains("status", "players", "player", "world", "events", "npc", "barbarians")
                .contains("broadcast", "cheats", "audit", "logs");
    }

    @Test
    void wrongUsageIsExitCode2() {
        assertThat(run("fly")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("events", "retry")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("events", "retry", "abc")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("npc", "spawn", "grumpy")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("player", "Ben", "role", "king")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("barbarians", "spawn", "1")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("cheats", "units", "5")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("cheats", "units", "5", "FOO=1")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("world", "set", "phase", "OPEN")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(err.toString()).contains("Unknown or fixed setting 'phase'");
    }

    @Test
    void worldOpenNeedsExactlyOneOfAtNowClear() {
        assertThat(run("world", "open")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(err.toString()).contains("exactly one of --at, --now, --clear");
        assertThat(run("world", "open", "--now", "--clear")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(run("world", "open", "--at", "next friday")).isEqualTo(AdminCli.EXIT_USAGE);
        assertThat(err.toString()).contains("Can't read the time 'next friday'");
    }

    @Test
    void theOpeningTimeIsBerlinTimeUnlessItHasAnOffset() {
        // 18:00 in October 2026 is CEST, UTC+2
        assertThat(AdminCli.WorldCommand.Open.parseTime("2026-10-17 18:00"))
                .isEqualTo(java.time.Instant.parse("2026-10-17T16:00:00Z"));
        assertThat(AdminCli.WorldCommand.Open.parseTime("2026-10-17T18:00"))
                .isEqualTo(java.time.Instant.parse("2026-10-17T16:00:00Z"));
        // winter: UTC+1
        assertThat(AdminCli.WorldCommand.Open.parseTime("2026-12-24 18:00"))
                .isEqualTo(java.time.Instant.parse("2026-12-24T17:00:00Z"));
        assertThat(AdminCli.WorldCommand.Open.parseTime("2026-10-17T16:00:00Z"))
                .isEqualTo(java.time.Instant.parse("2026-10-17T16:00:00Z"));
        assertThat(AdminCli.WorldCommand.Open.parseTime("2026-10-17T18:00:00+02:00"))
                .isEqualTo(java.time.Instant.parse("2026-10-17T16:00:00Z"));
    }

    @Test
    void aServerThatIsntThereIsExitCode1() {
        assertThat(run("status")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("Can't reach the server at http://127.0.0.1:9");
    }

    @Test
    void withoutTokenTheCliSaysSo() {
        assertThat(run(Map.of(), "status")).isEqualTo(AdminCli.EXIT_REFUSED);
        assertThat(err.toString()).contains("BANNERFALL_ADMIN_TOKEN is not set");
    }

    @Test
    void unitsAreParsedInAnyCaseAndAddedUp() {
        assertThat(AdminCli.parseUnits(List.of("spearman=20", "AXEMAN=5", "SPEARMAN=1")))
                .containsExactly(Map.entry("SPEARMAN", 21), Map.entry("AXEMAN", 5));
        assertThatThrownBy(() -> AdminCli.parseUnits(List.of("SPEARMAN")))
                .isInstanceOf(AdminCli.UsageException.class);
        assertThatThrownBy(() -> AdminCli.parseUnits(List.of("SPEARMAN=x")))
                .isInstanceOf(AdminCli.UsageException.class);
    }

    @Test
    void settingNamesAreKebabOnTheCommandLine() {
        assertThat(AdminCli.kebab("buildQueueSize")).isEqualTo("build-queue-size");
        assertThat(AdminCli.camel("build-queue-size")).isEqualTo("buildQueueSize");
        assertThat(AdminCli.camel("inviteCode")).isEqualTo("inviteCode");
        assertThat(AdminCli.WORLD_SETTINGS)
                .allSatisfy(s -> assertThat(AdminCli.camel(AdminCli.kebab(s))).isEqualTo(s));
    }

    @Test
    void durationsAreShort() {
        assertThat(AdminCli.duration(42)).isEqualTo("42 s");
        assertThat(AdminCli.duration(125)).isEqualTo("2 min");
        assertThat(AdminCli.duration(3 * 3600 + 5 * 60)).isEqualTo("3 h 5 min");
        assertThat(AdminCli.duration(3 * 86400 + 7200)).isEqualTo("3 d 2 h");
    }

    @Test
    void logsShowsTheLastLinesOfServerErr(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("server.err"), List.of("one", "two", "three"));
        Files.write(dir.resolve("server.log"), List.of("info"));
        Map<String, String> env = Map.of("BANNERFALL_LOG_DIR", dir.toString());

        assertThat(run(env, "logs", "--lines", "2")).isZero();
        assertThat(out.toString().lines()).containsExactly("two", "three");
        assertThat(run(env, "logs", "--all")).isZero();
        assertThat(out.toString()).endsWith("info" + System.lineSeparator());
        assertThat(run(Map.of("BANNERFALL_LOG_DIR", dir.resolve("nope").toString()), "logs"))
                .isEqualTo(AdminCli.EXIT_REFUSED);
    }
}
