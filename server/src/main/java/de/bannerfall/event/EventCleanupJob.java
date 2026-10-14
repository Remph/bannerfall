package de.bannerfall.event;

import de.bannerfall.market.TradeTransportRepository;
import de.bannerfall.report.ReportRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes DONE events a week after they were processed, so the table stays small. FAILED and
 * PENDING events are never touched. The same job deletes the reports of NPC lords a week after they
 * were created: the lords read them in their next round and nobody else does (09-npc-lords §4). It
 * also deletes market transports whose merchants are back home (03-data-model
 * <i>trade_transport</i>).
 */
@Component
public class EventCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(EventCleanupJob.class);

    static final Duration KEEP_DONE = Duration.ofDays(7);

    /** Reports of NPC lords are kept this long (real time). */
    static final Duration KEEP_NPC_REPORTS = Duration.ofDays(7);

    private final GameEventRepository events;
    private final ReportRepository reports;
    private final TradeTransportRepository transports;
    private final Clock clock;

    EventCleanupJob(
            GameEventRepository events,
            ReportRepository reports,
            TradeTransportRepository transports,
            Clock clock) {
        this.events = events;
        this.reports = reports;
        this.transports = transports;
        this.clock = clock;
    }

    /** Every night at 03:30 UTC. */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void run() {
        int deleted = deleteOldDoneEvents();
        if (deleted > 0) {
            log.info("Deleted {} old events", deleted);
        }
        int npcReports = deleteOldNpcReports();
        if (npcReports > 0) {
            log.info("Deleted {} old reports of NPC lords", npcReports);
        }
        int finishedTransports = deleteFinishedTransports();
        if (finishedTransports > 0) {
            log.info("Deleted {} finished market transports", finishedTransports);
        }
    }

    /**
     * @return how many events were deleted
     */
    public int deleteOldDoneEvents() {
        return events.deleteDoneBefore(clock.instant().minus(KEEP_DONE));
    }

    /**
     * @return how many reports of NPC lords were deleted
     */
    public int deleteOldNpcReports() {
        return reports.deleteNpcReportsCreatedBefore(clock.instant().minus(KEEP_NPC_REPORTS));
    }

    /**
     * @return how many transports were deleted: arrived, and their merchants are back
     */
    public int deleteFinishedTransports() {
        return transports.deleteFinishedBefore(clock.instant());
    }
}
