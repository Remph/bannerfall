package de.bannerfall.cli;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.MapSpread;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The host console {@code bannerfall-admin} (08-host-console §5): a plain Java program in the app
 * jar, <b>without</b> a Spring context. It calls the running server's {@code /api/admin/**} with
 * the host token from {@code BANNERFALL_ADMIN_TOKEN} ({@link AdminClient}); all logic is on the
 * server. Without a command it opens a numbered menu ({@link Menu}).
 *
 * <p>Started by the wrapper script {@code ops/bannerfall-admin}: {@code docker compose exec app
 * bannerfall-admin status}. Exit codes: {@value #EXIT_OK} ok, {@value #EXIT_REFUSED} the server
 * refused (its {@code code} and detail are printed) or can't be reached, {@value #EXIT_USAGE} wrong
 * usage.
 */
@Command(
        name = "bannerfall-admin",
        mixinStandardHelpOptions = true,
        version = "bannerfall-admin (see `status` for the server's version)",
        description = {
            "The Bannerfall host console. Without a command: an interactive menu.",
            "Needs BANNERFALL_ADMIN_TOKEN (the server's host token); BANNERFALL_URL overrides the"
                    + " server address."
        },
        subcommands = {
            AdminCli.PlayerCommand.class,
            AdminCli.WorldCommand.class,
            AdminCli.EventsCommand.class,
            AdminCli.NpcCommand.class,
            AdminCli.BarbariansCommand.class,
            AdminCli.BannerCommand.class,
            AdminCli.CheatsCommand.class,
            AdminCli.PushCommand.class
        })
public class AdminCli implements Callable<Integer> {

    static final int EXIT_OK = 0;
    static final int EXIT_REFUSED = 1;
    static final int EXIT_USAGE = 2;

    static final String DEFAULT_URL = "http://localhost:8080";
    static final String DEFAULT_LOG_DIR = "/app/logs";

    /** The settings {@code PUT /api/admin/world-settings} takes (WorldSettingsUpdateRequest). */
    static final List<String> WORLD_SETTINGS =
            List.of(
                    "name",
                    "speed",
                    "unitSpeed",
                    "startResources",
                    "luckRange",
                    "beginnerProtectionHours",
                    "nobleMaxDistance",
                    "loyaltyRegenPerHour",
                    "buildQueueSize",
                    "buildQueueMax",
                    "recruitQueueSize",
                    "cancelWindowSeconds",
                    "barbariansPerNewPlayer",
                    "barbarianGrowthEnabled",
                    "tradeMaxRatio",
                    "inviteCode",
                    "npcThinkMinutes",
                    "guildsEnabled",
                    "guildMaxMembers",
                    "guildStartTogether",
                    "npcGuildsEnabled",
                    "npcGuildMaxMembers",
                    "barbarianWallsEnabled",
                    "barbarianWallStartDays",
                    "barbarianWallMaxLevel",
                    "barbarianWallsRebuild",
                    "unitLevelsEnabled",
                    "paladinVillagesPerSlot",
                    "paladinMaxSlots",
                    "bannersEnabled",
                    "bannerDropFactor",
                    "standardTheftEnabled",
                    "npcBannersEnabled",
                    "npcLearningEnabled",
                    "npcShipmentsEnabled",
                    "marketExchangeRatio",
                    "victoryMode",
                    "dominationPercent",
                    "victoryHoldHours",
                    "keepCount",
                    "keepPercent",
                    "villageNameLanguage",
                    "nightBonusEnabled",
                    "nightBonusStartHour",
                    "nightBonusEndHour",
                    "nightBonusFactor",
                    "archersEnabled",
                    "moraleEnabled",
                    "moraleFloor",
                    "noblePriceStep",
                    "automationVillages");

    /** Only in the pre-game (M14g): fixed once the world's content exists. */
    static final List<String> PREGAME_SETTINGS =
            List.of("mapSize", "mapSpread", "barbarianInitialCount", "npcLordCount");

    /** The time zone of `world open --at`: where the host and his players live. */
    static final java.time.ZoneId HOST_ZONE = java.time.ZoneId.of("Europe/Berlin");

    static final List<String> UNIT_TYPES =
            Arrays.stream(UnitType.values()).map(UnitType::name).toList();

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    // INHERIT: also after the command (`status --json`), not only before it (M14d review)
    @Option(
            names = "--url",
            scope = ScopeType.INHERIT,
            description = "Server address (default: BANNERFALL_URL or " + DEFAULT_URL + ")")
    String url;

    @Option(
            names = "--json",
            scope = ScopeType.INHERIT,
            description = "Print the server's raw JSON answer")
    boolean json;

    private final Map<String, String> env;
    private final BufferedReader in;
    private final PrintWriter out;
    private final PrintWriter err;
    private final boolean interactive;

    AdminCli(
            Map<String, String> env,
            BufferedReader in,
            PrintWriter out,
            PrintWriter err,
            boolean interactive) {
        this.env = env;
        this.in = in;
        this.out = out;
        this.err = err;
        this.interactive = interactive;
    }

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
        BufferedReader in =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.exit(run(args, System.getenv(), in, out, err, System.console() != null));
    }

    /**
     * Runs one command line; the testable entry point.
     *
     * @param interactive a person sits at a terminal: destructive commands may ask instead of
     *     needing {@code --yes}
     * @return the exit code
     */
    static int run(
            String[] args,
            Map<String, String> env,
            BufferedReader in,
            PrintWriter out,
            PrintWriter err,
            boolean interactive) {
        return new AdminCli(env, in, out, err, interactive).execute(args);
    }

    /**
     * Parses and runs {@code args} with this CLI's streams (the menu runs its choices this way).
     */
    int execute(String... args) {
        CommandLine commandLine = new CommandLine(this);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setExecutionExceptionHandler(
                (e, cmd, parsed) -> {
                    if (e instanceof CliException) {
                        err.println(e.getMessage());
                        return EXIT_REFUSED;
                    }
                    if (e instanceof UsageException) {
                        err.println(e.getMessage());
                        return EXIT_USAGE;
                    }
                    throw e;
                });
        int code = commandLine.execute(args);
        out.flush();
        err.flush();
        return code;
    }

    /** No command: the menu. */
    @Override
    public Integer call() {
        new Menu(this, in, out).run();
        return EXIT_OK;
    }

    // ---- shared helpers for the commands ----

    AdminClient client() {
        String base = url != null ? url : env.getOrDefault("BANNERFALL_URL", DEFAULT_URL);
        String token = env.get("BANNERFALL_ADMIN_TOKEN");
        return new AdminClient(base, token == null || token.isBlank() ? null : token, MAPPER);
    }

    PrintWriter out() {
        return out;
    }

    /** With {@code --json}: prints the answer and returns true, the caller prints nothing else. */
    boolean printedJson(JsonNode answer) {
        if (!json) {
            return false;
        }
        out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(answer));
        return true;
    }

    /**
     * Destructive commands (08 §5): {@code --yes}, or the host types the player's name. Without a
     * terminal and without {@code --yes} it's a usage error, so a script can't hang on a question.
     */
    void confirm(boolean yes, String what, String name) {
        if (yes) {
            return;
        }
        if (!interactive) {
            throw new UsageException(what + ": add --yes to confirm (no terminal to ask on)");
        }
        out.print(what + ". Type " + (name.contains(" ") ? "the name" : name) + " to confirm: ");
        out.flush();
        String typed = readLine();
        if (typed == null || !typed.strip().equalsIgnoreCase(name)) {
            throw new CliException("Not confirmed, nothing changed.");
        }
    }

    /** Reads a line without echo when a person sits at a terminal (passwords). */
    String readSecret(String prompt) {
        Console console = interactive ? System.console() : null;
        if (console == null) {
            out.print(prompt + " ");
            out.flush();
            return readLine();
        }
        char[] secret = console.readPassword("%s ", prompt);
        return secret == null ? null : new String(secret);
    }

    String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The human player with this name (any case), from {@code GET /api/admin/players}. */
    JsonNode findPlayer(AdminClient client, String name) {
        for (JsonNode player : client.get("/api/admin/players")) {
            if (name.equalsIgnoreCase(Json.text(player, "name"))) {
                return player;
            }
        }
        throw new CliException("No human player named '" + name + "' (see `players`)");
    }

    // ---- top-level commands ----

    @Command(name = "status", description = "Version, uptime, database, counts, event queue")
    int status() {
        JsonNode status = client().get("/api/admin/status");
        if (printedJson(status)) {
            return EXIT_OK;
        }
        JsonNode events = status.path("events");
        out.printf(
                "Bannerfall %s – up %s – database %s – %s%n",
                Json.show(status, "version"),
                duration(status.path("uptimeSeconds").asLong()),
                status.path("databaseOk").asBoolean() ? "ok" : "NOT OK",
                cheatsBanner(status));
        out.printf(
                "Players %d · villages %d (barbarian %d) · NPC lords %d%n",
                status.path("humanPlayers").asLong(),
                status.path("villages").asLong(),
                status.path("barbarianVillages").asLong(),
                status.path("npcLords").asLong());
        out.printf(
                "Events: %d pending, next %s, lag %s, %d failed%n",
                events.path("pending").asLong(),
                Json.time(events, "nextDueAt"),
                duration(events.path("lagSeconds").asLong()),
                events.path("failed").asLong());
        JsonNode abuse = status.path("abuse");
        out.printf(
                "Abuse: %d banned, %d suspicious accounts today, %d automatic locks today%s%n",
                abuse.path("bannedPlayers").asLong(),
                abuse.path("suspiciousAccounts").asLong(),
                abuse.path("autoLocks").asLong(),
                abuse.path("invariants").isNull()
                        ? ""
                        : abuse.path("invariants").path("problems").isEmpty()
                                ? ", last check clean"
                                : ", LAST CHECK FOUND PROBLEMS (run `check`)");
        JsonNode push = status.path("push");
        out.println(
                push.path("enabled").asBoolean()
                        ? "Push: on, %d devices of %d players"
                                .formatted(
                                        push.path("devices").asLong(),
                                        push.path("players").asLong())
                        : "Push: off (no keys, see `push keys`)");
        return EXIT_OK;
    }

    static String cheatsBanner(JsonNode status) {
        return status.path("cheatsEnabled").asBoolean() ? "CHEATS ON" : "CHEATS OFF";
    }

    @Command(name = "players", description = "All human players")
    int players() {
        JsonNode players = client().get("/api/admin/players");
        if (printedJson(players)) {
            return EXIT_OK;
        }
        Table table = new Table("Name", "Role", "Points", "Villages", "Last login", "");
        for (JsonNode p : players) {
            table.row(
                    Json.show(p, "name"),
                    Json.show(p, "role"),
                    p.path("points").asInt(),
                    p.path("villageCount").asInt(),
                    Json.time(p, "lastLoginAt"),
                    p.path("deleted").asBoolean() ? "deleted" : "");
        }
        table.print(out);
        return EXIT_OK;
    }

    @Command(name = "suspicious", description = "Accounts that sent requests the game never sends")
    int suspicious() {
        JsonNode accounts = client().get("/api/admin/suspicious");
        if (printedJson(accounts)) {
            return EXIT_OK;
        }
        if (accounts.isEmpty()) {
            out.println("Nobody has been suspicious in the last 30 days.");
            return EXIT_OK;
        }
        Table table = new Table("Player", "Events", "Kinds", "Last", "");
        for (JsonNode a : accounts) {
            StringBuilder kinds = new StringBuilder();
            a.path("counts")
                    .properties()
                    .forEach(
                            e ->
                                    kinds.append(kinds.isEmpty() ? "" : ", ")
                                            .append(e.getKey())
                                            .append(' ')
                                            .append(e.getValue().asLong()));
            table.row(
                    Json.show(a, "name"),
                    a.path("total").asLong(),
                    kinds.toString(),
                    Json.time(a, "lastAt"),
                    a.path("bannedUntil").isNull() ? "" : "BANNED");
        }
        table.print(out);
        return EXIT_OK;
    }

    @Command(
            name = "check",
            description = "Look for impossible states (negative amounts, orphans ...); read only")
    int check() {
        JsonNode report = client().post("/api/admin/invariants", null);
        if (printedJson(report)) {
            return EXIT_OK;
        }
        if (report.path("problems").isEmpty()) {
            out.println("Nothing wrong.");
            return EXIT_OK;
        }
        for (JsonNode p : report.path("problems")) {
            out.printf(
                    "%s × %d, e.g. %s%n",
                    Json.show(p, "kind"), p.path("count").asLong(), p.path("examples"));
        }
        return EXIT_REFUSED;
    }

    @Command(
            name = "setup",
            description =
                    "A one-time link that makes your account the admin and opens the setup wizard"
                            + " (pre-game only)")
    int setup() {
        JsonNode code = client().post("/api/admin/setup-code", null);
        if (printedJson(code)) {
            return EXIT_OK;
        }
        String domain = env.getOrDefault("BANNERFALL_DOMAIN", "").strip();
        String path = "/setup?code=" + Json.show(code, "code");
        out.println("Register or log in on your world, then open:");
        out.println(
                domain.isEmpty()
                        ? "  " + path + "  (after your world's address)"
                        : "  https://" + domain + path);
        out.println(
                "Your account becomes the admin and the setup wizard starts. The link works once,"
                        + " until "
                        + Json.time(code, "expiresAt")
                        + "; `setup` again makes a new one.");
        return EXIT_OK;
    }

    @Command(name = "broadcast", description = "A message from the host to everybody's chat")
    int broadcast(
            @Parameters(arity = "1..*", paramLabel = "TEXT", description = "The message")
                    List<String> words) {
        JsonNode message =
                client().post("/api/admin/broadcast", Map.of("text", String.join(" ", words)));
        if (!printedJson(message)) {
            out.println("Sent to everybody: " + Json.show(message, "text"));
        }
        return EXIT_OK;
    }

    @Command(name = "audit", description = "The newest admin actions")
    int audit(
            @Option(
                            names = "--limit",
                            defaultValue = "50",
                            description = "How many (1–500, default 50)")
                    int limit) {
        JsonNode actions = client().get("/api/admin/audit?limit=" + limit);
        if (printedJson(actions)) {
            return EXIT_OK;
        }
        Table table = new Table("When", "Who", "Action", "Target", "Details");
        for (JsonNode a : actions) {
            String target =
                    Json.text(a, "targetType") == null
                            ? ""
                            : Json.text(a, "targetType")
                                    + (Json.text(a, "targetId") == null
                                            ? ""
                                            : " " + Json.text(a, "targetId"));
            table.row(
                    Json.time(a, "at"),
                    Json.show(a, "actor"),
                    Json.show(a, "action"),
                    target,
                    MAPPER.writeValueAsString(a.path("details")));
        }
        table.print(out);
        return EXIT_OK;
    }

    /**
     * The last lines of the log files (08 §11 point 5): read straight from the disk, the CLI runs
     * in the server's container. No server call, so no token needed.
     */
    @Command(
            name = "logs",
            description = "The last lines of server.err (WARN/ERROR), or server.log with --all")
    int logs(
            @Option(names = "--lines", defaultValue = "50", description = "How many (default 50)")
                    int lines,
            @Option(names = "--all", description = "server.log (INFO and up) instead")
                    boolean all) {
        if (lines < 1) {
            throw new UsageException("--lines must be at least 1");
        }
        Path file =
                Path.of(
                        env.getOrDefault("BANNERFALL_LOG_DIR", DEFAULT_LOG_DIR),
                        all ? "server.log" : "server.err");
        if (!Files.isRegularFile(file)) {
            throw new CliException("No log file at " + file + " (BANNERFALL_LOG_DIR)");
        }
        Deque<String> last = new ArrayDeque<>(lines);
        try (Stream<String> stream = Files.lines(file, StandardCharsets.UTF_8)) {
            stream.forEach(
                    line -> {
                        if (last.size() == lines) {
                            last.removeFirst();
                        }
                        last.addLast(line);
                    });
        } catch (IOException e) {
            throw new CliException("Can't read " + file + ": " + e.getMessage());
        }
        if (last.isEmpty()) {
            out.println("(" + file + " is empty)");
        }
        last.forEach(out::println);
        return EXIT_OK;
    }

    static String duration(long seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " h " + (minutes % 60) + " min";
        }
        return (hours / 24) + " d " + (hours % 24) + " h";
    }

    // ---- player <name> [role|reset-password|delete] ----

    @Command(
            name = "player",
            description = "Show a player and his villages, or change him",
            subcommands = {
                PlayerCommand.Role.class,
                PlayerCommand.ResetPassword.class,
                PlayerCommand.Ban.class,
                PlayerCommand.Unban.class,
                PlayerCommand.RevokeAchievement.class,
                PlayerCommand.Delete.class
            })
    static class PlayerCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Parameters(index = "0", paramLabel = "NAME", description = "The player's name")
        String name;

        @Override
        public Integer call() {
            JsonNode player = cli.findPlayer(cli.client(), name);
            if (cli.printedJson(player)) {
                return EXIT_OK;
            }
            PrintWriter out = cli.out();
            out.printf(
                    "%s – %s – %d points – last login %s%s%n",
                    Json.show(player, "name"),
                    Json.show(player, "role"),
                    player.path("points").asInt(),
                    Json.time(player, "lastLoginAt"),
                    player.path("deleted").asBoolean() ? " – DELETED" : "");
            if (!player.path("bannedUntil").isNull() && player.has("bannedUntil")) {
                out.printf(
                        "BANNED %s: %s%n",
                        Json.banEnd(player, "bannedUntil"), Json.show(player, "banReason"));
            }
            Table villages = new Table("Village id", "Name", "Field", "Points");
            for (JsonNode v : player.path("villages")) {
                villages.row(
                        v.path("id").asLong(),
                        Json.show(v, "name"),
                        v.path("x").asInt() + "|" + v.path("y").asInt(),
                        v.path("points").asInt());
            }
            villages.print(out);
            return EXIT_OK;
        }

        enum RoleName {
            ADMIN,
            PLAYER
        }

        @Command(name = "role", description = "Make him an admin, or a player again")
        static class Role implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            @Parameters(index = "0", paramLabel = "admin|player")
            RoleName role;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player.name);
                String name = Json.text(found, "name");
                cli.confirm(yes, "Make " + name + " " + role.name().toLowerCase(Locale.ROOT), name);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/" + found.path("playerId").asLong() + "/role",
                                Map.of("role", role.name()));
                if (!cli.printedJson(answer)) {
                    cli.out().println(name + " is " + Json.show(answer, "role") + " now.");
                }
                return EXIT_OK;
            }
        }

        @Command(
                name = "reset-password",
                description = "Set a new password; without --password the server makes one up")
        static class ResetPassword implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            /**
             * {@code --password} alone asks for it without echo, so it stays out of the shell
             * history and the process list (M14d review); a value after it is taken as it is.
             */
            @Option(
                    names = "--password",
                    arity = "0..1",
                    interactive = true,
                    prompt = "New password (not shown): ",
                    description = {
                        "The new password (8–128 characters).",
                        "Without a value: asked for, not shown and not in the shell history."
                    })
            String password;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player.name);
                String name = Json.text(found, "name");
                cli.confirm(yes, "Reset the password of " + name, name);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("password", password);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/"
                                        + found.path("playerId").asLong()
                                        + "/reset-password",
                                body);
                if (cli.printedJson(answer)) {
                    return EXIT_OK;
                }
                String generated = Json.text(answer, "generatedPassword");
                cli.out()
                        .println(
                                generated == null
                                        ? "The password of " + name + " is changed."
                                        : "New password for "
                                                + name
                                                + ": "
                                                + generated
                                                + "   (shown only now)");
                return EXIT_OK;
            }
        }

        @Command(
                name = "ban",
                description =
                        "Ban him: no login, open sessions end. Permanent unless --days or --until"
                                + " is given; a permanent ban turns his villages barbarian")
        static class Ban implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            @Option(names = "--reason", required = true, description = "Shown to him at login")
            String reason;

            @Option(names = "--days", description = "A ban of this many days")
            Integer days;

            @Option(
                    names = "--until",
                    description = "A ban until this instant, e.g. 2026-12-24T18:00:00Z")
            String until;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                if (days != null && until != null) {
                    throw new CliException("Use --days or --until, not both");
                }
                JsonNode found = cli.findPlayer(client, player.name);
                String name = Json.text(found, "name");
                String end =
                        days != null
                                ? java.time.Instant.now()
                                        .plus(java.time.Duration.ofDays(days))
                                        .toString()
                                : until;
                cli.confirm(
                        yes, "Ban " + name + (end == null ? " for good" : " until " + end), name);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("until", end);
                body.put("reason", reason);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/" + found.path("playerId").asLong() + "/ban",
                                body);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    name
                                            + " is banned "
                                            + Json.banEnd(answer, "bannedUntil")
                                            + ".");
                }
                return EXIT_OK;
            }
        }

        @Command(name = "unban", description = "Lift his ban")
        static class Unban implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player.name);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/" + found.path("playerId").asLong() + "/unban",
                                null);
                if (!cli.printedJson(answer)) {
                    cli.out().println(Json.text(found, "name") + " can log in again.");
                }
                return EXIT_OK;
            }
        }

        @Command(
                name = "revoke-achievement",
                description = "Take an achievement away for good (he can't earn it again)")
        static class RevokeAchievement implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            @Parameters(index = "0", description = "The key, e.g. VICTOR or FIRST_BLOOD")
            String key;

            @Option(names = "--reason", description = "Goes into the audit log")
            String reason;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player.name);
                String name = Json.text(found, "name");
                cli.confirm(
                        yes, "Take " + key.toUpperCase(Locale.ROOT) + " away from " + name, name);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("reason", reason);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/"
                                        + found.path("playerId").asLong()
                                        + "/achievements/"
                                        + key.toUpperCase(Locale.ROOT)
                                        + "/revoke",
                                body);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    answer.path("changed").asBoolean()
                                            ? name + " lost " + key.toUpperCase(Locale.ROOT) + "."
                                            : name
                                                    + " had lost "
                                                    + key.toUpperCase(Locale.ROOT)
                                                    + " before.");
                }
                return EXIT_OK;
            }
        }

        @Command(
                name = "delete",
                description = "Delete him: his villages become barbarian, he can't log in")
        static class Delete implements Callable<Integer> {

            @ParentCommand PlayerCommand player;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = player.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player.name);
                String name = Json.text(found, "name");
                cli.confirm(yes, "Delete " + name + " (his villages become barbarian)", name);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/" + found.path("playerId").asLong() + "/delete",
                                null);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    name
                                            + " is deleted; "
                                            + answer.path("villagesConverted").asInt()
                                            + " villages are barbarian now.");
                }
                return EXIT_OK;
            }
        }
    }

    // ---- world [set <setting> <value>] ----

    @Command(
            name = "world",
            description = "Show the world settings",
            subcommands = {
                WorldCommand.Set.class,
                WorldCommand.Open.class,
                WorldCommand.End.class,
                WorldCommand.Reopen.class
            })
    static class WorldCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Override
        public Integer call() {
            JsonNode settings = cli.client().get("/api/admin/world-settings");
            if (cli.printedJson(settings)) {
                return EXIT_OK;
            }
            Table table = new Table("Setting", "Value", "");
            for (Map.Entry<String, JsonNode> entry : settings.properties()) {
                table.row(
                        kebab(entry.getKey()),
                        entry.getValue().asString(),
                        worldNote(entry.getKey()));
            }
            table.print(cli.out());
            return EXIT_OK;
        }

        private static String worldNote(String key) {
            if (WORLD_SETTINGS.contains(key)) {
                return "";
            }
            if (PREGAME_SETTINGS.contains(key)) {
                return "(before opening)";
            }
            if (key.equals("endsAt") || key.equals("endedAt")) {
                return "(see `world end`)";
            }
            return key.equals("phase") || key.equals("opensAt") ? "(see `world open`)" : "(fixed)";
        }

        @Command(
                name = "open",
                description =
                        "Pre-game: set the time the world opens (--at \"2026-10-17 18:00\", Berlin"
                                + " time), take it back (--clear) or open it right away (--now)")
        static class Open implements Callable<Integer> {

            @ParentCommand WorldCommand world;

            @Option(
                    names = "--at",
                    description = "e.g. \"2026-10-17 18:00\" (Berlin time) or 2026-10-17T16:00:00Z")
            String at;

            @Option(names = "--now", description = "Open the world right away")
            boolean now;

            @Option(names = "--clear", description = "No opening time: the world waits")
            boolean clear;

            @Option(names = "--yes", description = "Don't ask (--now)")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = world.cli;
                if ((at != null ? 1 : 0) + (now ? 1 : 0) + (clear ? 1 : 0) != 1) {
                    throw new UsageException("Give exactly one of --at, --now, --clear");
                }
                AdminClient client = cli.client();
                JsonNode answer;
                if (now) {
                    cli.confirm(yes, "Open the world now", "open");
                    answer = client.post("/api/admin/world-opening/now", null);
                } else {
                    ObjectNode body = MAPPER.createObjectNode();
                    body.put("opensAt", clear ? null : parseTime(at).toString());
                    answer = client.put("/api/admin/world-opening", body);
                }
                if (!cli.printedJson(answer)) {
                    String opensAt = Json.text(answer, "opensAt");
                    cli.out()
                            .println(
                                    opensAt == null
                                            ? "The world waits: no opening time is set."
                                            : "The world opens at "
                                                    + java.time.Instant.parse(opensAt)
                                                            .atZone(HOST_ZONE)
                                                            .toLocalDateTime()
                                                            .toString()
                                                            .replace('T', ' ')
                                                    + " (Berlin time).");
                }
                return EXIT_OK;
            }

            /** "2026-10-17 18:00" in Berlin time, or a full instant with Z / an offset. */
            static java.time.Instant parseTime(String text) {
                String trimmed = text.strip();
                try {
                    return java.time.OffsetDateTime.parse(trimmed).toInstant();
                } catch (java.time.format.DateTimeParseException notAnOffsetTime) {
                    // try the local form below
                }
                try {
                    return java.time.LocalDateTime.parse(trimmed.replace(' ', 'T'))
                            .atZone(HOST_ZONE)
                            .toInstant();
                } catch (java.time.format.DateTimeParseException e) {
                    throw new UsageException(
                            "Can't read the time '"
                                    + text
                                    + "'. Use e.g. \"2026-10-17 18:00\" (Berlin time)");
                }
            }
        }

        @Command(
                name = "end",
                description =
                        "Set the end date (--at \"2026-11-07 20:00\", Berlin time; the side with"
                                + " the most points wins then), take it back (--clear) or end the"
                                + " world right away (--now)")
        static class End implements Callable<Integer> {

            @ParentCommand WorldCommand world;

            @Option(
                    names = "--at",
                    description = "e.g. \"2026-11-07 20:00\" (Berlin time) or 2026-11-07T19:00:00Z")
            String at;

            @Option(names = "--now", description = "End the world right away")
            boolean now;

            @Option(
                    names = "--clear",
                    description = "No end date: the world runs until a side wins")
            boolean clear;

            @Option(names = "--yes", description = "Don't ask (--now)")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = world.cli;
                if ((at != null ? 1 : 0) + (now ? 1 : 0) + (clear ? 1 : 0) != 1) {
                    throw new UsageException("Give exactly one of --at, --now, --clear");
                }
                AdminClient client = cli.client();
                JsonNode answer;
                if (now) {
                    cli.confirm(yes, "End the world now", "end");
                    answer = client.post("/api/admin/world-end/now", null);
                } else {
                    ObjectNode body = MAPPER.createObjectNode();
                    body.put("endsAt", clear ? null : Open.parseTime(at).toString());
                    answer = client.put("/api/admin/world-end", body);
                }
                if (!cli.printedJson(answer)) {
                    String endsAt = Json.text(answer, "endsAt");
                    cli.out()
                            .println(
                                    now
                                            ? "The world ends within a moment."
                                            : endsAt == null
                                                    ? "No end date: the world runs until a side"
                                                            + " wins."
                                                    : "The world ends at "
                                                            + java.time.Instant.parse(endsAt)
                                                                    .atZone(HOST_ZONE)
                                                                    .toLocalDateTime()
                                                                    .toString()
                                                                    .replace('T', ' ')
                                                            + " (Berlin time).");
                }
                return EXIT_OK;
            }
        }

        @Command(
                name = "reopen",
                description = "Take the end of the world back: it runs again, without an end date")
        static class Reopen implements Callable<Integer> {

            @ParentCommand WorldCommand world;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = world.cli;
                cli.confirm(yes, "Take the end of the world back", "reopen");
                JsonNode answer = cli.client().post("/api/admin/world-end/reopen", null);
                if (!cli.printedJson(answer)) {
                    cli.out().println("The world runs again.");
                }
                return EXIT_OK;
            }
        }

        @Command(name = "set", description = "Change one setting, e.g. `world set invite-code x`")
        static class Set implements Callable<Integer> {

            @ParentCommand WorldCommand world;

            @Parameters(index = "0", paramLabel = "SETTING", description = "e.g. invite-code")
            String setting;

            @Parameters(index = "1", paramLabel = "VALUE")
            String value;

            @Override
            public Integer call() {
                AdminCli cli = world.cli;
                String key = camel(setting);
                if (!WORLD_SETTINGS.contains(key) && !PREGAME_SETTINGS.contains(key)) {
                    throw new UsageException(
                            "Unknown or fixed setting '"
                                    + setting
                                    + "'. Changeable: "
                                    + String.join(
                                            ", ",
                                            Stream.concat(
                                                            WORLD_SETTINGS.stream(),
                                                            PREGAME_SETTINGS.stream())
                                                    .map(AdminCli::kebab)
                                                    .toList()));
                }
                AdminClient client = cli.client();
                JsonNode current = client.get("/api/admin/world-settings");
                ObjectNode body = MAPPER.createObjectNode();
                for (String name : WORLD_SETTINGS) {
                    body.set(name, current.get(name));
                }
                boolean newSpread =
                        key.equals("mapSpread")
                                && "PREGAME".equals(current.path("phase").asString());
                if (key.equals("mapSpread")) {
                    MapSpread spread = parseSpread(value);
                    body.put("mapSpread", spread.name());
                    if (newSpread) {
                        // it brings its map size and noble range, like on the admin page
                        body.put("mapSize", spread.defaultMapSize());
                        body.put("nobleMaxDistance", spread.defaultNobleMaxDistance());
                    }
                } else {
                    body.set(key, parseValue(current.get(key), value));
                }
                JsonNode answer = client.put("/api/admin/world-settings", body);
                if (!cli.printedJson(answer)) {
                    cli.out().println(setting + " = " + answer.get(key).asString());
                    if (newSpread) {
                        cli.out()
                                .println(
                                        "map-size = "
                                                + answer.get("mapSize").asString()
                                                + ", noble-max-distance = "
                                                + answer.get("nobleMaxDistance").asString()
                                                + " (change them with `world set` if you like)");
                    }
                }
                return EXIT_OK;
            }

            private static MapSpread parseSpread(String text) {
                try {
                    return MapSpread.valueOf(text.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new UsageException(
                            "Expected CLOSE, NORMAL, WIDE or VAST, got '" + text + "'");
                }
            }

            /** The value in the type the setting has now (number, true/false or text). */
            private static JsonNode parseValue(JsonNode currentValue, String text) {
                if (currentValue != null && currentValue.isBoolean()) {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                        throw new UsageException("Expected true or false, got '" + text + "'");
                    }
                    return MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(text));
                }
                if (currentValue != null && currentValue.isNumber()) {
                    try {
                        return currentValue.isIntegralNumber()
                                ? MAPPER.getNodeFactory().numberNode(Integer.parseInt(text))
                                : MAPPER.getNodeFactory().numberNode(Double.parseDouble(text));
                    } catch (NumberFormatException e) {
                        throw new UsageException("Expected a number, got '" + text + "'");
                    }
                }
                return MAPPER.getNodeFactory().stringNode(text);
            }
        }
    }

    /** buildQueueSize → build-queue-size */
    static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /** build-queue-size → buildQueueSize (a camelCase name stays as it is) */
    static String camel(String kebab) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (char c : kebab.toCharArray()) {
            if (c == '-' || c == '_') {
                upper = true;
            } else {
                camel.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return camel.toString();
    }

    // ---- events failed | retry <id> ----

    @Command(
            name = "events",
            description = "Failed events",
            subcommands = {EventsCommand.Failed.class, EventsCommand.Retry.class})
    static class EventsCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        @Command(name = "failed", description = "Events the worker gave up on")
        static class Failed implements Callable<Integer> {

            @ParentCommand EventsCommand events;

            @Override
            public Integer call() {
                AdminCli cli = events.cli;
                JsonNode failed = cli.client().get("/api/admin/events/failed");
                if (cli.printedJson(failed)) {
                    return EXIT_OK;
                }
                Table table = new Table("Id", "Kind", "About", "Attempts", "Failed at", "Error");
                for (JsonNode e : failed) {
                    String error = Json.show(e, "lastError").lines().findFirst().orElse("");
                    table.row(
                            e.path("id").asLong(),
                            Json.show(e, "kind"),
                            Json.show(e, "refId"),
                            e.path("attempts").asInt(),
                            Json.time(e, "processedAt"),
                            error.length() > 80 ? error.substring(0, 79) + "…" : error);
                }
                table.print(cli.out());
                return EXIT_OK;
            }
        }

        @Command(name = "retry", description = "Put a failed event back into the queue")
        static class Retry implements Callable<Integer> {

            @ParentCommand EventsCommand events;

            @Parameters(index = "0", paramLabel = "ID")
            long id;

            @Override
            public Integer call() {
                events.cli.client().post("/api/admin/events/" + id + "/retry", null);
                events.cli.out().println("Event " + id + " is pending again.");
                return EXIT_OK;
            }
        }
    }

    // ---- npc list | spawn <personality> | difficulty [<level>] | guild <lord> <action> ----

    @Command(
            name = "npc",
            description = "NPC lords",
            subcommands = {
                NpcCommand.ListLords.class,
                NpcCommand.Spawn.class,
                NpcCommand.Level.class,
                NpcCommand.GuildPlace.class
            })
    static class NpcCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        enum Personality {
            BALANCED,
            OFFENSIVE,
            DEFENSIVE
        }

        enum Difficulty {
            EASY,
            NORMAL,
            HARD,
            CUSTOM
        }

        @Command(name = "list", description = "All NPC lords")
        static class ListLords implements Callable<Integer> {

            @ParentCommand NpcCommand npc;

            @Override
            public Integer call() {
                AdminCli cli = npc.cli;
                JsonNode lords = cli.client().get("/api/admin/npc-lords");
                if (cli.printedJson(lords)) {
                    return EXIT_OK;
                }
                Table table =
                        new Table(
                                "Name",
                                "Personality",
                                "Points",
                                "Villages",
                                "Guild",
                                "Banners",
                                "Learned",
                                "Next round");
                for (JsonNode lord : lords) {
                    String guild =
                            lord.path("guildTag").isString()
                                    ? lord.path("guildTag").asString()
                                            + (lord.path("guildByAdmin").asBoolean()
                                                    ? " (placed)"
                                                    : "")
                                    : lord.path("guildByAdmin").asBoolean() ? "- (placed)" : "-";
                    table.row(
                            Json.show(lord, "name"),
                            Json.show(lord, "personality"),
                            lord.path("points").asInt(),
                            lord.path("villageCount").asInt(),
                            guild,
                            banners(lord),
                            learned(lord),
                            Json.time(lord, "nextThinkAt"));
                }
                table.print(cli.out());
                return EXIT_OK;
            }
        }

        /**
         * Lord banners in short (09-npc-lords §14.8): equipped ones, the stash and the paladin
         * level, e.g. "2 up, 3 in stash, paladin 2".
         */
        private static String banners(JsonNode lord) {
            int up = 0;
            for (JsonNode village : lord.path("bannerVillages")) {
                up +=
                        (village.path("banner").isObject() ? 1 : 0)
                                + (village.path("standard").isObject() ? 1 : 0);
            }
            int stash = 0;
            for (JsonNode entry : lord.path("stash")) {
                stash += entry.path("count").asInt();
            }
            return up
                    + " up, "
                    + stash
                    + " in stash, paladin "
                    + lord.path("paladinLevel").asInt(1);
        }

        /**
         * Learning lords in short (09-npc-lords §15.5): how much each lesson moved the mix and how
         * many wall goals stand, e.g. "attacks 30 %, wins 5 %, 1 wall"; "-" while nothing is
         * learned or learning is off.
         */
        static String learned(JsonNode lord) {
            JsonNode learning = lord.path("learning");
            if (!learning.isObject()) {
                return "-";
            }
            String text =
                    "attacks "
                            + Math.round(learning.path("defenceWeight").asDouble() * 100)
                            + " %, wins "
                            + Math.round(learning.path("attackWeight").asDouble() * 100)
                            + " %";
            int walls = learning.path("walls").size();
            return walls == 0 ? text : text + ", " + walls + (walls == 1 ? " wall" : " walls");
        }

        @Command(name = "spawn", description = "A new NPC lord")
        static class Spawn implements Callable<Integer> {

            @ParentCommand NpcCommand npc;

            @Parameters(index = "0", paramLabel = "balanced|offensive|defensive")
            Personality personality;

            @Override
            public Integer call() {
                AdminCli cli = npc.cli;
                JsonNode lord =
                        cli.client()
                                .post("/api/admin/npc-lords", Map.of("personality", personality));
                if (!cli.printedJson(lord)) {
                    cli.out()
                            .println(
                                    Json.show(lord, "name")
                                            + " ("
                                            + Json.show(lord, "personality")
                                            + ") has joined the world.");
                }
                return EXIT_OK;
            }
        }

        @Command(name = "difficulty", description = "Show or set the NPC difficulty")
        static class Level implements Callable<Integer> {

            @ParentCommand NpcCommand npc;

            @Parameters(index = "0", arity = "0..1", paramLabel = "easy|normal|hard|custom")
            Difficulty difficulty;

            @Override
            public Integer call() {
                AdminCli cli = npc.cli;
                JsonNode answer =
                        difficulty == null
                                ? cli.client().get("/api/admin/npc-settings")
                                : cli.client()
                                        .put(
                                                "/api/admin/npc-settings",
                                                Map.of("difficulty", difficulty));
                if (!cli.printedJson(answer)) {
                    cli.out().println("NPC difficulty: " + Json.show(answer, "difficulty"));
                }
                return EXIT_OK;
            }
        }

        enum GuildAction {
            JOIN,
            LEAVE,
            FOUND,
            AUTO
        }

        @Command(
                name = "guild",
                description =
                        "Place an NPC lord: join <tag> (into that lord guild), leave, found (a new"
                                + " lord guild), auto (let it choose again)")
        static class GuildPlace implements Callable<Integer> {

            @ParentCommand NpcCommand npc;

            @Parameters(index = "0", paramLabel = "<lord>", description = "Name or player id")
            String lord;

            @Parameters(index = "1", paramLabel = "join|leave|found|auto")
            GuildAction action;

            @Parameters(index = "2", arity = "0..1", paramLabel = "<tag>")
            String tag;

            @Override
            public Integer call() {
                AdminCli cli = npc.cli;
                JsonNode lords = cli.client().get("/api/admin/npc-lords");
                JsonNode found = null;
                Long guildId = null;
                for (JsonNode l : lords) {
                    if (Json.show(l, "name").equalsIgnoreCase(lord)
                            || Long.toString(l.path("playerId").asLong()).equals(lord)) {
                        found = l;
                    }
                    if (tag != null
                            && l.path("guildTag").isString()
                            && l.path("guildTag").asString().equalsIgnoreCase(tag)) {
                        guildId = l.path("guildId").asLong();
                    }
                }
                if (found == null) {
                    throw new CliException("No NPC lord called " + lord);
                }
                if (action == GuildAction.JOIN && guildId == null) {
                    throw new CliException(
                            tag == null
                                    ? "join needs the tag of a lord guild"
                                    : "No lord guild " + tag);
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("action", action);
                body.put("guildId", guildId);
                JsonNode answer =
                        cli.client()
                                .put(
                                        "/api/admin/npc-lords/"
                                                + found.path("playerId").asLong()
                                                + "/guild",
                                        body);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    Json.show(answer, "name")
                                            + ": "
                                            + (answer.path("guildName").isString()
                                                    ? answer.path("guildName").asString()
                                                    : "no guild")
                                            + (answer.path("guildByAdmin").asBoolean()
                                                    ? " (placed)"
                                                    : " (chooses on its own)"));
                }
                return EXIT_OK;
            }
        }
    }

    // ---- barbarians spawn <x> <y> ----

    @Command(
            name = "barbarians",
            description = "Barbarian villages",
            subcommands = BarbariansCommand.Spawn.class)
    static class BarbariansCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        @Command(name = "spawn", description = "A barbarian village on exactly this field")
        static class Spawn implements Callable<Integer> {

            @ParentCommand BarbariansCommand barbarians;

            @Parameters(index = "0", paramLabel = "X")
            int x;

            @Parameters(index = "1", paramLabel = "Y")
            int y;

            @Override
            public Integer call() {
                AdminCli cli = barbarians.cli;
                JsonNode village =
                        cli.client().post("/api/admin/barbarians", Map.of("x", x, "y", y));
                if (!cli.printedJson(village)) {
                    cli.out()
                            .println(
                                    "Barbarian village "
                                            + village.path("id").asLong()
                                            + " on "
                                            + x
                                            + "|"
                                            + y
                                            + ".");
                }
                return EXIT_OK;
            }
        }
    }

    // ---- push keys ----

    @Command(
            name = "push",
            description = "Push notifications (1.5)",
            subcommands = {PushCommand.Keys.class})
    static class PushCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        /**
         * A new VAPID key pair as two {@code .env} lines; made here, without the server. A new pair
         * makes every device sign up again (it happens by itself when the player next opens the
         * game).
         */
        @Command(
                name = "keys",
                description = {
                    "Prints a new key pair for push notifications as two .env lines.",
                    "Add them to the world's .env once and recreate the app container."
                })
        static class Keys implements Callable<Integer> {

            @ParentCommand PushCommand push;

            @Override
            public Integer call() {
                de.bannerfall.push.VapidKeys keys = de.bannerfall.push.VapidKeys.generate();
                PrintWriter out = push.cli.out();
                out.println("BANNERFALL_PUSH_PUBLIC_KEY=" + keys.publicKeyText());
                out.println("BANNERFALL_PUSH_PRIVATE_KEY=" + keys.privateKeyText());
                return EXIT_OK;
            }
        }
    }

    // ---- banner list <player> | grant <player> <TYPE> <tier> | revoke <id> ----

    @Command(
            name = "banner",
            description = "Banners of the players (02-game-rules §21)",
            subcommands = {
                BannerCommand.ListBanners.class,
                BannerCommand.Grant.class,
                BannerCommand.Revoke.class
            })
    static class BannerCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        enum Type {
            HARVEST,
            BUILDERS,
            DRILL,
            WAR,
            BULWARK,
            HASTE,
            TREASURY,
            PLUNDER,
            OATH,
            // paladin standards (1.4)
            SPEAR_STANDARD,
            SWORD_STANDARD,
            AXE_STANDARD,
            LANCE_STANDARD,
            KNIGHT_STANDARD,
            RAM_STANDARD,
            SIEGE_STANDARD
        }

        @Command(name = "list", description = "A player's banners: stash and equipped")
        static class ListBanners implements Callable<Integer> {

            @ParentCommand BannerCommand banner;

            @Parameters(index = "0", paramLabel = "PLAYER", description = "The player's name")
            String player;

            @Override
            public Integer call() {
                AdminCli cli = banner.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player);
                JsonNode banners =
                        client.get(
                                "/api/admin/players/"
                                        + found.path("playerId").asLong()
                                        + "/banners");
                if (cli.printedJson(banners)) {
                    return EXIT_OK;
                }
                Table table = new Table("Id", "Type", "Tier", "Flies in village");
                for (JsonNode b : banners) {
                    table.row(
                            b.path("id").asLong(),
                            Json.show(b, "type"),
                            b.path("tier").asInt(),
                            b.path("villageId").isNull()
                                    ? "(stash)"
                                    : b.path("villageId").asLong());
                }
                table.print(cli.out());
                return EXIT_OK;
            }
        }

        @Command(
                name = "grant",
                description = "Give a player a new banner in his stash (the audit log keeps it)")
        static class Grant implements Callable<Integer> {

            @ParentCommand BannerCommand banner;

            @Parameters(index = "0", paramLabel = "PLAYER", description = "The player's name")
            String player;

            @Parameters(
                    index = "1",
                    paramLabel = "TYPE",
                    description = "HARVEST, WAR, ..., AXE_STANDARD, ...")
            Type type;

            @Parameters(index = "2", paramLabel = "TIER", description = "1, 2 or 3")
            int tier;

            @Option(names = "--reason", description = "Goes into the audit log")
            String reason;

            @Override
            public Integer call() {
                AdminCli cli = banner.cli;
                AdminClient client = cli.client();
                JsonNode found = cli.findPlayer(client, player);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", type.name());
                body.put("tier", tier);
                body.put("reason", reason);
                JsonNode answer =
                        client.post(
                                "/api/admin/players/"
                                        + found.path("playerId").asLong()
                                        + "/banners",
                                body);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    Json.text(found, "name")
                                            + " got banner "
                                            + answer.path("id").asLong()
                                            + " ("
                                            + type
                                            + ", tier "
                                            + tier
                                            + ") in his stash.");
                }
                return EXIT_OK;
            }
        }

        @Command(name = "revoke", description = "Take a banner away for good, wherever it is")
        static class Revoke implements Callable<Integer> {

            @ParentCommand BannerCommand banner;

            @Parameters(
                    index = "0",
                    paramLabel = "ID",
                    description = "The banner's id (`banner list`)")
            long id;

            @Option(names = "--reason", description = "Goes into the audit log")
            String reason;

            @Option(names = "--yes", description = "Don't ask")
            boolean yes;

            @Override
            public Integer call() {
                AdminCli cli = banner.cli;
                cli.confirm(yes, "Take banner " + id + " away for good", String.valueOf(id));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("reason", reason);
                JsonNode answer = cli.client().post("/api/admin/banners/" + id + "/revoke", body);
                if (!cli.printedJson(answer)) {
                    cli.out()
                            .println(
                                    "Banner "
                                            + id
                                            + " ("
                                            + Json.show(answer, "type")
                                            + ", tier "
                                            + answer.path("tier").asInt()
                                            + ") is gone.");
                }
                return EXIT_OK;
            }
        }
    }

    // ---- cheats (only while the server has bannerfall.cheats.enabled) ----

    @Command(
            name = "cheats",
            description = "Host cheats on any village (only when the server has them switched on)",
            subcommands = {
                CheatsCommand.Give.class,
                CheatsCommand.FinishOrders.class,
                CheatsCommand.FinishMovements.class,
                CheatsCommand.Units.class
            })
    static class CheatsCommand implements Callable<Integer> {

        @ParentCommand AdminCli cli;

        @Spec CommandSpec spec;

        @Override
        public Integer call() {
            throw new ParameterException(spec.commandLine(), "Missing command");
        }

        /** A 404 NOT_FOUND on the path means the switch is off; say so instead of "not found". */
        void post(long villageId, String cheat, Object body) {
            try {
                cli.client().post("/api/admin/cheats/villages/" + villageId + "/" + cheat, body);
            } catch (CliException e) {
                if (e.getMessage().contains("404 NOT_FOUND")) {
                    throw new CliException(
                            "The cheats are off on this server (BANNERFALL_CHEATS_ENABLED).");
                }
                throw e;
            }
        }

        @Command(name = "give", description = "Add resources (ignores the warehouse)")
        static class Give implements Callable<Integer> {

            @ParentCommand CheatsCommand cheats;

            @Parameters(index = "0", paramLabel = "VILLAGE_ID")
            long villageId;

            @Parameters(index = "1", paramLabel = "WOOD")
            int wood;

            @Parameters(index = "2", paramLabel = "CLAY")
            int clay;

            @Parameters(index = "3", paramLabel = "IRON")
            int iron;

            @Override
            public Integer call() {
                cheats.post(
                        villageId, "resources", Map.of("wood", wood, "clay", clay, "iron", iron));
                cheats.cli
                        .out()
                        .printf(
                                "Village %d got %d wood, %d clay, %d iron.%n",
                                villageId, wood, clay, iron);
                return EXIT_OK;
            }
        }

        @Command(name = "finish-orders", description = "Finish all build and recruit orders now")
        static class FinishOrders implements Callable<Integer> {

            @ParentCommand CheatsCommand cheats;

            @Parameters(index = "0", paramLabel = "VILLAGE_ID")
            long villageId;

            @Override
            public Integer call() {
                cheats.post(villageId, "finish-orders", null);
                cheats.cli.out().println("Orders of village " + villageId + " are finished.");
                return EXIT_OK;
            }
        }

        @Command(
                name = "finish-movements",
                description = "Let the village's troops and merchants arrive now")
        static class FinishMovements implements Callable<Integer> {

            @ParentCommand CheatsCommand cheats;

            @Parameters(index = "0", paramLabel = "VILLAGE_ID")
            long villageId;

            @Override
            public Integer call() {
                cheats.post(villageId, "finish-movements", null);
                cheats.cli.out().println("Movements of village " + villageId + " have arrived.");
                return EXIT_OK;
            }
        }

        @Command(name = "units", description = "Add units at home, e.g. SPEARMAN=20 AXEMAN=5")
        static class Units implements Callable<Integer> {

            @ParentCommand CheatsCommand cheats;

            @Parameters(index = "0", paramLabel = "VILLAGE_ID")
            long villageId;

            @Parameters(index = "1..*", arity = "1..*", paramLabel = "UNIT=COUNT")
            List<String> units;

            @Override
            public Integer call() {
                cheats.post(villageId, "units", Map.of("units", parseUnits(units)));
                cheats.cli
                        .out()
                        .println("Village " + villageId + " got " + String.join(" ", units));
                return EXIT_OK;
            }
        }
    }

    /** {@code SPEARMAN=20} (any case) → {@code {"SPEARMAN": 20}}. */
    static Map<String, Integer> parseUnits(List<String> pairs) {
        Map<String, Integer> units = new LinkedHashMap<>();
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String unit = parts[0].strip().toUpperCase(Locale.ROOT);
            if (parts.length != 2 || !UNIT_TYPES.contains(unit)) {
                throw new UsageException(
                        "Expected UNIT=COUNT with UNIT one of "
                                + String.join(", ", UNIT_TYPES)
                                + ", got '"
                                + pair
                                + "'");
            }
            try {
                units.merge(unit, Integer.parseInt(parts[1].strip()), Integer::sum);
            } catch (NumberFormatException e) {
                throw new UsageException("Not a number in '" + pair + "'");
            }
        }
        return units;
    }

    /** Wrong usage found after picocli's own parsing (exit code {@value #EXIT_USAGE}). */
    static class UsageException extends RuntimeException {

        UsageException(String message) {
            super(message);
        }
    }
}
