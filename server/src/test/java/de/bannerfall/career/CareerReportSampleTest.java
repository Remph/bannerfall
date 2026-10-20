package de.bannerfall.career;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.world.EndReason;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The career report has two copies of its format: {@link CareerReporter}'s records here and the
 * account server's {@code CareerService}, which also sets limits on it. This test keeps a sample of
 * the largest report this world can send: the longest names, every achievement at every level, the
 * longest end reason. The account server's {@code CareerTest} posts it and expects it stored, so a
 * change on either side that the other can't take fails a test.
 *
 * <p>After a change to the report (or a new achievement): {@code ./gradlew test --tests
 * '*CareerReportSampleTest' -PupdateCareerSample} writes the sample.
 */
class CareerReportSampleTest {

    /** Relative to {@code server/}, the working directory of the tests. */
    static final Path SAMPLE = Path.of("src/test/resources/career/report-sample.json");

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static final Instant AT = Instant.parse("2026-10-12T10:00:00Z");

    /** The longest player name (sign-up and the account server's names: 3 to 24 characters). */
    private static final String PLAYER_NAME = "P".repeat(24);

    /** The longest side in the final standings: a guild name (at most 30 characters). */
    private static final String SIDE_NAME = "G".repeat(30);

    /** The longest world name (the world settings: at most 64 characters). */
    private static final String WORLD_NAME = "W".repeat(64);

    static CareerReporter.Report sample() {
        List<CareerReporter.EarnedAchievement> achievements = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            int levels = achievement.leveled() ? Achievement.LEVELS : 1;
            for (int level = 1; level <= levels; level++) {
                achievements.add(
                        new CareerReporter.EarnedAchievement(
                                achievement.name(), achievement.leveled(), level, AT));
            }
        }
        String longestReason =
                Arrays.stream(EndReason.values())
                        .map(Enum::name)
                        .max(Comparator.comparingInt(String::length))
                        .orElseThrow();
        CareerReporter.PlayerReport winner =
                new CareerReporter.PlayerReport(
                        "subject-of-the-winner",
                        PLAYER_NAME,
                        AT,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        1L,
                        false,
                        achievements,
                        new CareerReporter.Result(longestReason, AT, true, 1, SIDE_NAME),
                        new CareerReporter.Stats(
                                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        CareerReporter.PlayerReport deleted =
                new CareerReporter.PlayerReport(
                        "subject-of-the-deleted",
                        PLAYER_NAME.toLowerCase(),
                        AT,
                        0,
                        0,
                        null,
                        true,
                        List.of(),
                        null,
                        new CareerReporter.Stats(0, 0, 0, 0));
        return new CareerReporter.Report(
                new CareerReporter.World(WORLD_NAME, AT, "ENDED", AT), List.of(winner, deleted));
    }

    @Test
    void theAccountServersSampleIsCurrent() throws Exception {
        JsonNode current = JSON.readTree(CareerReporter.toJson(sample()));
        if (Boolean.getBoolean("bannerfall.updateCareerSample")) {
            Files.writeString(
                    SAMPLE, JSON.writeValueAsString(current) + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(SAMPLE).exists();
        assertThat(JSON.readTree(Files.readString(SAMPLE, StandardCharsets.UTF_8)))
                .as(
                        "The career report changed: run ./gradlew test --tests"
                                + " '*CareerReportSampleTest' -PupdateCareerSample, then the"
                                + " account server's check")
                .isEqualTo(current);
    }

    @Test
    void aBatchFitsTheAccountServersLimit() {
        // CareerService.Report takes at most 500 players
        assertThat(CareerReporter.BATCH).isLessThanOrEqualTo(500);
    }
}
