package de.bannerfall.chat;

import de.bannerfall.account.Account;
import de.bannerfall.account.AccountBannedException;
import de.bannerfall.account.AccountRepository;
import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.common.RateLimiter;
import de.bannerfall.guild.GuildMember;
import de.bannerfall.guild.GuildMemberRepository;
import de.bannerfall.guild.GuildRole;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.world.WorldService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chats (02-game-rules §13, §16): the global channel and, since M15b, one per guild. Messages
 * of 1–500 characters after trimming, at most one per second and player, the last 100 on load.
 */
@Service
public class ChatService {

    static final int HISTORY_SIZE = 100;
    static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final ChatMessageRepository messages;
    private final PlayerRepository players;
    private final AccountRepository accounts;
    private final GuildMemberRepository guildMembers;
    private final WorldService worldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AchievementService achievements;

    /**
     * When each player last sent a message. In memory like the login rate limit: a restart forgets
     * it, which costs at most one extra message.
     */
    private final RateLimiter lastSent = new RateLimiter();

    ChatService(
            ChatMessageRepository messages,
            PlayerRepository players,
            AccountRepository accounts,
            GuildMemberRepository guildMembers,
            WorldService worldService,
            ApplicationEventPublisher events,
            Clock clock,
            AchievementService achievements) {
        this.messages = messages;
        this.players = players;
        this.accounts = accounts;
        this.guildMembers = guildMembers;
        this.worldService = worldService;
        this.events = events;
        this.clock = clock;
        this.achievements = achievements;
    }

    /**
     * Saves the message and sends it to everyone on {@code /topic/chat} after commit.
     *
     * @throws ApiException 400 {@code VALIDATION} if the text is empty after trimming or longer
     *     than 500 characters
     * @throws GameRuleException {@code CHAT_RATE_LIMIT} if the player's last message is less than a
     *     second old
     * @throws NotFoundException {@code PLAYER_NOT_FOUND} if the admin deleted the player: his open
     *     WebSocket outlives the deletion (M14a; M14e closes the connections of banned players)
     */
    @Transactional
    public ChatMessageDto send(long playerId, String text) {
        return post(playerId, text, null);
    }

    /**
     * Saves a message for the player's guild and sends it to the members' {@code
     * /user/queue/guild-chat} after commit (M15b). Same text rules and the same one-per-second
     * limit as {@link #send}, shared with it.
     *
     * @throws GameRuleException {@code GUILDS_DISABLED}, {@code NOT_IN_GUILD}, and those of {@link
     *     #send}
     */
    @Transactional
    public ChatMessageDto sendToGuild(long playerId, String text) {
        requireGuildsEnabled();
        long guildId = guildOf(playerId);
        return post(playerId, text, guildId);
    }

    private ChatMessageDto post(long playerId, String text, Long guildId) {
        String trimmed = ChatText.checked(text);
        Instant now = clock.instant();
        // The slot counts only for a message that is really sent: if the transaction rolls back,
        // the player may try again at once.
        lastSent.take(
                playerId,
                now,
                MIN_INTERVAL,
                () -> new GameRuleException(ErrorCode.CHAT_RATE_LIMIT, "One message per second"));

        Player author = players.findById(playerId).orElseThrow();
        if (author.getAccountId() != null) {
            Account account = accounts.findById(author.getAccountId()).orElseThrow();
            if (account.isDisabled()) {
                throw new NotFoundException(ErrorCode.PLAYER_NOT_FOUND);
            }
            // His connection is closed at the ban (M14e); this covers a frame already on its way.
            if (account.isBannedAt(now)) {
                throw new AccountBannedException(account.getBannedUntil(), account.getBanReason());
            }
        }
        ChatMessage saved = messages.save(new ChatMessage(playerId, trimmed, now, guildId));
        if (guildId == null) {
            achievements.grant(author, Achievement.FIRST_WORDS); // 02-game-rules §19
        }
        ChatMessageDto dto =
                new ChatMessageDto(
                        saved.getId(), playerId, author.getName(), trimmed, saved.getCreatedAt());
        if (guildId == null) {
            events.publishEvent(dto);
        } else {
            events.publishEvent(new GuildChatDelivery(guildMembers.playerIdsOf(guildId), dto));
        }
        return dto;
    }

