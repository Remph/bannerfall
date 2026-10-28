package de.bannerfall.chat;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.guild.GuildMemberRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which chat messages a player has read (1.7), so the unread badge follows him from device to
 * device. One marker per channel, the id of the newest message he has seen; it only moves forward.
 */
@Service
public class ChatSeenService {

    private final JdbcClient jdbc;
    private final GuildMemberRepository guildMembers;
    private final ApplicationEventPublisher events;

    ChatSeenService(
            JdbcClient jdbc, GuildMemberRepository guildMembers, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.guildMembers = guildMembers;
        this.events = events;
    }

    /** All the player's markers (the web picks the world's and his current guild's). */
    @Transactional(readOnly = true)
    public List<ChatSeenDto> markers(long playerId) {
        return jdbc.sql("select guild_id, last_seen_id from chat_seen where player_id = ?")
                .param(playerId)
                .query(
                        (rs, row) ->
                                new ChatSeenDto(
                                        rs.getObject("guild_id", Long.class),
                                        rs.getLong("last_seen_id")))
                .list();
    }

    /**
     * Moves the marker of a channel forward to {@code id}, at most to the newest message there is
     * (a marker from the future would hide the messages to come). An older {@code id} changes
     * nothing. Only a marker that really moved is sent to the player's connections.
     *
     * @return the marker as it stands now, so a device that was behind catches up
     * @throws GameRuleException {@code NOT_IN_GUILD} if {@code guildId} is not the player's guild
     */
    @Transactional
    public ChatSeenDto markSeen(long playerId, Long guildId, long id) {
        if (guildId != null && !guildMembers.findGuildIdOf(playerId).equals(Optional.of(guildId))) {
            throw new GameRuleException(ErrorCode.NOT_IN_GUILD, "You are not in that guild");
        }
        long newest = newestMessage(guildId);
        long capped = Math.min(id, newest);
        if (capped < 1) {
            return new ChatSeenDto(guildId, current(playerId, guildId));
        }
        Optional<Long> moved =
                jdbc.sql(
                                """
                                insert into chat_seen (player_id, guild_id, last_seen_id)
                                values (?, ?, ?)
                                on conflict (player_id, (coalesce(guild_id, 0))) do update
                                    set last_seen_id = excluded.last_seen_id
                                    where chat_seen.last_seen_id < excluded.last_seen_id
                                returning last_seen_id
                                """)
                        .param(playerId)
                        .param(guildId)
                        .param(capped)
                        .query(Long.class)
                        .optional();
        if (moved.isEmpty()) {
            return new ChatSeenDto(guildId, current(playerId, guildId));
        }
        ChatSeenDto seen = new ChatSeenDto(guildId, moved.get());
        events.publishEvent(new ChatSeenUpdate(playerId, seen));
        return seen;
    }

    private long newestMessage(Long guildId) {
        String where = guildId == null ? "guild_id is null" : "guild_id = ?";
        var statement =
                jdbc.sql(
                        "select coalesce(max(id), 0) from chat_message where deleted = false and "
                                + where);
        return (guildId == null ? statement : statement.param(guildId)).query(Long.class).single();
    }

    private long current(long playerId, Long guildId) {
        return jdbc.sql(
                        "select coalesce(max(last_seen_id), 0) from chat_seen where player_id = ?"
                                + " and coalesce(guild_id, 0) = ?")
                .param(playerId)
                .param(guildId == null ? 0L : guildId)
                .query(Long.class)
                .single();
    }
}
