package de.bannerfall.chronicle;

import de.bannerfall.chronicle.ChronicleFrames.Frame;
import de.bannerfall.chronicle.ChronicleFrames.Owner;
import de.bannerfall.victory.SideKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which moments the chronicle tells (2.1 I1, 02-game-rules §32): the turning points a player
 * remembers, not every report. Pure, so the rule is tested without a world.
 *
 * <ul>
 *   <li>Always: guilds of humans founded and gone, enemy guilds marked, allies joining and leaving,
 *       attacks ordered, help requests answered or ignored, the court, the victory clock, the end.
 *   <li>A conquest when it is the world's first, takes a banner keep, takes a player's last
 *       village, or is the conqueror's first village taken from another player.
 *   <li>The other conquests of a day in one line: how many, and the day's busiest conqueror if one
 *       took two or more and more than anybody else.
 *   <li>A new leader in points (by side: a guild while guilds are on), from the daily frames.
 * </ul>
 */
final class ChronicleRules {

    private ChronicleRules() {}

    /** A stored moment. */
    record Stored(long id, Instant at, int day, ChronicleKind kind, ChronicleFacts facts) {}

    /** A moment the chronicle tells. */
    record Entry(int day, Instant at, ChronicleKind kind, ChronicleFacts facts) {}

    /** The turning points of {@code events} and {@code frames}, in the order they happened. */
    static List<Entry> pick(List<Stored> events, List<Frame> frames, boolean guilds) {
        List<Entry> picked = new ArrayList<>();
        Map<Integer, List<Stored>> quiet = new TreeMap<>();
        Set<Long> conquered = new HashSet<>(); // conquerors who took a player's village already
        boolean firstSeen = false;
        List<Stored> ordered =
                events.stream()
                        .sorted(Comparator.comparing(Stored::at).thenComparingLong(Stored::id))
                        .toList();
        for (Stored event : ordered) {
            if (event.kind() != ChronicleKind.CONQUEST) {
                picked.add(new Entry(event.day(), event.at(), event.kind(), event.facts()));
                continue;
            }
            ChronicleFacts facts = event.facts();
            boolean first = !firstSeen;
            firstSeen = true;
            boolean fromPlayer = facts.from() != null;
            boolean firstOfConqueror =
                    fromPlayer && facts.to() != null && conquered.add(facts.to().id());
            if (first
                    || Boolean.TRUE.equals(facts.keep())
                    || Boolean.TRUE.equals(facts.eliminated())
                    || firstOfConqueror) {
                picked.add(
                        new Entry(
                                event.day(),
                                event.at(),
                                ChronicleKind.CONQUEST,
                                first ? facts.withFirst() : facts));
            } else {
                quiet.computeIfAbsent(event.day(), d -> new ArrayList<>()).add(event);
            }
        }
        quiet.forEach((day, list) -> picked.add(summary(day, list)));
        picked.addAll(leads(frames, guilds));
        picked.sort(
                Comparator.comparingInt(Entry::day)
                        .thenComparing(Entry::at)
                        .thenComparing(e -> e.kind() == ChronicleKind.WORLD_ENDED ? 1 : 0));
        return picked;
    }

    /** One line for the day's other conquests, at the time of the last of them. */
    private static Entry summary(int day, List<Stored> list) {
        Map<Long, Integer> byConqueror = new LinkedHashMap<>();
        Map<Long, ChroniclePlayer> who = new LinkedHashMap<>();
        for (Stored event : list) {
            ChroniclePlayer to = event.facts().to();
            if (to != null) {
                byConqueror.merge(to.id(), 1, Integer::sum);
                who.putIfAbsent(to.id(), to);
            }
        }
        int most = byConqueror.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Map.Entry<Long, Integer>> atMost =
                byConqueror.entrySet().stream().filter(e -> e.getValue() == most).toList();
        // named only when one conqueror took more than one and more than anybody else: "1 of them
        // to X" out of a tie would pick X at random
        Map.Entry<Long, Integer> busiest =
                most >= 2 && atMost.size() == 1 ? atMost.getFirst() : null;
        Instant last = list.stream().map(Stored::at).max(Comparator.naturalOrder()).orElseThrow();
        ChronicleFacts facts =
                ChronicleFacts.empty()
                        .withCount(
                                busiest == null ? null : who.get(busiest.getKey()),
                                list.size(),
                                busiest == null ? 0 : busiest.getValue());
        return new Entry(day, last, ChronicleKind.CONQUESTS, facts);
    }

    /** A line each time another side leads in points at a frame (none for the first leader). */
    static List<Entry> leads(List<Frame> frames, boolean guilds) {
        List<Entry> leads = new ArrayList<>();
        String leader = null;
        for (Frame frame : frames.stream().sorted(Comparator.comparingInt(Frame::day)).toList()) {
            Map<String, Long> points = new LinkedHashMap<>();
            Map<String, ChronicleSide> sides = new LinkedHashMap<>();
            for (Owner owner : frame.players()) {
                ChronicleSide side = sideOf(owner, guilds);
                points.merge(side.key(), owner.points(), Long::sum);
                sides.putIfAbsent(side.key(), side);
            }
            long top = points.values().stream().mapToLong(Long::longValue).max().orElse(0);
            List<String> atTop =
                    points.entrySet().stream()
                            .filter(e -> e.getValue() == top)
                            .map(Map.Entry::getKey)
                            .toList();
            if (top == 0 || atTop.size() != 1) {
                continue; // nobody, or a tie: the lead stays where it was
            }
            String now = atTop.getFirst();
            if (leader != null && !Objects.equals(leader, now)) {
                leads.add(
                        new Entry(
                                frame.day(),
                                frame.at(),
                                ChronicleKind.LEAD,
                                ChronicleFacts.empty().withSide(sides.get(now))));
            }
            leader = now;
        }
        return leads;
    }

    /** The side a player belongs to in a frame: his guild while guilds are on, else himself. */
    static ChronicleSide sideOf(Owner owner, boolean guilds) {
        if (guilds && owner.guildId() != null) {
            return ChronicleSide.guild(owner.guildId(), owner.guildName(), owner.guildTag());
        }
        return new ChronicleSide(
                SideKind.PLAYER, owner.id(), owner.name(), null, owner.npc(), null);
    }
}
