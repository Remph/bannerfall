package de.bannerfall.guild;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GuildRepository extends JpaRepository<Guild, Long> {

    /**
     * The guild with its row locked until the end of the transaction. Every request that changes
     * who is in a guild (join, leave, kick, hand-over, disband) starts here, so two of them can't
     * overfill a guild or leave it without a leader.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Guild t where t.id = :id")
    Optional<Guild> findForUpdate(long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByTagIgnoreCase(String tag);

    /** All guilds, strongest first; equal points by age. Guilds always have at least one member. */
    @Query(
            """
            select new de.bannerfall.guild.GuildSummaryDto(
                       t.id, t.name, t.tag, count(m), coalesce(sum(p.points), 0))
            from Guild t
            join GuildMember m on m.guildId = t.id
            join Player p on p.id = m.playerId
            group by t.id, t.name, t.tag
            order by coalesce(sum(p.points), 0) desc, t.id
            """)
    List<GuildSummaryDto> findSummaries();

    /** The guild of a player, empty if he is in none. */
    @Query(
            """
            select new de.bannerfall.guild.GuildRefDto(t.id, t.name, t.tag)
            from GuildMember m join Guild t on t.id = m.guildId
            where m.playerId = :playerId
            """)
    Optional<GuildRefDto> findRefOf(long playerId);
}
