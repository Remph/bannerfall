package de.bannerfall.event;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the admin does with FAILED events (M14a): list them, put one back to PENDING. */
@Service
public class EventAdminService {

    private static final Logger log = LoggerFactory.getLogger(EventAdminService.class);
    static final int LIST_LIMIT = 100;

    /** Kinds whose handler schedules the next round itself and that are re-made at start. */
    private static final Set<GameEventKind> RECURRING =
            EnumSet.of(
                    GameEventKind.BARBARIAN_GROWTH,
                    GameEventKind.NPC_THINK,
                    GameEventKind.WORLD_OPEN);

    private final GameEventRepository events;

    EventAdminService(GameEventRepository events) {
        this.events = events;
    }

    /** The newest {@value #LIST_LIMIT} FAILED events, newest first. */
    @Transactional(readOnly = true)
    public List<FailedEventDto> failed() {
        return events
                .findByStatusOrderByProcessedAtDescIdDesc(
                        GameEventStatus.FAILED, PageRequest.of(0, LIST_LIMIT))
                .stream()
                .map(FailedEventDto::of)
                .toList();
    }

    /**
     * Sets a FAILED event back to PENDING with 3 fresh attempts; the worker picks it up at once
     * (its time is long past).
     *
     * @throws NotFoundException {@code EVENT_NOT_FOUND}
     * @throws GameRuleException {@code EVENT_NOT_FAILED} if it isn't FAILED (e.g. a second click),
     *     or if it is a recurring event whose next round is already waiting
     */
    @Transactional
    public void retry(long id) {
        GameEvent event =
                events.findByIdForUpdate(id)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() != GameEventStatus.FAILED) {
            throw new GameRuleException(ErrorCode.EVENT_NOT_FAILED, "The event isn't failed");
        }
        // A recurring event that failed is scheduled afresh at the next start; retrying the old
        // one as well would run two chains of it from then on (growth twice per interval, a lord
        // thinking twice as often).
        if (RECURRING.contains(event.getKind())
                && events.existsPendingFor(event.getKind().name(), event.getRefId())) {
            throw new GameRuleException(
                    ErrorCode.EVENT_NOT_FAILED,
                    "A newer " + event.getKind() + " event is already waiting");
        }
        event.retry();
        log.info("Admin retries failed event {} ({})", id, event.getKind());
    }
}
