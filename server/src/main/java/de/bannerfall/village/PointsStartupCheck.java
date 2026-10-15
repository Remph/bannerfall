package de.bannerfall.village;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Brings the stored village and player points in line with the points formula at every start
 * (02-game-rules §4.2, "Points growth"). Village points are only recalculated when a village
 * changes, so without this a running world would keep the old numbers after an update that changes
 * the formula or a points base. A world that is in line costs one read of all villages.
 *
 * <p>Off in the integration tests with the world initializer ({@code
 * bannerfall.world.initializer-enabled=false}): they share one database.
 */
@Component
class PointsStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(PointsStartupCheck.class);

    /** Villages per transaction, so the locks are held only briefly. */
    private static final int BATCH = 200;

    private final PointsService points;
    private final boolean enabled;

    PointsStartupCheck(
            PointsService points,
            @Value("${bannerfall.world.initializer-enabled:true}") boolean enabled) {
        this.points = points;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!enabled) {
            return;
        }
        List<Long> stale;
        try {
            stale = points.findStale();
        } catch (RuntimeException e) {
            // Never stop the server for this: each village is corrected when it changes next.
            log.error("Finding the villages with outdated points failed", e);
            return;
        }
        int failed = 0;
        for (int from = 0; from < stale.size(); from += BATCH) {
            List<Long> batch = stale.subList(from, Math.min(from + BATCH, stale.size()));
            if (!recalculate(batch) && !recalculate(batch)) {
                failed += batch.size();
            }
        }
        if (!stale.isEmpty()) {
            log.info(
                    "Points recalculated for {} of {} villages and their owners",
                    stale.size() - failed,
                    stale.size());
        }
    }

    /**
     * One batch; false if it failed. The game runs already, so an owner's points can be written at
     * the same time (an optimistic lock failure on the player): the batch is tried once more, and a
     * failed batch doesn't keep the others from running.
     */
    private boolean recalculate(List<Long> batch) {
        try {
            points.recalculateAll(batch);
            return true;
        } catch (RuntimeException e) {
            log.warn("Recalculating the points of {} villages failed", batch.size(), e);
            return false;
        }
    }
}
