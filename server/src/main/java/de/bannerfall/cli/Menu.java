package de.bannerfall.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The interactive menu (08-host-console §5) when {@code bannerfall-admin} starts without a command.
 * Each choice asks for what it needs and then runs the same command a script would ({@link
 * AdminCli#execute}), so menu and commands can't drift apart. Enter on an empty prompt goes back.
 */
class Menu {

    private final AdminCli cli;
    private final BufferedReader in;
    private final PrintWriter out;

    Menu(AdminCli cli, BufferedReader in, PrintWriter out) {
        this.cli = cli;
        this.in = in;
        this.out = out;
    }

    void run() {
        while (true) {
            JsonNode status = header();
            boolean cheats = status != null && status.path("cheatsEnabled").asBoolean();
            out.println("  1  Status              6  Barbarians");
            out.println("  2  Players             7  Broadcast");
            out.println("  3  World settings      8  Cheats" + (cheats ? "" : " (off)"));
            out.println("  4  Failed events       9  Audit log");
            out.println("  5  NPC lords           0  Quit");
            String choice = ask(">");
            if (choice == null || choice.equals("0") || choice.equalsIgnoreCase("q")) {
                return;
            }
            switch (choice) {
                case "1" -> cli.execute("status");
                case "2" -> players();
                case "3" -> world();
                case "4" -> events();
                case "5" -> npc();
                case "6" -> barbarians();
                case "7" -> broadcast();
                case "8" -> cheats(cheats);
                case "9" -> cli.execute("audit", "--limit", "20");
                case "" -> {}
                default -> out.println("Pick a number from the menu.");
            }
            out.println();
        }
    }

    /** "Bannerfall host console – 3 players, 12 villages – CHEATS OFF"; null if that failed. */
    private JsonNode header() {
        try {
            JsonNode status = cli.client().get("/api/admin/status");
            out.printf(
                    "Bannerfall host console – %d players, %d villages – %s%n",
                    status.path("humanPlayers").asLong(),
                    status.path("villages").asLong(),
                    AdminCli.cheatsBanner(status));
            return status;
        } catch (CliException e) {
            out.println("Bannerfall host console – " + e.getMessage());
            return null;
        }
    }

    private void players() {
        cli.execute("players");
        String name = ask("Player name (Enter = back)");
        if (blank(name)) {
            return;
        }
        if (cli.execute("player", name) != AdminCli.EXIT_OK) {
            return;
        }
        String action = ask("a = make admin, p = make player, r = reset password, d = delete");
        switch (blank(action) ? "" : action.strip().toLowerCase()) {
            case "a" -> cli.execute("player", name, "role", "admin");
            case "p" -> cli.execute("player", name, "role", "player");
            case "r" -> {
                String password =
                        cli.readSecret(
                                "New password, not shown (Enter = let the server make one up)");
                List<String> args = new ArrayList<>(List.of("player", name, "reset-password"));
                if (!blank(password)) {
                    args.add(
                            "--password="
                                    + password); // "=": a password starting with "-" stays a value
                }
                cli.execute(args.toArray(String[]::new));
            }
            case "d" -> cli.execute("player", name, "delete");
            default -> {}
        }
    }

    private void world() {
        cli.execute("world");
        String setting = ask("Setting to change, e.g. invite-code (Enter = back)");
        if (blank(setting)) {
            return;
        }
        String value = ask("New value for " + setting.strip());
        if (!blank(value)) {
            cli.execute("world", "set", setting.strip(), value.strip());
        }
    }

    private void events() {
        cli.execute("events", "failed");
        String id = ask("Event id to retry (Enter = back)");
        if (!blank(id)) {
            cli.execute("events", "retry", id.strip());
        }
    }

    private void npc() {
        cli.execute("npc", "list");
        cli.execute("npc", "difficulty");
        String action = ask("Spawn a lord: balanced, offensive, defensive (Enter = back)");
        if (!blank(action)) {
            cli.execute("npc", "spawn", action.strip());
        }
    }

    private void barbarians() {
        String x = ask("x (Enter = back)");
        if (blank(x)) {
            return;
        }
        String y = ask("y");
        if (!blank(y)) {
            cli.execute("barbarians", "spawn", x.strip(), y.strip());
        }
    }

    private void broadcast() {
        String text = ask("Message to everybody (Enter = back)");
        if (blank(text)) {
            return;
        }
        String where = ask("1 = popup (Enter), 2 = chat");
        cli.execute(
                !blank(where) && where.strip().equals("2") ? "chat-say" : "broadcast",
                text.strip());
    }

    private void cheats(boolean enabled) {
        if (!enabled) {
            out.println("The cheats are off on this server (BANNERFALL_CHEATS_ENABLED).");
            return;
        }
        out.println("Village ids: see Players → a player's name.");
        String village = ask("Village id (Enter = back)");
        if (blank(village)) {
            return;
        }
        String id = village.strip();
        String action =
                ask("g = give resources, o = finish orders, m = finish movements, u = units");
        switch (blank(action) ? "" : action.strip().toLowerCase()) {
            case "g" -> {
                String amounts = ask("wood clay iron, e.g. 10000 10000 10000");
                if (!blank(amounts)) {
                    List<String> args = new ArrayList<>(List.of("cheats", "give", id));
                    args.addAll(List.of(amounts.strip().split("\\s+")));
                    cli.execute(args.toArray(String[]::new));
                }
            }
            case "o" -> cli.execute("cheats", "finish-orders", id);
            case "m" -> cli.execute("cheats", "finish-movements", id);
            case "u" -> {
                String units = ask("Units, e.g. SPEARMAN=100 AXEMAN=50");
                if (!blank(units)) {
                    List<String> args = new ArrayList<>(List.of("cheats", "units", id));
                    args.addAll(List.of(units.strip().split("\\s+")));
                    cli.execute(args.toArray(String[]::new));
                }
            }
            default -> {}
        }
    }

    /** Prints the prompt and reads a line; null at the end of the input. */
    private String ask(String prompt) {
        out.print(prompt + " ");
        out.flush();
        return cli.readLine();
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
