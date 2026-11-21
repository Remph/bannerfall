package de.bannerfall.chronicle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writes the replay map's frame of each game day (2.1 I1, 02-game-rules §32): a check every few
 * minutes (real time), so a day ÷ speed is caught soon after it began, also under fast forward. A
 * world that is asleep, paused, ended or not open yet gets none.
 */
@Component
class ChronicleSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(ChronicleSnapshotJob.class);

    private final Chronicle chronicle;

    ChronicleSnapshotJob(Chronicle chronicle) {
        this.chronicle = chronicle;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    void run() {
        chronicle.dailyFrame().ifPresent(day -> log.info("Chronicle frame of day {} written", day));
    }
}
