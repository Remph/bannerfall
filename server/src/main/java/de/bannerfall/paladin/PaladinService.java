package de.bannerfall.paladin;

import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.PaladinRules;
import de.bannerfall.unit.PlayerUnitCount;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
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
    private final PlayerUnitCount unitCount;
    private final PlayerRepository players;
    private final WorldService worldService;
    private final GameData gameData;

    PaladinService(
            VillageRepository villages,
            PlayerUnitCount unitCount,
            PlayerRepository players,
            WorldService worldService,
            GameData gameData) {
        this.villages = villages;
        this.unitCount = unitCount;
        this.players = players;
        this.worldService = worldService;
        this.gameData = gameData;
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
     * Paladins that take a slot: at home, supporting, on the way, in training, waiting to be
     * dismissed ({@link PlayerUnitCount}).
     */
    @Transactional(readOnly = true)
    public int used(long playerId) {
        return unitCount.owned(playerId, UnitType.PALADIN);
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
