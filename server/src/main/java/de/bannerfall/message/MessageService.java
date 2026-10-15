package de.bannerfall.message;

import de.bannerfall.chat.ChatText;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.common.RateLimiter;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Private messages and the block list (02-game-rules §17). A conversation is the messages between
 * two players. Rules: the text of a chat message, one message per second and sender, at most 30
 * unread messages from one sender with the recipient. A block hides the blocked player's messages
 * from the blocker and tells the blocked one nothing: his messages are stored as read, nobody is
 * notified.
 */
@Service
public class MessageService {

    static final int HISTORY_SIZE = 100;
    static final int CONVERSATIONS = 100;
    static final int MAX_UNREAD_FROM_ONE = 30;
    static final int MAX_BLOCKS = 100;
    static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final JdbcClient jdbc;
    private final PlayerRepository players;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** When each player last sent a message; in memory like the chat's limit. */
    private final RateLimiter lastSent = new RateLimiter();

    MessageService(
            JdbcClient jdbc,
            PlayerRepository players,
            ApplicationEventPublisher events,
            Clock clock) {
        this.jdbc = jdbc;
        this.players = players;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends a message and notifies the recipient after commit (unless he blocked the sender).
     *
     * @throws ApiException 400 {@code VALIDATION} for a bad text; 429 {@code MESSAGE_RATE_LIMIT}
     * @throws GameRuleException {@code OWN_PLAYER}, {@code PLAYER_BLOCKED} (the sender blocked the
     *     recipient), {@code INBOX_FULL}
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for an unknown player, an NPC lord or a
     *     deleted account
     */
    @Transactional
    public MessageDto send(long senderId, long recipientId, String text) {
        String trimmed = ChatText.checked(text);
        if (senderId == recipientId) {
            throw new GameRuleException(ErrorCode.OWN_PLAYER, "You can't write to yourself");
        }
        Player recipient = humanPlayer(recipientId);
        if (isBlocked(senderId, recipientId)) {
            throw new GameRuleException(
                    ErrorCode.PLAYER_BLOCKED, "You blocked this player: unblock him first");
        }
        takeSlot(senderId);

        // A recipient who blocked the sender looks like one who never reads: the same limit,
        // counted
        // over what the sender wrote since the block. Without it the sender could tell the block
        // from silence (no INBOX_FULL however much he writes) and the table would grow unbounded.
        Instant blockedSince = blockedSince(recipientId, senderId);
        boolean blockedByRecipient = blockedSince != null;
        long waiting =
                blockedByRecipient
                        ? sentSince(senderId, recipientId, blockedSince)
                        : unreadFrom(senderId, recipientId);
        if (waiting >= MAX_UNREAD_FROM_ONE) {
            throw new GameRuleException(
                    ErrorCode.INBOX_FULL, "This player has too many unread messages from you");
        }
        Instant now = clock.instant();
        long id =
                jdbc.sql(
                                """
                                insert into private_message
                                    (sender_id, recipient_id, text, created_at, read_at)
                                values (:sender, :recipient, :text, :now, :readAt)
                                returning id
                                """)
                        .param("sender", senderId)
                        .param("recipient", recipientId)
                        .param("text", trimmed)
                        .param("now", Timestamp.from(now))
                        .param("readAt", blockedByRecipient ? Timestamp.from(now) : null)
                        .query(Long.class)
                        .single();
        if (!blockedByRecipient) {
            String senderName = players.findById(senderId).orElseThrow().getName();
            events.publishEvent(
                    new PlayerNotification(
                            recipient.getId(),
                            new GameNotification.MessageReceived(senderId, senderName)));
        }
        return new MessageDto(id, true, trimmed, now);
    }

    /**
     * The newest 100 messages between the caller and another player, oldest first; {@code before}
     * pages back. Empty while the caller has the other player blocked, and once the other player is
     * deleted (he takes his messages with him, 02-game-rules §17).
     *
     * @throws NotFoundException {@code PLAYER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<MessageDto> conversation(long me, long other, Long before) {
        if (!players.existsById(other)) {
            throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
        }
        if (isBlocked(me, other) || isDeleted(other)) {
            return List.of();
        }
        List<MessageDto> page =
                jdbc.sql(
                                """
                                select id, sender_id = :me as mine, text, created_at
                                from private_message
                                where ((sender_id = :me and recipient_id = :other)
                                    or (sender_id = :other and recipient_id = :me))
                                  and id < :before
                                order by id desc
                                limit :limit
                                """)
                        .param("me", me)
                        .param("other", other)
                        .param("before", before == null ? Long.MAX_VALUE : before)
                        .param("limit", HISTORY_SIZE)
                        .query(
                                (rs, n) ->
                                        new MessageDto(
                                                rs.getLong("id"),
                                                rs.getBoolean("mine"),
                                                rs.getString("text"),
                                                rs.getTimestamp("created_at").toInstant()))
                        .list();
        return page.reversed();
    }

    /**
     * The inbox: one line per player the caller talked to, newest conversation first. Deleted
     * players are left out (§17; deletion keeps the rows, code review 1.2, D6).
     */
    @Transactional(readOnly = true)
    public List<ConversationDto> conversations(long me) {
        return jdbc.sql(
                        """
                        select c.partner, p.name, t.tag, c.text, c.created_at, c.mine,
                               (select count(*) from private_message u
                                 where u.recipient_id = :me and u.sender_id = c.partner
                                   and u.read_at is null) as unread
                        from (select distinct on (partner) partner, id, text, created_at, mine
                              from (select case when sender_id = :me then recipient_id
                                                else sender_id end as partner,
                                           id, text, created_at, sender_id = :me as mine
                                    from private_message
                                    where sender_id = :me or recipient_id = :me) m
                              order by partner, id desc) c
                        join player p on p.id = c.partner
                        left join guild_member tm on tm.player_id = c.partner
                        left join guild t on t.id = tm.guild_id
                        where not exists (select 1 from player_block b
                                          where b.blocker_id = :me and b.blocked_id = c.partner)
                          and not exists (select 1 from account a
                                          where a.id = p.account_id and a.disabled)
                        order by c.id desc
                        limit :limit
                        """)
                .param("me", me)
                .param("limit", CONVERSATIONS)
                .query(
                        (rs, n) ->
                                new ConversationDto(
                                        rs.getLong("partner"),
                                        rs.getString("name"),
                                        rs.getString("tag"),
                                        rs.getString("text"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getBoolean("mine"),
                                        rs.getInt("unread")))
                .list();
    }

    /** Unread messages, not counting those of players the caller has blocked or who are deleted. */
    @Transactional(readOnly = true)
    public long unreadCount(long me) {
        return jdbc.sql(
                        """
                        select count(*) from private_message m
                        where m.recipient_id = :me and m.read_at is null
                          and not exists (select 1 from player_block b
                                          where b.blocker_id = :me and b.blocked_id = m.sender_id)
                          and not exists (select 1 from player sp
                                          join account a on a.id = sp.account_id
                                          where sp.id = m.sender_id and a.disabled)
                        """)
                .param("me", me)
                .query(Long.class)
                .single();
    }

    /** Marks everything the other player sent the caller as read. Idempotent. */
    @Transactional
    public void markRead(long me, long other) {
        jdbc.sql(
                        """
                        update private_message set read_at = :now
                        where recipient_id = :me and sender_id = :other and read_at is null
                        """)
                .param("now", Timestamp.from(clock.instant()))
                .param("me", me)
                .param("other", other)
                .update();
    }

    /**
     * Puts a player on the block list. Idempotent.
     *
     * @throws GameRuleException {@code OWN_PLAYER}, {@code BLOCK_LIST_FULL}
     * @throws NotFoundException {@code PLAYER_NOT_FOUND}
     */
    @Transactional
    public void block(long me, long other) {
        if (me == other) {
            throw new GameRuleException(ErrorCode.OWN_PLAYER, "You can't block yourself");
        }
        humanPlayer(other);
        if (isBlocked(me, other)) {
            return;
        }
        long blocks =
                jdbc.sql("select count(*) from player_block where blocker_id = :me")
                        .param("me", me)
                        .query(Long.class)
                        .single();
        if (blocks >= MAX_BLOCKS) {
            throw new GameRuleException(
                    ErrorCode.BLOCK_LIST_FULL, "The block list has at most " + MAX_BLOCKS);
        }
        jdbc.sql(
                        """
                        insert into player_block (blocker_id, blocked_id, created_at)
                        values (:me, :other, :now) on conflict do nothing
                        """)
                .param("me", me)
                .param("other", other)
                .param("now", Timestamp.from(clock.instant()))
                .update();
    }

    /** Takes a player off the block list; his messages show again. Idempotent. */
    @Transactional
    public void unblock(long me, long other) {
        jdbc.sql("delete from player_block where blocker_id = :me and blocked_id = :other")
                .param("me", me)
                .param("other", other)
                .update();
    }

    /** The block list, newest block first. */
    @Transactional(readOnly = true)
    public List<BlockedPlayerDto> blocks(long me) {
        return jdbc.sql(
                        """
                        select p.id, p.name from player_block b join player p on p.id = b.blocked_id
                        where b.blocker_id = :me order by b.created_at desc, p.id
                        """)
                .param("me", me)
                .query((rs, n) -> new BlockedPlayerDto(rs.getLong("id"), rs.getString("name")))
                .list();
    }

    /**
     * A player {@code senderId} may send messages or reports to, found by name (ignoring case; M40
     * sends reports under the rules of messages).
     *
     * @throws GameRuleException {@code OWN_PLAYER}, {@code PLAYER_BLOCKED} (the sender blocked him)
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} for an unknown name, an NPC lord or a
     *     deleted account
     */
    @Transactional(readOnly = true)
    public Player recipientByName(long senderId, String name) {
        Player recipient =
                players.findHumanByName(name.trim())
                        .filter(this::hasActiveAccount)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
        if (recipient.getId() == senderId) {
            throw new GameRuleException(ErrorCode.OWN_PLAYER, "You can't send to yourself");
        }
        if (isBlocked(senderId, recipient.getId())) {
            throw new GameRuleException(
                    ErrorCode.PLAYER_BLOCKED, "You blocked this player: unblock him first");
        }
        return recipient;
    }

    /**
     * Whether {@code senderId} may send messages or reports to the player: another human with an
     * active account whom the sender hasn't blocked. The rules of {@link #recipientByName} without
     * its errors, for sending a report to the whole guild, which skips who fails them.
     */
    @Transactional(readOnly = true)
    public boolean canSendTo(long senderId, Player recipient) {
        return recipient.getId() != senderId
                && !recipient.isNpc()
                && hasActiveAccount(recipient)
                && !isBlocked(senderId, recipient.getId());
    }

    /** A player who can read messages: no NPC lord, no deleted or for good banned account. */
    private Player humanPlayer(long id) {
        return players.findById(id)
                .filter(p -> !p.isNpc() && hasActiveAccount(p))
                .orElseThrow(() -> new NotFoundException(ErrorCode.PLAYER_NOT_FOUND));
    }

    /** The player's account is deleted (disabled); an NPC lord has none and isn't. */
    private boolean isDeleted(long playerId) {
        return jdbc.sql(
                        """
                        select exists (select 1 from player p join account a on a.id = p.account_id
                                       where p.id = :id and a.disabled)
                        """)
                .param("id", playerId)
                .query(Boolean.class)
                .single();
    }

    private boolean hasActiveAccount(Player player) {
        return player.getAccountId() != null
                && jdbc.sql("select exists (select 1 from account where id = :id and not disabled)")
                        .param("id", player.getAccountId())
                        .query(Boolean.class)
                        .single();
    }

    private boolean isBlocked(long blocker, long blocked) {
        return jdbc.sql(
                        "select exists (select 1 from player_block"
                                + " where blocker_id = :blocker and blocked_id = :blocked)")
                .param("blocker", blocker)
                .param("blocked", blocked)
                .query(Boolean.class)
                .single();
    }

    /** When {@code blocker} put {@code blocked} on the block list, null if he didn't. */
    @Transactional(readOnly = true)
    public Instant blockedSince(long blocker, long blocked) {
        return jdbc.sql(
                        "select created_at from player_block"
                                + " where blocker_id = :blocker and blocked_id = :blocked")
                .param("blocker", blocker)
                .param("blocked", blocked)
                .query((rs, n) -> rs.getTimestamp("created_at").toInstant())
                .optional()
                .orElse(null);
    }

    private long sentSince(long senderId, long recipientId, Instant since) {
        return jdbc.sql(
                        "select count(*) from private_message"
                                + " where sender_id = :sender and recipient_id = :recipient"
                                + " and created_at >= :since")
                .param("sender", senderId)
                .param("recipient", recipientId)
                .param("since", Timestamp.from(since))
                .query(Long.class)
                .single();
    }

    private long unreadFrom(long senderId, long recipientId) {
        return jdbc.sql(
                        "select count(*) from private_message"
                                + " where sender_id = :sender and recipient_id = :recipient"
                                + " and read_at is null")
                .param("sender", senderId)
                .param("recipient", recipientId)
                .query(Long.class)
                .single();
    }

    /**
     * Takes the sender's message slot; it counts only for a message that is really sent. Sending a
     * report to players takes the same slot (M40).
     *
     * @throws ApiException 429 {@code MESSAGE_RATE_LIMIT} if his last message is less than a second
     *     old
     */
    public void takeSlot(long senderId) {
        lastSent.take(
                senderId,
                clock.instant(),
                MIN_INTERVAL,
                () ->
                        new ApiException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                ErrorCode.MESSAGE_RATE_LIMIT,
                                "One message per second"));
    }
}
