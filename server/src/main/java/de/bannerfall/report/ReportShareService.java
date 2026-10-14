package de.bannerfall.report;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.message.MessageService;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sending a report to other players (M40, 02-game-rules §14): each recipient gets a read-only copy
 * in his own list that says who sent it. The rules are those of private messages (§17): human
 * players only, not to yourself or to a player you blocked, one send per second (the same slot as a
 * message), at most 30 unread copies from one sender with one recipient. A recipient who blocked
 * the sender is told nothing and the sender can't tell: the copy is stored as read, hidden from the
 * recipient's list while the block lasts, and the limit counts the copies since the block.
 */
@Service
public class ReportShareService {

    /** Players one send reaches at most. */
    static final int MAX_RECIPIENTS = 10;

    /** Unread copies from one sender a recipient can have (like private messages). */
    static final int MAX_UNREAD_FROM_ONE = 30;

    private final ReportRepository reports;
    private final MessageService messages;
    private final PlayerRepository players;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReportShareService(
            ReportRepository reports,
            MessageService messages,
            PlayerRepository players,
            ApplicationEventPublisher events,
            Clock clock) {
        this.reports = reports;
        this.messages = messages;
        this.players = players;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends a copy of the sender's report to each named player (a name given twice counts once).
     * All or nothing: the first name that can't get it stops the send, and its error carries the
     * name as {@code name}. Recipients who didn't block the sender are notified after commit.
     *
     * @param names 1 to {@link #MAX_RECIPIENTS} player names, ignoring case
     * @return the recipients' names as they are spelled
     * @throws NotFoundException {@code REPORT_NOT_FOUND}; {@code PLAYER_NOT_FOUND}
     * @throws GameRuleException {@code REPORT_UNREADABLE}, {@code OWN_PLAYER}, {@code
     *     PLAYER_BLOCKED}, {@code INBOX_FULL}
     * @throws ApiException 429 {@code MESSAGE_RATE_LIMIT}
     */
    @Transactional
    public List<String> share(long reportId, long senderId, List<String> names) {
        Report original =
                reports.findByIdAndPlayerId(reportId, senderId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
        if (original.getPayload() == null) {
            throw new GameRuleException(
                    ErrorCode.REPORT_UNREADABLE, "This report can't be read any more");
        }
        Map<Long, Player> recipients = new LinkedHashMap<>();
        for (String name : names) {
            try {
                Player recipient = messages.recipientByName(senderId, name);
                recipients.putIfAbsent(recipient.getId(), recipient);
            } catch (ApiException e) {
                throw e.withProperty("name", name.trim());
            }
        }
        messages.takeSlot(senderId);

        // whether each recipient blocked the sender, and the limit, before anything is stored
        Map<Player, Boolean> hidden = new LinkedHashMap<>();
        for (Player recipient : recipients.values()) {
            Instant blockedSince = messages.blockedSince(recipient.getId(), senderId);
            long waiting =
                    blockedSince != null
                            ? reports.countByPlayerIdAndSharedByIdAndCreatedAtGreaterThanEqual(
                                    recipient.getId(), senderId, blockedSince)
                            : reports.countByPlayerIdAndSharedByIdAndReadFalse(
                                    recipient.getId(), senderId);
            if (waiting >= MAX_UNREAD_FROM_ONE) {
                throw new GameRuleException(
                                ErrorCode.INBOX_FULL,
                                "This player has too many unread reports from you")
                        .withProperty("name", recipient.getName());
            }
            hidden.put(recipient, blockedSince != null);
        }

        String senderName = players.findById(senderId).orElseThrow().getName();
        Instant now = clock.instant();
        List<String> sentTo = new ArrayList<>();
        hidden.forEach(
                (recipient, blocked) -> {
                    Report copy =
                            reports.save(
                                    Report.sharedCopy(
                                            original,
                                            recipient.getId(),
                                            senderId,
                                            senderName,
                                            blocked,
                                            now));
                    if (!blocked) {
                        events.publishEvent(
                                new PlayerNotification(
                                        recipient.getId(),
                                        new GameNotification.ReportCreated(
                                                copy.getId(), copy.getKind(), senderName)));
                    }
                    sentTo.add(recipient.getName());
                });
        return sentTo;
    }
}
