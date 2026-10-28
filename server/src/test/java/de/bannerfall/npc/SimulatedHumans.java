package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.TestPlayer;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.movement.CommandService.SendAttempt;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scripted human players of the simulation's "active humans" scenario (09 §11): they play like
 * the humans of Realm 1 did (two farmers and a raider that only hit barbarian villages, a warlord
 * that grows a big army and attacks the lords), without an economy of their own. Their units are
 * granted: before each attack the home stack is topped up to the army the day's curve asks for, so
 * the run tests what the lords do about attackers (learning, archers, the brake), not how well a
 * bot builds. The warlord sends no nobles; conquests by humans are covered by the flow tests.
 *
 * <p>Everything is a function of the clock and of the map, nothing is drawn, so a run stays
 * repeatable.
 */
final class SimulatedHumans {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private enum Role {
        FARMER,
        RAIDER,
        WARLORD
    }

    /** One scripted player; {@code minutes} are minutes of the Berlin day, one slot each. */
    private static final class Bot {
        final Role role;
        final long playerId;
        final long villageId;
        final Coordinates home;
        final int firstDay;
        final int[] minutes;
        int slot;
        int lordAttacks;
        int farmAttacks;
        int sent;
        final Map<String, Integer> refused = new TreeMap<>();

        Bot(
                Role role,
                long playerId,
                long villageId,
                Coordinates home,
                int firstDay,
                int... minutes) {
            this.role = role;
            this.playerId = playerId;
            this.villageId = villageId;
            this.home = home;
            this.firstDay = firstDay;
            this.minutes = minutes;
        }
    }

    private final CommandService commands;
    private final JdbcClient jdbc;
    private final List<Bot> bots = new ArrayList<>();
    private Instant start;

    SimulatedHumans(CommandService commands, JdbcClient jdbc) {
        this.commands = commands;
        this.jdbc = jdbc;
    }

    /** Registers the four players and returns their ids. {@code start} is day 0. */
    List<Long> register(MockMvc mockMvc, Instant start) throws Exception {
        this.start = start;
        List<Long> ids = new ArrayList<>();
        // hours as seen in Realm 1: the farmers in the morning or the evening, the raider all
        // day, the warlord a main attack in the morning, farming at noon, a second attack in the
        // evening
        ids.add(add(mockMvc, Role.FARMER, 1, 7 * 60, 9 * 60 + 30, 15 * 60, 16 * 60, 20 * 60));
        ids.add(add(mockMvc, Role.FARMER, 1, 15 * 60, 17 * 60, 18 * 60 + 30, 20 * 60, 22 * 60));
        ids.add(
                add(
                        mockMvc,
                        Role.RAIDER,
                        0,
                        8 * 60,
                        9 * 60,
                        10 * 60,
                        11 * 60,
                        14 * 60,
                        15 * 60,
                        16 * 60));
        ids.add(add(mockMvc, Role.WARLORD, 0, 10 * 60, 14 * 60, 18 * 60));
        return ids;
    }

