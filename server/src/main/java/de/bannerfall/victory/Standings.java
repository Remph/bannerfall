package de.bannerfall.victory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Every side that owns a village, best first (most points, then most villages), with the totals of
 * the map (02-game-rules §22).
 *
 * @param allVillages every village on the map, barbarians and keeps included
 * @param allKeeps every banner keep on the map
 */
record Standings(List<VictorySide> sides, int allVillages, int allKeeps) {

    static final Comparator<VictorySide> BY_POINTS =
            Comparator.comparingLong(VictorySide::points)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(VictorySide::villages).reversed())
                    .thenComparing(VictorySide::key);

    /**
     * The side with the most points: the winner on the end date; empty if nobody owns a village.
     */
    Optional<VictorySide> best() {
        return sides.stream().findFirst();
    }

    /**
     * The side that alone has the most of {@code measure} (villages or keeps), if it has any; empty
     * when two sides share the top.
     */
    Optional<VictorySide> aloneAhead(ToLongFunction<VictorySide> measure) {
        List<VictorySide> sorted =
                sides.stream()
                        .sorted(Comparator.comparingLong(measure).reversed())
                        .limit(2)
                        .toList();
        if (sorted.isEmpty() || measure.applyAsLong(sorted.get(0)) == 0) {
            return Optional.empty();
        }
        if (sorted.size() == 2
                && measure.applyAsLong(sorted.get(0)) == measure.applyAsLong(sorted.get(1))) {
            return Optional.empty();
        }
        return Optional.of(sorted.get(0));
    }
}