    /**
     * A message from the host to everybody (M14d, 08-host-console §2), e.g. "restart in 5 minutes":
     * stored and sent like a chat message, without an author. No rate limit.
     *
     * @throws ApiException 400 {@code VALIDATION} as for {@link #send}
     */
    @Transactional
    public ChatMessageDto broadcast(String text) {
        String trimmed = ChatText.checked(text);
        ChatMessage saved = messages.save(new ChatMessage(null, trimmed, clock.instant()));
        ChatMessageDto dto =
                new ChatMessageDto(saved.getId(), null, null, trimmed, saved.getCreatedAt());
        events.publishEvent(dto);
        return dto;
    }

    /**
     * Hides a message from the history (admin, M14a) and tells the open chats on {@code
     * /topic/chat} after commit ({@link ChatHiddenDto}). Idempotent: hiding it again sends nothing.
     */
    @Transactional
    public void hide(long messageId) {
        ChatMessage message =
                messages.findById(messageId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.isDeleted()) {
            message.hide();
            publishHidden(message);
        }
    }

    /**
     * The leader of a guild hides a message of his guild's chat (M15b); open guild chats remove it.
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}
     * @throws ApiException 403 {@code NOT_GUILD_LEADER}
     * @throws NotFoundException {@code MESSAGE_NOT_FOUND} for a message that isn't in his guild's
     *     chat (the same answer as for one that doesn't exist, so ids of other chats stay hidden)
     */
    @Transactional
    public void hideInGuild(long playerId, long messageId) {
        GuildMember me = guildMembers.findById(playerId).orElseThrow(this::notInGuild);
        if (me.getRole() != GuildRole.LEADER) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.NOT_GUILD_LEADER,
                    "Only the leader hides guild messages");
        }
        ChatMessage message =
                messages.findById(messageId)
                        .filter(m -> me.getGuildId().equals(m.getGuildId()))
                        .orElseThrow(() -> new NotFoundException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!message.isDeleted()) {
            message.hide();
            publishHidden(message);
        }
    }

    private void publishHidden(ChatMessage message) {
        ChatHiddenDto hidden = new ChatHiddenDto(message.getId());
        if (message.getGuildId() == null) {
            events.publishEvent(hidden);
        } else {
            events.publishEvent(
                    new GuildChatDelivery(guildMembers.playerIdsOf(message.getGuildId()), hidden));
        }
    }

    /**
     * The newest 100 visible messages, oldest first (the order the chat shows them); with {@code
     * before} the 100 before that message id (scrolling up).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> history(Long before) {
        List<ChatMessageDto> page =
                new ArrayList<>(
                        messages.findNewestBefore(
                                before == null ? Long.MAX_VALUE : before,
                                PageRequest.of(0, HISTORY_SIZE)));
        return page.reversed();
    }

    /**
     * The newest 100 visible messages of the player's guild, oldest first; {@code before} pages
     * further back (M15b). Readable while the host has guilds switched off, like the guilds.
     *
     * @throws GameRuleException {@code NOT_IN_GUILD}
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> guildHistory(long playerId, Long before) {
        long guildId = guildOf(playerId);
        List<ChatMessageDto> page =
                new ArrayList<>(
                        messages.findNewestOfGuildBefore(
                                guildId,
                                before == null ? Long.MAX_VALUE : before,
                                PageRequest.of(0, HISTORY_SIZE)));
        return page.reversed();
    }

    private long guildOf(long playerId) {
        return guildMembers.findGuildIdOf(playerId).orElseThrow(this::notInGuild);
    }

    private GameRuleException notInGuild() {
        return new GameRuleException(ErrorCode.NOT_IN_GUILD, "You are in no guild");
    }

    private void requireGuildsEnabled() {
        if (!worldService.settings().guildsEnabled()) {
            throw new GameRuleException(
                    ErrorCode.GUILDS_DISABLED, "The host has not switched guilds on");
        }
    }
}
