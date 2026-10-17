package de.bannerfall.rules;

import java.time.Duration;
import java.time.Instant;

/**
 * How a world is won (02-game-rules §22): the numbers of the two victory conditions and the clock
 * that a side must hold them for. Pure functions.
 */
public final class Victory {

    private Victory() {}

    /**
     * Whether {@code sideVillages} of {@code allVillages} (every village on the map, barbarians and
     * keeps included) reach {@code percent}.
     */
    public static boolean dominates(int sideVillages, int allVillages, int percent) {
        return allVillages > 0 && (long) sideVillages * 100 >= (long) percent * allVillages;
    }

    /**
     * How many of the {@code keepCount} keeps a side must hold: {@code percent} of them, rounded
     * up, at least 1 (51 % of 5 = 3, 99 % of 4 = 4).
     */
    public static int keepsNeeded(int keepCount, int percent) {
        return Math.max(1, (int) Math.ceil(keepCount * percent / 100.0 - 1e-9));
    }

    /** When a side that meets the condition since {@code since} wins: {@code holdHours} later. */
    public static Instant winsAt(Instant since, int holdHours) {
        return since.plus(Duration.ofHours(holdHours));
    }

    /**
     * The victory clock after a check at {@code at}: the side that meets the condition now ({@code
     * candidate}, null = nobody) keeps its {@code since} if it is the same side as before, starts a
     * new clock at {@code at} if it is another one, and nobody leads if nobody meets it.
     */
    public static Clock next(String leader, Instant since, String candidate, Instant at) {
        if (candidate == null) {
            return new Clock(null, null);
        }
        if (candidate.equals(leader) && since != null) {
            return new Clock(candidate, since);
        }
        return new Clock(candidate, at);
    }

    /** The side that meets the condition and since when; both null = nobody. */
    public record Clock(String leader, Instant since) {

        /** Whether the leader has held long enough at {@code at}. */
        public boolean won(Instant at, int holdHours) {
            return leader != null && !at.isBefore(winsAt(since, holdHours));
        }
    }
}
