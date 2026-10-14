package de.bannerfall.research;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.rules.UnitLevels;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The unit levels of a player at a point in time (M30, 02-game-rules §5.4), for everything that
 * fights: a battle reads them at its arrival time, so a research that finishes before the arrival
 * counts and one that finishes after it doesn't. Read only; the villages are not locked for this.
 */
@Service
public class UnitLevelService {

    private final ResearchOrderRepository orders;
    private final WorldService worldService;

    UnitLevelService(ResearchOrderRepository orders, WorldService worldService) {
        this.orders = orders;
        this.worldService = worldService;
    }

    /**
     * The levels of the owner of a village or stack. No owner (barbarians), NPC lords and worlds
     * with unit levels switched off give {@link UnitLevels#NONE}: everything is level 1.
     *
     * @param player null for a barbarian village
     */
    public UnitLevels levelsOf(Player player, Instant at) {
        if (player == null || player.isNpc() || !worldService.settings().unitLevelsEnabled()) {
            return UnitLevels.NONE;
        }
        return levelsOf(player.getId(), at);
    }

    /** The levels a player has at {@code at}, ignoring the world setting (see the other method). */
    UnitLevels levelsOf(long playerId, Instant at) {
        Map<UnitType, Integer> levels = new EnumMap<>(UnitType.class);
        for (ResearchOrder order : orders.findByPlayerIdAndEndAtLessThanEqual(playerId, at)) {
            levels.merge(order.getUnit(), order.getLevel(), Math::max);
        }
        return new UnitLevels(levels);
    }
}
