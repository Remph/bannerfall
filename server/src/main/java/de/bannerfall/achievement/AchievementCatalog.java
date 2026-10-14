package de.bannerfall.achievement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The thresholds of the levelled achievements, bound from {@code gamedata/achievements.yml}. The
 * constructor validates them, so a broken file stops the application at startup: every levelled
 * achievement has exactly {@link Achievement#LEVELS} thresholds, positive and strictly ascending,
 * and no one-time achievement has any.
 */
@ConfigurationProperties("achievements")
public record AchievementCatalog(Map<Achievement, List<Long>> levels) {

    public AchievementCatalog {
        Map<Achievement, List<Long>> copy = new EnumMap<>(Achievement.class);
        if (levels != null) {
            levels.forEach((key, values) -> copy.put(key, List.copyOf(values)));
        }
        levels = Collections.unmodifiableMap(copy);
        validate(levels);
    }

    /** The values that earn level 1, 2 and 3 of a levelled achievement. */
    public List<Long> thresholds(Achievement achievement) {
        List<Long> found = levels.get(achievement);
        if (found == null) {
            throw new IllegalArgumentException(achievement + " has no levels");
        }
        return found;
    }

    /** How many levels a value reaches: 0 … {@link Achievement#LEVELS}. */
    public int levelFor(Achievement achievement, long value) {
        int reached = 0;
        for (long threshold : thresholds(achievement)) {
            if (value >= threshold) {
                reached++;
            }
        }
        return reached;
    }

    private static void validate(Map<Achievement, List<Long>> levels) {
        List<String> problems = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            List<Long> values = levels.get(achievement);
            if (!achievement.leveled()) {
                if (values != null) {
                    problems.add(achievement + ": is a one-time achievement, no levels");
                }
                continue;
            }
            if (values == null || values.size() != Achievement.LEVELS) {
                problems.add(achievement + ": needs exactly " + Achievement.LEVELS + " levels");
                continue;
            }
            long previous = 0;
            for (long value : values) {
                if (value <= previous) {
                    problems.add(achievement + ": levels must be positive and ascending");
                    break;
                }
                previous = value;
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid achievements.yml:\n" + String.join("\n", problems));
        }
    }
}
