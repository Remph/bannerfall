package de.bannerfall.noble;

import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.NobleRules;
import de.bannerfall.unit.PlayerUnitCount;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rising noble price (1.6, 02-game-rules §5.7): a player's rank and the price of his next
 * noble, counted from what he owns at the moment. Nothing is stored: a lost village, a dead or
 * dismissed noble and a cancelled order lower the rank at once.
 */
@Service
public class NobleService {

    private final VillageRepository villages;
    private final PlayerUnitCount unitCount;
    private final PlayerRepository players;
    private final WorldService worldService;
    private final GameData gameData;

    NobleService(
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

    /** The player's rank and the price of his next noble and of the one after it. */
    @Transactional(readOnly = true)
    public NobleDto of(long playerId) {
        double step = worldService.settings().noblePriceStep();
        int owned = Math.toIntExact(villages.countByPlayerId(playerId));
        int nobles = unitCount.owned(playerId, UnitType.NOBLE);
        int rank = NobleRules.rank(owned, nobles);
        ResourceAmounts base = baseCost();
        return new NobleDto(
                owned,
                nobles,
                NobleRules.FREE_RANKS,
                rank,
                step,
                NobleRules.MAX_FACTOR,
                NobleRules.factor(step, rank),
                base,
                NobleRules.price(base, step, rank),
                NobleRules.price(base, step, NobleRules.rank(owned, nobles + 1)));
    }

    /** Villages plus nobles the player owns now: what his rank counts (§5.7). */
    @Transactional(readOnly = true)
    public int held(long playerId) {
        return Math.toIntExact(villages.countByPlayerId(playerId))
                + unitCount.owned(playerId, UnitType.NOBLE);
    }

    /** The price of the player's next noble. */
    @Transactional(readOnly = true)
    public ResourceAmounts nextPrice(long playerId) {
        return NobleRules.price(
                baseCost(),
                worldService.settings().noblePriceStep(),
                NobleRules.rank(held(playerId)));
    }

    /** A noble's price at rank 0 (the game data). */
    public ResourceAmounts baseCost() {
        return gameData.unit(UnitType.NOBLE).cost();
    }

    /**
     * Takes the lock that keeps two villages of one player from ordering nobles at the same rank.
     * Call it after the village lock (the village lock order stays village → player) and before
     * {@link #held}. The paladin slots take the same lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(long playerId) {
        players.lockById(playerId);
    }
}
