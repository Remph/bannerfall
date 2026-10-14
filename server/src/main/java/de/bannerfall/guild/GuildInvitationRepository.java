package de.bannerfall.guild;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GuildInvitationRepository extends JpaRepository<GuildInvitation, Long> {

    String DTO =
            """
            select new de.bannerfall.guild.GuildInvitationDto(
                       i.id, t.id, t.name, t.tag, p.id, p.name, inviter.name, i.createdAt)
            from GuildInvitation i
            join Guild t on t.id = i.guildId
            join Player p on p.id = i.playerId
            join Player inviter on inviter.id = i.invitedBy
            """;

    /** The open invitations of a guild, newest first. */
    @Query(DTO + " where i.guildId = :guildId order by i.id desc")
    List<GuildInvitationDto> findOfGuild(long guildId);

    /** The invitations a player has received, newest first. */
    @Query(DTO + " where i.playerId = :playerId order by i.id desc")
    List<GuildInvitationDto> findOfPlayer(long playerId);

    Optional<GuildInvitation> findByGuildIdAndPlayerId(long guildId, long playerId);

    /** Whether this invitation still exists and is the player's (a database query, not a cache). */
    boolean existsByIdAndPlayerId(long id, long playerId);

    /** The guilds that have an open invitation out to a player: whose leaders must hear of it. */
    @Query("select i.guildId from GuildInvitation i where i.playerId = :playerId")
    List<Long> guildIdsInvitingPlayer(long playerId);

    /** The players a guild has invited. */
    @Query("select i.playerId from GuildInvitation i where i.guildId = :guildId")
    List<Long> invitedPlayerIds(long guildId);

    @Modifying
    @Query("delete from GuildInvitation i where i.playerId = :playerId")
    void deleteAllFor(long playerId);
}
