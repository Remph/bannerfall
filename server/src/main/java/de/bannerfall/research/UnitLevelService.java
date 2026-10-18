package de.bannerfall.research;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.Player;
import de.bannerfall.rules.ResearchRules;
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
    private final GameData gameData;

    UnitLevelService(ResearchOrderRepository orders, WorldService worldService, GameData gameData) {
        this.orders = orders;
        this.worldService = worldService;
        this.gameData = gameData;
    }

    /**
     * The levels of the owner of a village or stack. No owner (barbarians) and NPC lords give
     * {@link UnitLevels#NONE}: everything is level 1; with lord banners on a lord has its
     * Hall-trained paladin level. With unit levels switched off only the levels trained in the
     * Banner Hall count (paladin and banner thief, Hall training 1.4).
     *
     * @param player null for a barbarian village
     */
    public UnitLevels levelsOf(Player player, Instant at) {
        if (player == null) {
            return UnitLevels.NONE;
        }
        if (player.isNpc()) {
            // lords keep no unit levels; with lord banners on they train the paladin in the
            // Banner Hall (1.4, 09-npc-lords §14.6), their thieves stay at level 1
            return worldService.settings().lordBanners()
                    ? levelsOf(player.getId(), at).only(type -> type == UnitType.PALADIN)
                    : UnitLevels.NONE;
        }
        UnitLevels levels = levelsOf(player.getId(), at);
        return worldService.settings().unitLevelsEnabled()
                ? levels
                : levels.only(this::hallTrained);
    }

    /** True for a unit whose levels the Banner Hall trains: they count in every world. */
    public boolean hallTrained(UnitType type) {
        return ResearchRules.building(gameData.unit(type)) == BuildingType.BANNER_HALL;
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
