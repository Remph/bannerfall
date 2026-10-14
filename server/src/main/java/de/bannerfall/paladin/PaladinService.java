package de.bannerfall.paladin;

import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.PaladinRules;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paladin slots and the paladins that take them (02-game-rules §5.5). A paladin takes a slot from
 * the moment he is ordered until he is dead or dismissed: standing at home or in a support, on the
 * way, in training, or waiting to be dismissed. Nobody is removed when the slots shrink (a lost
 * village, a lower maximum): the player just cannot recruit another one until he is below his slots
 * again.
 */
@Service
public class PaladinService {

    private final VillageRepository villages;
    private final JdbcClient jdbc;
    private final PlayerRepository players;
    private final WorldService worldService;
    private final GameData gameData;
    private final Clock clock;

    PaladinService(
            VillageRepository villages,
            JdbcClient jdbc,
            PlayerRepository players,
            WorldService worldService,
            GameData gameData,
            Clock clock) {
        this.villages = villages;
        this.jdbc = jdbc;
        this.players = players;
        this.worldService = worldService;
        this.gameData = gameData;
        this.clock = clock;
    }

    /** The paladin slots of the player now. */
    @Transactional(readOnly = true)
    public PaladinDto of(long playerId) {
        WorldSettings world = worldService.settings();
        int owned = Math.toIntExact(villages.countByPlayerId(playerId));
        int next = PaladinRules.nextSlotAtVillages(owned, world);
        return new PaladinDto(
                world.paladinMaxSlots(),
                world.paladinVillagesPerSlot(),
                owned,
                PaladinRules.slots(owned, world),
                used(playerId),
                next < 0 ? null : next,
                gameData.unit(UnitType.PALADIN).minutesPerField()
                        / (world.speed() * world.unitSpeed()));
    }

    /**
     * Paladins that take a slot, read in one statement: the database runs at READ COMMITTED, so
     * separate sums could each see their own moment and miss a paladin that moves between them
     * (from the recruit queue into a stack, from a movement into a stack). A dismissal frees its
     * slots by the clock (RecruitRules.finishedBy), not when the village is caught up next.
     */
    @Transactional(readOnly = true)
    public int used(long playerId) {
        // plain SQL doesn't flush by itself: an order or stack this transaction just changed would
        // still be pending
        players.flush();
        long used =
                jdbc.sql(
                                """
                                select
                                  (select coalesce(sum(s.u_paladin), 0) from unit_stack s
                                     join village v on v.id = s.home_village_id
                                     where v.player_id = :player)
                                + (select coalesce(sum(m.u_paladin), 0) from movement m
                                     join village v on v.id = m.home_village_id
                                     where v.player_id = :player)
                                + (select coalesce(sum(o.count - o.completed_count), 0)
                                     from recruit_order o join village v on v.id = o.village_id
                                     where v.player_id = :player and o.unit = 'PALADIN')
                                + (select coalesce(sum(o.count - least(o.count,
                                       greatest(0, floor(extract(epoch from
                                         (cast(:now as timestamptz) - o.start_at))))::bigint
                                       / o.seconds_per_unit)), 0)
                                     from dismiss_order o join village v on v.id = o.village_id
                                     where v.player_id = :player and o.unit = 'PALADIN')
                                """)
                        .param("player", playerId)
                        .param("now", Timestamp.from(clock.instant()))
                        .query(Long.class)
                        .single();
        return Math.toIntExact(used);
    }

    /** Slots not taken. Never negative (more paladins than slots leave none free). */
    @Transactional(readOnly = true)
    public int free(long playerId) {
        PaladinDto dto = of(playerId);
        return Math.max(0, dto.slots() - dto.used());
    }

    /**
     * Takes the lock that keeps two villages of one player from filling the last slot at once. Call
     * it after the village lock (the village lock order stays village → player) and before {@link
     * #free}.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockSlots(long playerId) {
        players.lockById(playerId);
    }
}
