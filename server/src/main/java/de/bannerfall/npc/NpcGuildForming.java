package de.bannerfall.npc;

import de.bannerfall.common.Coordinates;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * How NPC lords find each other for a guild (09-npc-lords §13.1), as pure functions: who takes
 * part, which guild a lord joins or whom it founds one with, and the next free name.
 */
final class NpcGuildForming {

    private NpcGuildForming() {}

    /**
     * An NPC lord as the forming sees it.
     *
     * @param guildId its guild, null for none
     * @param guildSize the members of that guild (0 without one)
     * @param byAdmin the admin placed it (§13.7): it neither joins nor founds on its own
     * @param midPoints the points where its template's MID stage starts (850)
     * @param villages where its villages are
     */
    record Lord(
            long id,
            int points,
            Long guildId,
            int guildSize,
            boolean byAdmin,
            int midPoints,
            List<Coordinates> villages) {

        /** Free to join or found a guild on its own. */
        boolean takesPart() {
            return guildId == null && !byAdmin && !villages.isEmpty() && points >= midPoints;
        }
    }

    /** What a lord does: join a guild, found one with another lord, or nothing. */
    sealed interface Choice {}

    record Join(long guildId) implements Choice {}

    record Found(long otherId) implements Choice {}

    record Stay() implements Choice {}

    /**
     * The choice of {@code me} (§13.1): the nearest neighbour within {@code radius} that is in a
     * guild with room – join it; otherwise the nearest neighbour that takes part itself – found a
     * guild with it. Neighbours are ordered by the distance between their closest villages, ties by
     * the lower player id.
     *
     * @param maxMembers the most lords a lord guild may have
     */
    static Choice choose(Lord me, List<Lord> others, double radius, int maxMembers) {
        if (!me.takesPart()) {
            return new Stay();
        }
        List<Lord> neighbours =
                others.stream()
                        .filter(o -> o.id() != me.id() && !o.villages().isEmpty())
                        .filter(o -> distance(me, o) <= radius)
                        .sorted(
                                Comparator.comparingDouble((Lord o) -> distance(me, o))
                                        .thenComparingLong(Lord::id))
                        .toList();
        Optional<Lord> withRoom =
                neighbours.stream()
                        .filter(o -> o.guildId() != null && o.guildSize() < maxMembers)
                        .findFirst();
        if (withRoom.isPresent()) {
            return new Join(withRoom.get().guildId());
        }
        return neighbours.stream()
                .filter(Lord::takesPart)
                .findFirst()
                .<Choice>map(o -> new Found(o.id()))
                .orElse(new Stay());
    }

    /** The distance between the closest pair of villages of two lords. */
    static double distance(Lord a, Lord b) {
        double best = Double.MAX_VALUE;
        for (Coordinates x : a.villages()) {
            for (Coordinates y : b.villages()) {
                best = Math.min(best, x.distanceTo(y));
            }
        }
        return best;
    }

    /** A guild name and its tag. */
    record Name(String name, String tag) {}

    /** The names of lord guilds (§13.1), Latin orders that read the same in English and German. */
    static final List<Name> NAMES =
            List.of(
                    new Name("Ordo Corvi", "CORVI"),
                    new Name("Ordo Leonis", "LEO"),
                    new Name("Ordo Aquilae", "AQUIL"),
                    new Name("Ordo Ursi", "URSI"),
                    new Name("Ordo Lupi", "LUPI"),
                    new Name("Ordo Draconis", "DRACO"),
                    new Name("Ordo Cervi", "CERVI"),
                    new Name("Ordo Falconis", "FALCO"),
                    new Name("Ordo Tauri", "TAURI"),
                    new Name("Ordo Vulpis", "VULPS"));

    /**
     * The first entry of {@link #NAMES} whose name and tag are both free (ignoring case); once all
     * are taken, the list again with " II", " III", … and the tag cut to four letters plus 2, 3, …
     *
     * @param takenNames the names of all guilds, lower case
     * @param takenTags the tags of all guilds, lower case
     */
    static Name nextName(Set<String> takenNames, Set<String> takenTags) {
        for (int round = 1; ; round++) {
            for (Name base : NAMES) {
                Name candidate = round == 1 ? base : numbered(base, round);
                if (!takenNames.contains(candidate.name().toLowerCase(Locale.ROOT))
                        && !takenTags.contains(candidate.tag().toLowerCase(Locale.ROOT))) {
                    return candidate;
                }
            }
        }
    }

    private static Name numbered(Name base, int round) {
        String number = Integer.toString(round);
        String stem = base.tag().substring(0, Math.min(base.tag().length(), 5 - number.length()));
        return new Name(base.name() + " " + roman(round), stem + number);
    }

    static String roman(int n) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                out.append(letters[i]);
                n -= values[i];
            }
        }
        return out.toString();
    }
}
