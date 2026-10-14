package de.bannerfall.rules;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.world.WorldSettings;
import java.util.Map;

/** Distance, army speed and travel time (02-game-rules §2, §6). Pure functions. */
public final class Travel {

    private Travel() {}

    /** Distance in fields between two villages. */
    public static double distance(Coordinates from, Coordinates to) {
        return from.distanceTo(to);
    }

    /**
     * Army speed in minutes per field: the slowest (largest value) of the unit types that are
     * actually in the army (count &gt; 0).
     *
     * @throws IllegalArgumentException if the army has no units
     */
    public static double armyMinutesPerField(GameData data, Map<UnitType, Integer> army) {
        double slowest = 0;
        for (var entry : army.entrySet()) {
            if (entry.getValue() > 0) {
                slowest = Math.max(slowest, data.unit(entry.getKey()).minutesPerField());
            }
        }
        if (slowest == 0) {
            throw new IllegalArgumentException("An army needs at least one unit");
        }
        return slowest;
    }

    /**
     * Army speed of a <b>support</b> (also its withdrawal and send-back, 02-game-rules §5.5): with
     * a paladin in the army every unit walks at his speed, the slower ones faster than alone, the
     * quicker ones (cavalry, scouts) at his pace. Without one it is {@link #armyMinutesPerField}.
     */
    public static double supportMinutesPerField(GameData data, Map<UnitType, Integer> army) {
        if (army.getOrDefault(UnitType.PALADIN, 0) > 0) {
            return data.unit(UnitType.PALADIN).minutesPerField();
        }
        return armyMinutesPerField(data, army);
    }

    /**
     * Travel time in seconds of a support (or its return) from one village to another, see {@link
     * #supportMinutesPerField}.
     */
    public static long supportTravelSeconds(
            GameData data,
            WorldSettings world,
            Coordinates from,
            Coordinates to,
            Map<UnitType, Integer> army) {
        return travelSeconds(distance(from, to), supportMinutesPerField(data, army), world);
    }

    /**
     * Travel time in seconds of an army from one village to another: the two steps above in one.
     *
     * @throws IllegalArgumentException if the army has no units
     */
    public static long travelSeconds(
            GameData data,
            WorldSettings world,
            Coordinates from,
            Coordinates to,
            Map<UnitType, Integer> army) {
        return travelSeconds(distance(from, to), armyMinutesPerField(data, army), world);
    }

    /**
     * A travel time shortened by the Haste banner of the command's source village (02-game-rules
     * §21.2): {@code max(1, round(seconds × (1 − haste)))} on top of the travel time.
     *
     * @param haste the Haste banner as a fraction; 0 without
     */
    public static long withHaste(long seconds, double haste) {
        return BannerRules.seconds(seconds, haste);
    }

    /**
     * Travel time in seconds: {@code max(1, round(distance × minutesPerField × 60 / (speed ×
     * unitSpeed)))}.
     */
    public static long travelSeconds(double distance, double minutesPerField, WorldSettings world) {
        double seconds = distance * minutesPerField * 60 / (world.speed() * world.unitSpeed());
        return Math.max(1, Math.round(seconds));
    }
}
