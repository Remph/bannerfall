package de.bannerfall.village;

import de.bannerfall.common.Coordinates;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VillageRepository extends JpaRepository<Village, Long> {

    /** {@code select ... for update}, rows locked in id order. Use {@link VillageLocker}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Village v where v.id in :ids order by v.id")
    List<Village> lockAllInIdOrder(Collection<Long> ids);

    /** {@code select ... for update} of a village that belongs to the player. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Village v where v.id = :id and v.player.id = :playerId")
    Optional<Village> lockOwned(long id, long playerId);

    /** The points of all villages of a player (0 if he has none). */
    @Query("select coalesce(sum(v.points), 0L) from Village v where v.player.id = :playerId")
    long sumPointsOfPlayer(long playerId);

    /** Villages with their owners in one query, for lists that show many (the farm list). */
    @Query("select v from Village v left join fetch v.player where v.id in :ids")
    List<Village> findAllWithPlayer(Collection<Long> ids);

    /** The village on a field, if there is one ({@code x, y} are unique). */
    Optional<Village> findByXAndY(int x, int y);

    /** Every occupied field (a few hundred villages at most). */
    @Query("select new de.bannerfall.common.Coordinates(v.x, v.y) from Village v")
    List<Coordinates> findAllCoordinates();

    /**
     * The villages of a player's guild mates (1.6, 02-game-rules §2): the leader's first, then by
     * membership, longest first, and each mate's main village (held the longest) first.
     */
    @Query(
            """
            select new de.bannerfall.common.Coordinates(v.x, v.y)
            from Village v, de.bannerfall.guild.GuildMember m
            where m.guildId = :guildId and m.playerId <> :playerId and v.player.id = m.playerId
            order by case when m.role = de.bannerfall.guild.GuildRole.LEADER then 0 else 1 end,
                     m.joinedAt, m.playerId, v.ownerSince, v.id
            """)
    List<Coordinates> findGuildMateCoordinates(long guildId, long playerId);

    /** All barbarian villages, for the growth round (they are locked afterwards). */
    @Query("select v.id from Village v where v.player is null order by v.id")
    List<Long> findBarbarianIds();

    /** The villages of a player, for locking. */
    @Query("select v.id from Village v where v.player.id = :playerId order by v.id")
    List<Long> findIdsByPlayerId(long playerId);

    /** The villages of a player, oldest first. */
    List<Village> findByPlayerIdOrderByIdAsc(long playerId);

    /** How many villages the player has. */
    long countByPlayerId(long playerId);

    /** The banner keeps on the map (v1.3, 02-game-rules §22). */
    long countByKeepTrue();

    /** Villages owned by players (humans and NPC lords), for the spawn radius. */
    long countByPlayerIsNotNull();

    @Query(
            """
            select new de.bannerfall.village.VillageSummaryDto(
                v.id, v.name, v.x, v.y, v.points,
                cast((select count(m) from Movement m
                      where m.targetVillage = v
                        and m.kind = de.bannerfall.movement.MovementKind.ATTACK
                        and m.ownerPlayerId <> :playerId) as Integer))
            from Village v where v.player.id = :playerId order by v.ownerSince, v.id
            """)
    List<VillageSummaryDto> findSummariesByPlayerId(long playerId);

    /**
     * Gives every village with a default name of {@code from} the default name of {@code to}
     * ("Ben's village" → "Dorf von Ben", v1.3, 02-game-rules §2), unless that is too long for the
     * column. A default name is the pattern around a player's name (the owner's, or the previous
     * owner's after a conquest), so a name a player gave in the same shape ("Grandma's village")
     * stays. Bumps the version, so a write that read the village before fails instead of putting
     * the old name back.
     *
     * @return how many villages were renamed
     */
    @Modifying
    @Query(
            value =
                    """
                    update village v set
                        name = :toPrefix
                            || substr(v.name, length(:fromPrefix) + 1,
                                      length(v.name) - length(:fromPrefix) - length(:fromSuffix))
                            || :toSuffix,
                        version = v.version + 1
                    where v.name like :fromPattern escape '\\'
                      and length(v.name) - length(:fromPrefix) - length(:fromSuffix)
                          + length(:toPrefix) + length(:toSuffix) <= :maxLength
                      and exists (
                          select 1 from player p
                          where p.name = substr(v.name, length(:fromPrefix) + 1,
                                                length(v.name) - length(:fromPrefix)
                                                    - length(:fromSuffix)))
                    """,
            nativeQuery = true)
    int renameDefaultNames(
            String fromPrefix,
            String fromSuffix,
            String fromPattern,
            String toPrefix,
            String toSuffix,
            int maxLength);

    /** A player's villages as a profile shows them, oldest first. */
    @Query(
            """
            select new de.bannerfall.village.ProfileVillageDto(v.id, v.name, v.x, v.y, v.points)
            from Village v where v.player.id = :playerId order by v.id
            """)
    List<ProfileVillageDto> findProfileVillagesByPlayerId(long playerId);
}
