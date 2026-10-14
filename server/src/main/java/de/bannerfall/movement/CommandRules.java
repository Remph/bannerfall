package de.bannerfall.movement;

import de.bannerfall.common.ErrorCode;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.unit.UnitCounts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The checks of a command (02-game-rules §6). Pure functions. */
final class CommandRules {

    private CommandRules() {}

    /**
     * Every rule the command breaks, in this order (send answers with the first one): {@code
     * EMPTY_COMMAND}, {@code NOT_ENOUGH_UNITS}, {@code SAME_VILLAGE} or {@code OWN_VILLAGE}, {@code
     * NOBLE_ONLY_IN_ATTACK} or {@code NOBLE_TOO_FAR}, {@code PROTECTED_TARGET}.
     *
     * @param kind ATTACK or SUPPORT
     * @param home the units at home, caught up
     * @param ownTarget the target belongs to the sender
     * @param targetProtectedUntil the target player's beginner protection; null for barbarians
     * @param arriveAt null if the command has no units
     */
    static List<ErrorCode> check(
            MovementKind kind,
            UnitCounts units,
            UnitCounts home,
            boolean sameVillage,
            boolean ownTarget,
            double distance,
            Instant targetProtectedUntil,
            Instant arriveAt,
            int nobleMaxDistance) {
        boolean attack = kind == MovementKind.ATTACK;
        List<ErrorCode> errors = new ArrayList<>();
        if (units.isEmpty()) {
            errors.add(ErrorCode.EMPTY_COMMAND);
        }
        if (!units.fitsInto(home)) {
            errors.add(ErrorCode.NOT_ENOUGH_UNITS);
        }
        if (sameVillage) {
            errors.add(ErrorCode.SAME_VILLAGE);
        } else if (attack && ownTarget) {
            errors.add(ErrorCode.OWN_VILLAGE);
        }
        if (units.get(UnitType.NOBLE) > 0) {
            if (!attack) {
                errors.add(ErrorCode.NOBLE_ONLY_IN_ATTACK);
            } else if (distance > nobleMaxDistance) {
                errors.add(ErrorCode.NOBLE_TOO_FAR);
            }
        }
        if (attack
                && !ownTarget
                && targetProtectedUntil != null
                && arriveAt != null
                && targetProtectedUntil.isAfter(arriveAt)) {
            errors.add(ErrorCode.PROTECTED_TARGET);
        }
        return errors;
    }

    /**
     * The catapults' target (02-game-rules §6): only an attack with catapults has one, {@code
     * HEADQUARTERS} unless the player chose another building.
     */
    static BuildingType catapultTarget(
            MovementKind kind, UnitCounts units, BuildingType requested) {
        if (kind != MovementKind.ATTACK || units.get(UnitType.CATAPULT) == 0) {
            return null;
        }
        return requested != null ? requested : BuildingType.HEADQUARTERS;
    }
}
