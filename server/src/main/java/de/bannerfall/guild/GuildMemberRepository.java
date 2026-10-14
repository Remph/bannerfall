package de.bannerfall.guild;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {

    /**
     * The id of the player's guild, empty if he is in none. A plain value, not the entity: the
     * caller locks the guild first and loads the membership after, so it never sees a stale role.
     */
    @Query("select m.guildId from GuildMember m where m.playerId = :playerId")
    Optional<Long> findGuildIdOf(long playerId);

    long countByGuildId(long guildId);

    /** The members, leader first, then by points. */
    @Query(
            """
            select new de.bannerfall.guild.GuildMemberDto(
                       p.id, p.name, m.role, p.points, p.villageCount, m.joinedAt)
            from GuildMember m join Player p on p.id = m.playerId
            where m.guildId = :guildId
            order by case when m.role = de.bannerfall.guild.GuildRole.LEADER then 0 else 1 end,
                     p.points desc, p.name
            """)
    List<GuildMemberDto> membersOf(long guildId);

    /** The leader of a guild (it has exactly one). */
    @Query(
            "select m.playerId from GuildMember m where m.guildId = :guildId and m.role ="
                    + " de.bannerfall.guild.GuildRole.LEADER")
    Optional<Long> leaderIdOf(long guildId);

    /** The ids of the members: who hears of a change. */
    @Query("select m.playerId from GuildMember m where m.guildId = :guildId")
    List<Long> playerIdsOf(long guildId);

    /** The members other than one player, longest member first: the next leader. */
    @Query(
            """
            select m from GuildMember m
            where m.guildId = :guildId and m.playerId <> :exceptPlayerId
            order by m.joinedAt, m.playerId
            """)
    List<GuildMember> othersOldestFirst(long guildId, long exceptPlayerId);
}
