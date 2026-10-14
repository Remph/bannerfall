package de.bannerfall.movement;

import de.bannerfall.rules.BannerEffect;

/**
 * The battle banners of a command's source village, snapshotted when it leaves (02-game-rules
 * §21.2): a banner stolen or swapped while the army walks changes nothing, and the RETURN of the
 * survivors keeps the numbers of its trip. Fractions, 0 = no such banner.
 *
 * @param attack War: attack strength +
 * @param carry Plunder: carry capacity +
 * @param haste Haste: travel time −
 */
public record CommandBanner(double attack, double carry, double haste) {

    /** No banner. */
    public static final CommandBanner NONE = new CommandBanner(0, 0, 0);

    /**
     * What a command of {@code kind} takes along from its source's banner: an attack War, Plunder
     * and Haste, a support only Haste (it neither fights nor carries).
     */
    public static CommandBanner of(MovementKind kind, BannerEffect source) {
        return switch (kind) {
            case ATTACK -> new CommandBanner(source.war(), source.plunder(), source.haste());
            case SUPPORT -> new CommandBanner(0, 0, source.haste());
            case RETURN -> throw new IllegalArgumentException("A RETURN is not a command");
        };
    }
}