    private long add(MockMvc mockMvc, Role role, int firstDay, int... minutes) throws Exception {
        TestPlayer player = TestPlayer.register(mockMvc);
        Coordinates home =
                jdbc.sql("select x, y from village where id = ?")
                        .param(player.villageId)
                        .query((rs, row) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                        .single();
        Bot bot = new Bot(role, player.playerId, player.villageId, home, firstDay, minutes);
        bots.add(bot);
        // the first slot that is not in the past
        while (slotAt(bot, bot.slot).isBefore(start)) {
            bot.slot++;
        }
        return player.playerId;
    }

    private Instant slotAt(Bot bot, int slot) {
        LocalDate day =
                start.atZone(BERLIN)
                        .toLocalDate()
                        .plusDays(bot.firstDay + slot / bot.minutes.length);
        int minute = bot.minutes[slot % bot.minutes.length];
        return ZonedDateTime.of(day.atStartOfDay(), BERLIN).plusMinutes(minute).toInstant();
    }

    /** Sends every attack that is due at {@code now}. */
    void act(Instant now) {
        for (Bot bot : bots) {
            while (!slotAt(bot, bot.slot).isAfter(now)) {
                int index = bot.slot % bot.minutes.length;
                double day = Duration.between(start, slotAt(bot, bot.slot)).toMinutes() / 1440.0;
                bot.slot++;
                attack(bot, index, day);
            }
        }
    }

    private void attack(Bot bot, int index, double day) {
        Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
        boolean lord = false;
        switch (bot.role) {
            case FARMER -> army.put(UnitType.LIGHT_CAVALRY, (int) Math.min(120, 8 + 3 * day));
            case RAIDER -> {
                army.put(UnitType.AXEMAN, (int) Math.min(220, 20 + 10 * day));
                army.put(UnitType.LIGHT_CAVALRY, (int) Math.min(110, 10 + 5 * day));
            }
            case WARLORD -> {
                if (index == 1) { // noon: farming
                    army.put(UnitType.LIGHT_CAVALRY, (int) Math.min(200, 30 + 5 * day));
                } else if (day < 7 || (index == 2 && day < 10)) { // the army is still growing
                    army.put(UnitType.LIGHT_CAVALRY, 8);
                } else {
                    lord = true;
                    double share = index == 2 ? 0.5 : 1.0;
                    army.put(UnitType.AXEMAN, (int) (share * Math.min(2000, 150 * (day - 2))));
                    army.put(UnitType.LIGHT_CAVALRY, (int) (share * Math.min(650, 60 * (day - 2))));
                    if (day >= 8) {
                        int siege = (int) (share * Math.min(100, 5 * (day - 6)));
                        army.put(UnitType.RAM, siege);
                        army.put(UnitType.CATAPULT, siege);
                    }
                }
            }
        }
        Coordinates target = pickTarget(bot, lord);
        if (target == null) {
            bot.refused.merge("NO_TARGET", 1, Integer::sum);
            return;
        }
        top(bot.villageId, army);
        SendAttempt attempt =
                commands.trySend(
                        bot.villageId,
                        bot.playerId,
                        new CommandRequest(CommandKind.ATTACK, target, army, null));
        if (attempt.movement() == null) {
            bot.refused.merge(attempt.errors().getFirst().name(), 1, Integer::sum);
            return;
        }
        bot.sent++;
        if (lord) {
            bot.lordAttacks++;
        } else {
            bot.farmAttacks++;
        }
    }

    /**
     * The k-th nearest village of the kind: barbarian ones rotate through the nearest eight (a
     * farmer's list), the lords' ones through the nearest three, one step every two attacks.
     */
    private Coordinates pickTarget(Bot bot, boolean lord) {
        List<Coordinates> near =
                jdbc.sql(
                                "select v.x, v.y from village v left join player p on p.id ="
                                        + " v.player_id where "
                                        + (lord ? "p.npc" : "v.player_id is null")
                                        + " order by (v.x - ?) * (v.x - ?) + (v.y - ?) * (v.y - ?),"
                                        + " v.id limit ?")
                        .params(
                                bot.home.x(),
                                bot.home.x(),
                                bot.home.y(),
                                bot.home.y(),
                                lord ? 3 : 8)
                        .query((rs, row) -> new Coordinates(rs.getInt("x"), rs.getInt("y")))
                        .list();
        if (near.isEmpty()) {
            return null;
        }
        int n = lord ? bot.lordAttacks / 2 : bot.farmAttacks;
        return near.get(n % near.size());
    }

    /** Raises the home stack to at least {@code army}. */
    private void top(long villageId, Map<UnitType, Integer> army) {
        army.forEach(
                (type, count) ->
                        jdbc.sql(
                                        "update unit_stack set u_"
                                                + type.name().toLowerCase(Locale.ROOT)
                                                + " = greatest(u_"
                                                + type.name().toLowerCase(Locale.ROOT)
                                                + ", ?) where home_village_id = ? and"
                                                + " location_village_id = ?")
                                .params(count, villageId, villageId)
                                .update());
    }

    /**
     * Fails when a bot did not play: a refused command (a rule that changed) would otherwise leave
     * the scenario green while it tests nothing. {@code days} is the length of the run.
     */
    void assertActed(int days) {
        for (Bot bot : bots) {
            assertThat(bot.sent)
                    .as("attacks sent by the %s (refused: %s)", bot.role, bot.refused)
                    .isPositive();
            if (bot.role == Role.WARLORD && days > 8) {
                assertThat(bot.lordAttacks)
                        .as("attacks of the warlord on lords (refused: %s)", bot.refused)
                        .isPositive();
            }
        }
    }

    /** What the bots did and what the lords did to them, for the run's text. */
    String lines() {
        StringBuilder out =
                new StringBuilder(
                        "\n"
                            + "Active humans (scripted, units granted; attacks sent, battles from"
                            + " their reports, lords' attacks on them from their defence"
                            + " reports):\n");
        List<Bot> sorted = new ArrayList<>(bots);
        sorted.sort(Comparator.comparingLong(b -> b.playerId));
        for (Bot bot : sorted) {
            String name =
                    jdbc.sql("select name from player where id = ?")
                            .param(bot.playerId)
                            .query(String.class)
                            .single();
            out.append(
                    String.format(
                            Locale.ROOT,
                            "  %-9s %-18s %d attacks (%d on barbarians, %d on lords), refused %s%n",
                            bot.role.name().toLowerCase(Locale.ROOT),
                            name,
                            bot.sent,
                            bot.farmAttacks,
                            bot.lordAttacks,
                            bot.refused.isEmpty() ? "none" : bot.refused));
            out.append("    battles: ").append(battles(bot)).append('\n');
            out.append("    villages now: ")
                    .append(
                            jdbc.sql("select count(*) from village where player_id = ?")
                                    .param(bot.playerId)
                                    .query(Integer.class)
                                    .single())
                    .append(", defence reports: ")
                    .append(defence(bot))
                    .append('\n');
        }
        return out.toString();
    }

    private String battles(Bot bot) {
        Map<String, Integer> tally = new TreeMap<>();
        jdbc.sql(
                        "select case when d.id is null then 'barbarians' when d.npc then 'lords'"
                            + " else 'humans' end as target, r.payload->>'outcome' as outcome,"
                            + " count(*) as n from report r left join player d on d.id ="
                            + " (r.payload->'defender'->>'playerId')::bigint where r.player_id = ?"
                            + " and r.kind = 'ATTACK' and r.payload->>'type' = 'BATTLE' group by 1,"
                            + " 2")
                .param(bot.playerId)
                .query(
                        (rs, row) -> {
                            tally.put(
                                    rs.getString("target") + " " + rs.getString("outcome"),
                                    rs.getInt("n"));
                            return null;
                        })
                .list();
        return tally.isEmpty() ? "none" : tally.toString();
    }

    private String defence(Bot bot) {
        Map<String, Integer> tally = new TreeMap<>();
        jdbc.sql(
                        "select r.payload->>'outcome' as outcome, count(*) as n from report r"
                                + " where r.player_id = ? and r.kind = 'DEFENSE' and"
                                + " r.payload->>'type' = 'BATTLE' group by 1")
                .param(bot.playerId)
                .query(
                        (rs, row) -> {
                            tally.put(rs.getString("outcome"), rs.getInt("n"));
                            return null;
                        })
                .list();
        return tally.isEmpty() ? "none" : tally.toString();
    }
}
