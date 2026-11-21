package de.bannerfall.npc;

import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.Movement;
import de.bannerfall.movement.MovementKind;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.rules.Merchants;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * What the order sheet shows of an ally (2.1 S3, 12-solo-and-allies §8): how far its nearest
 * village is from the target and how long its troops and merchants would walk. Only a guide: the
 * lord sends from the village it chooses in its round.
 */
@Component
public class NpcOrderSheet {

    private final VillageRepository villages;
    private final UnitService unitService;
    private final NpcBrainRepository brains;
    private final NpcTemplates templates;
    private final GameData gameData;
    private final MovementRepository movements;

    NpcOrderSheet(
            VillageRepository villages,
            UnitService unitService,
            NpcBrainRepository brains,
            NpcTemplates templates,
            GameData gameData,
            MovementRepository movements) {
        this.villages = villages;
        this.unitService = unitService;
        this.brains = brains;
        this.templates = templates;
        this.gameData = gameData;
        this.movements = movements;
    }

    /**
     * The human's attack on the village that lands first after {@code now} (scouts-only attacks
     * don't count): what "land together" times the allies' march to; null without one.
     */
    public Instant ownAttackArrival(long playerId, long villageId, Instant now) {
        return movements.findByTargetVillageIdOrderByArriveAtAscIdAsc(villageId).stream()
                .filter(m -> m.getKind() == MovementKind.ATTACK)
                .filter(m -> m.getOwnerPlayerId() == playerId)
                .filter(m -> m.getArriveAt().isAfter(now))
                .filter(NpcOrderSheet::moreThanScouts)
                .map(Movement::getArriveAt)
                .findFirst()
                .orElse(null);
    }

    private static boolean moreThanScouts(Movement movement) {
        return movement.getUnits().toMap().entrySet().stream()
                .anyMatch(e -> e.getKey() != UnitType.SCOUT && e.getValue() > 0);
    }

    /**
     * An ally's reach to the target, from its nearest village.
     *
     * @param fields the distance in fields
     * @param attackSeconds the travel time of the slowest attack unit at home there; null if none
     * @param supportSeconds the same for its support units; null if none
     * @param sendSeconds the merchants' travel time
     */
    public record Reach(double fields, Long attackSeconds, Long supportSeconds, long sendSeconds) {}

    /** The lord's reach to {@code target}; null if it owns no other village. */
    public Reach reach(long lordId, Village target, WorldSettings world) {
        Village nearest =
                villages.findByPlayerIdOrderByIdAsc(lordId).stream()
                        .filter(v -> !v.getId().equals(target.getId()))
                        .min(
                                Comparator.comparingDouble(
                                        (Village v) ->
                                                v.coordinates().distanceTo(target.coordinates())))
                        .orElse(null);
        NpcBrain brain = brains.findById(lordId).orElse(null);
        if (nearest == null || brain == null) {
            return null;
        }
        NpcTemplates.Template template = templates.of(brain);
        Map<UnitType, Integer> home = unitService.homeUnits(nearest.getId()).toMap();
        double fields = nearest.coordinates().distanceTo(target.coordinates());
        return new Reach(
                fields,
                travel(fields, NpcTargeting.only(home, template.attackUnits()), world),
                travel(fields, NpcTargeting.only(home, template.supportUnits()), world),
                Merchants.travelSeconds(fields, world));
    }

    private Long travel(double fields, Map<UnitType, Integer> army, WorldSettings world) {
        if (army.isEmpty()) {
            return null;
        }
        return Travel.travelSeconds(fields, Travel.armyMinutesPerField(gameData, army), world);
    }

    /** The kinds of order a target takes, seen from its owner's relation to the human. */
    public static List<NpcOrderKind> kinds(Long owner, long playerId, boolean ownerInGuild) {
        if (owner != null && owner == playerId) {
            return List.of(NpcOrderKind.SUPPORT, NpcOrderKind.SEND);
        }
        if (owner != null && ownerInGuild) {
            return List.of(NpcOrderKind.SUPPORT);
        }
        return List.of(NpcOrderKind.ATTACK, NpcOrderKind.HOLD);
    }
}
