package de.bannerfall.report;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.guild.GuildMemberRepository;
import de.bannerfall.message.MessageService;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
 * recipient's list while the block lasts, and the limit counts the copies since the block. Since
 * v1.2.3 a report can also go to the whole guild in one go ({@link #shareWithGuild}).
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
    private final GuildMemberRepository guildMembers;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReportShareService(
            ReportRepository reports,
            MessageService messages,
            PlayerRepository players,
            GuildMemberRepository guildMembers,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock) {
        this.reports = reports;
        this.messages = messages;
        this.players = players;
        this.guildMembers = guildMembers;
        this.worldService = worldService;
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
        Report original = shareable(reportId, senderId);
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
            if (inboxFull(recipient.getId(), senderId, blockedSince)) {
                throw new GameRuleException(
                                ErrorCode.INBOX_FULL,
                                "This player has too many unread reports from you")
                        .withProperty("name", recipient.getName());
            }
            hidden.put(recipient, blockedSince != null);
        }
        return store(original, senderId, hidden);
    }

    /**
     * Sends a copy of the sender's report to every other member of his guild, so nobody has to type
     * a name. Unlike {@link #share} it is not all or nothing and has no {@link #MAX_RECIPIENTS}: it
     * skips the members a named send would refuse (those the sender blocked, deleted accounts,
     * those with {@link #MAX_UNREAD_FROM_ONE} unread copies from him) and sends to the rest. A
     * member who blocked the sender gets the hidden copy of {@link #share}, so he still counts as a
     * recipient.
     *
     * @return the recipients' names as they are spelled, by name; empty if no member can get it
     * @throws NotFoundException {@code REPORT_NOT_FOUND}
     * @throws GameRuleException {@code REPORT_UNREADABLE}, {@code GUILDS_DISABLED}, {@code
     *     NOT_IN_GUILD}
     * @throws ApiException 429 {@code MESSAGE_RATE_LIMIT}
     */
    @Transactional
    public List<String> shareWithGuild(long reportId, long senderId) {
        Report original = shareable(reportId, senderId);
        if (!worldService.settings().guildsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched guilds on");
        }
        long guildId =
                guildMembers
                        .findGuildIdOf(senderId)
                        .orElseThrow(
                                () ->
                                        new GameRuleException(
                                                ErrorCode.NOT_IN_GUILD, "You are in no guild"));
        List<Player> members =
                players.findAllById(guildMembers.playerIdsOf(guildId)).stream()
                        .sorted(
                                Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
                                        .thenComparing(Player::getId))
                        .toList();
        messages.takeSlot(senderId);

        Map<Player, Boolean> hidden = new LinkedHashMap<>();
        for (Player member : members) {
            if (!messages.canSendTo(senderId, member)) {
                continue;
            }
            Instant blockedSince = messages.blockedSince(member.getId(), senderId);
            if (!inboxFull(member.getId(), senderId, blockedSince)) {
                hidden.put(member, blockedSince != null);
            }
        }
        return store(original, senderId, hidden);
    }

    /** The sender's report, if it can still be read. */
    private Report shareable(long reportId, long senderId) {
        Report original =
                reports.findByIdAndPlayerId(reportId, senderId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.REPORT_NOT_FOUND));
        if (original.getPayload() == null) {
            throw new GameRuleException(
                    ErrorCode.REPORT_UNREADABLE, "This report can't be read any more");
        }
        return original;
    }

    /**
     * Whether the recipient has {@link #MAX_UNREAD_FROM_ONE} copies from the sender waiting: unread
     * ones, or, if he blocked the sender ({@code blockedSince} not null), all since the block.
     */
    private boolean inboxFull(long recipientId, long senderId, Instant blockedSince) {
        long waiting =
                blockedSince != null
                        ? reports.countByPlayerIdAndSharedByIdAndCreatedAtGreaterThanEqual(
                                recipientId, senderId, blockedSince)
                        : reports.countByPlayerIdAndSharedByIdAndReadFalse(recipientId, senderId);
        return waiting >= MAX_UNREAD_FROM_ONE;
    }

    /**
     * Stores a copy for each recipient (hidden for those who blocked the sender, the map's value)
     * and notifies the others after commit.
     *
     * @return the recipients' names in the map's order
     */
    private List<String> store(Report original, long senderId, Map<Player, Boolean> hidden) {
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
