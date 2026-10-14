package de.bannerfall.report;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports (02-game-rules §14): created by the game (battles, supports, later trades and conquests)
 * inside the transaction of what happened, read and deleted by their recipient (04-api).
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReportService(ReportRepository reports, ApplicationEventPublisher events, Clock clock) {
        this.reports = reports;
        this.events = events;
        this.clock = clock;
    }

    /**
     * One report per recipient, all with the same payload. Barbarians ({@code null}) get nothing, a
     * player named twice gets one report. Runs in the caller's transaction, so the reports exist
     * exactly if what they tell about happened. Each recipient is notified (REPORT_CREATED) after
     * commit.
     *
     * @param happenedAt the game time (an event's due time, or now in a request)
     * @param recipients player ids; nulls are skipped
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(
            ReportKind kind, Instant happenedAt, ReportPayload payload, Long... recipients) {
        Set<Long> playerIds = new LinkedHashSet<>();
        for (Long recipient : recipients) {
            if (recipient != null) {
                playerIds.add(recipient);
            }
        }
        Instant now = clock.instant();
        for (long playerId : playerIds) {
            Report report = reports.save(new Report(playerId, kind, happenedAt, payload, now));
            events.publishEvent(
                    new PlayerNotification(
                            playerId,
                            new GameNotification.ReportCreated(report.getId(), kind, null)));
        }
    }

    /** A page of the player's reports, newest first, with the summaries the list shows. */
    @Transactional(readOnly = true)
    public ReportPageDto list(long playerId, int page, int size) {
        Page<Report> found = reports.findVisible(playerId, PageRequest.of(page, size));
        return new ReportPageDto(
                found.map(ReportItemDto::of).getContent(),
                page,
                size,
                found.getTotalElements(),
                reports.countUnreadVisible(playerId),
                reports.maxIdOfPlayer(playerId));
    }

    /**
     * How many of the player's reports are unread (all pages), not counting copies from players he
     * blocked.
     */
    @Transactional(readOnly = true)
    public long unreadCount(long playerId) {
        return reports.countUnreadVisible(playerId);
    }

    /**
     * One report of the player with its payload; it counts as read from now on.
     *
     * @throws NotFoundException {@code REPORT_NOT_FOUND} if the player has no such report
     */
    @Transactional
    public ReportDto open(long reportId, long playerId) {
        Report report =
                reports.findByIdAndPlayerId(reportId, playerId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
        reports.markRead(reportId);
        return ReportDto.of(report);
    }

    /**
     * Deletes one of the player's reports.
     *
     * @throws NotFoundException {@code REPORT_NOT_FOUND} if the player has no such report
     */
    @Transactional
    public void delete(long reportId, long playerId) {
        if (reports.deleteOwned(reportId, playerId) == 0) {
            throw new NotFoundException(ErrorCode.REPORT_NOT_FOUND);
        }
    }

    /**
     * Deletes all of the player's reports up to {@code upToId} (the newest one the list showed),
     * read or not; reports that came in after it stay.
     *
     * @return the number of reports deleted
     */
    @Transactional
    public int deleteAll(long playerId, long upToId) {
        return reports.deleteOwnedUpTo(playerId, upToId);
    }

    /** Marks all of the player's reports as read. */
    @Transactional
    public void markAllRead(long playerId) {
        reports.markAllRead(playerId);
    }
}
