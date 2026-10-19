package de.bannerfall.statistics;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes yesterday's statistics day (02-game-rules §24) soon after midnight in the world's time
 * zone. A check every few minutes instead of a cron, so a server that was down at midnight writes
 * the day when it is back (with the values of that moment; older missed days stay missing). Short
 * work on the shared scheduler thread: two {@code insert … select} over the players, once a day.
 */
@Component
class StatisticsSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(StatisticsSnapshotJob.class);

    private final StatisticsService statistics;

    /** The last day this job handled (written, or found written or not counting). */
    private LocalDate handled;

    StatisticsSnapshotJob(StatisticsService statistics) {
        this.statistics = statistics;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void run() {
        LocalDate yesterday = statistics.today().minusDays(1);
        if (yesterday.equals(handled)) {
            return;
        }
        if (statistics.snapshot(yesterday)) {
            log.info("Statistics for {} written", yesterday);
        }
        handled = yesterday;
    }
}
