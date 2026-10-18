package de.bannerfall.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** The thresholds of the levelled achievements (02-game-rules §19). */
class AchievementCatalogTest {

    /** The real {@code gamedata/achievements.yml}, bound like the application does. */
    private static AchievementCatalog real() {
        try {
            var sources =
                    new YamlPropertySourceLoader()
                            .load(
                                    "achievements",
                                    new ClassPathResource("gamedata/achievements.yml"));
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bind("achievements", AchievementCatalog.class)
                    .get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theRealFileHasThreeAscendingLevelsForEveryLevelledAchievementAndNoOther() {
        AchievementCatalog catalog = real();

        for (Achievement achievement : Achievement.values()) {
            if (achievement.leveled()) {
                assertThat(catalog.thresholds(achievement)).hasSize(Achievement.LEVELS).isSorted();
            } else {
                assertThat(catalog.levels()).doesNotContainKey(achievement);
            }
        }
        // 25 of v1.1, the six banner ones of M32c (02 §21.8) and the nine battle ones of 1.4
        assertThat(Achievement.values()).hasSize(40);
        assertThat(java.util.Arrays.stream(Achievement.values()).filter(Achievement::leveled))
                .hasSize(12);
    }

    @Test
    void theThresholdsOfTheRulebook() {
        AchievementCatalog catalog = real();

        assertThat(catalog.thresholds(Achievement.GROWTH)).containsExactly(1000L, 5000L, 30000L);
        assertThat(catalog.thresholds(Achievement.LANDLORD)).containsExactly(3L, 8L, 15L);
        assertThat(catalog.thresholds(Achievement.STANDING_ARMY))
                .containsExactly(200L, 2500L, 20000L);
        assertThat(catalog.thresholds(Achievement.VICTOR)).containsExactly(25L, 250L, 2500L);
        assertThat(catalog.thresholds(Achievement.BULWARK)).containsExactly(3L, 15L, 50L);
        assertThat(catalog.thresholds(Achievement.PLUNDERER))
                .containsExactly(20000L, 200000L, 2000000L);
        assertThat(catalog.thresholds(Achievement.MERCHANT_PRINCE))
                .containsExactly(10000L, 100000L, 1000000L);
        assertThat(catalog.thresholds(Achievement.VETERAN)).containsExactly(7L, 30L, 180L);
        assertThat(catalog.thresholds(Achievement.BANNER_COLLECTOR)).containsExactly(3L, 10L, 25L);
        assertThat(catalog.thresholds(Achievement.BANNER_THIEF)).containsExactly(1L, 5L, 20L);
        assertThat(catalog.thresholds(Achievement.SLAYER)).containsExactly(250L, 2500L, 25000L);
        assertThat(catalog.thresholds(Achievement.WARDEN)).containsExactly(100L, 1000L, 10000L);
    }

    @Test
    void aValueReachesTheLevelsOfItsThresholds() {
        AchievementCatalog catalog = real();

        assertThat(catalog.levelFor(Achievement.VICTOR, 0)).isZero();
        assertThat(catalog.levelFor(Achievement.VICTOR, 24)).isZero();
        assertThat(catalog.levelFor(Achievement.VICTOR, 25)).isEqualTo(1);
        assertThat(catalog.levelFor(Achievement.VICTOR, 249)).isEqualTo(1);
        assertThat(catalog.levelFor(Achievement.VICTOR, 250)).isEqualTo(2);
        assertThat(catalog.levelFor(Achievement.VICTOR, 2500)).isEqualTo(3);
        assertThat(catalog.levelFor(Achievement.VICTOR, 1_000_000)).isEqualTo(3);
    }

    @Test
    void aBrokenFileStopsTheApplication() {
        Map<Achievement, List<Long>> missing = valid();
        missing.remove(Achievement.VICTOR);
        assertThatThrownBy(() -> new AchievementCatalog(missing))
                .hasMessageContaining("VICTOR: needs exactly 3 levels");

        Map<Achievement, List<Long>> twoLevels = valid();
        twoLevels.put(Achievement.VICTOR, List.of(10L, 50L));
        assertThatThrownBy(() -> new AchievementCatalog(twoLevels))
                .hasMessageContaining("VICTOR: needs exactly 3 levels");

        Map<Achievement, List<Long>> descending = valid();
        descending.put(Achievement.VICTOR, List.of(10L, 5L, 250L));
        assertThatThrownBy(() -> new AchievementCatalog(descending))
                .hasMessageContaining("VICTOR: levels must be positive and ascending");

        Map<Achievement, List<Long>> zero = valid();
        zero.put(Achievement.VICTOR, List.of(0L, 5L, 250L));
        assertThatThrownBy(() -> new AchievementCatalog(zero))
                .hasMessageContaining("VICTOR: levels must be positive and ascending");

        Map<Achievement, List<Long>> oneTimeWithLevels = valid();
        oneTimeWithLevels.put(Achievement.FIRST_BLOOD, List.of(1L, 2L, 3L));
        assertThatThrownBy(() -> new AchievementCatalog(oneTimeWithLevels))
                .hasMessageContaining("FIRST_BLOOD: is a one-time achievement");
    }

    private static Map<Achievement, List<Long>> valid() {
        Map<Achievement, List<Long>> levels = new EnumMap<>(Achievement.class);
        real().levels().forEach(levels::put);
        return levels;
    }
}
