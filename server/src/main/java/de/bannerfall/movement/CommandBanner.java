package de.bannerfall.movement;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.rules.BannerEffect;

/**
 * The battle banners of a command's source village, snapshotted when it leaves (02-game-rules
 * §21.2): a banner stolen or swapped while the army walks changes nothing, and the RETURN of the
 * survivors keeps the numbers of its trip. Fractions, 0 = no such banner.
 *
 * @param attack War: attack strength +
 * @param carry Plunder: carry capacity +
 * @param haste Haste: travel time −
 * @param standard the paladin standard an attack with a paladin takes along (1.4, §21.18); null
 *     without
 */
public record CommandBanner(double attack, double carry, double haste, Standard standard) {

    /** No banner. */
    public static final CommandBanner NONE = new CommandBanner(0, 0, 0);

    /** Without a standard. */
    public CommandBanner(double attack, double carry, double haste) {
        this(attack, carry, haste, null);
    }

    /**
     * A paladin standard as it left with an attack (§21.18).
     *
     * @param bannerId the standard itself, which the defenders' thieves may take if the attack is
     *     lost
     * @param type its kind
     * @param tier its own tier
     * @param effectiveTier the tier it works at: at most the owner's paladin level when it left
     */
    public record Standard(long bannerId, BannerType type, int tier, int effectiveTier) {

        public Standard {
            if (!type.isStandard()) {
                throw new IllegalArgumentException(type + " is no paladin standard");
            }
        }
    }

    /**
     * What a command of {@code kind} takes along from its source's banner: an attack War, Plunder
     * and Haste, a support only Haste (it neither fights nor carries).
     */
    public static CommandBanner of(MovementKind kind, BannerEffect source) {
        return of(kind, source, null);
    }

    /**
     * Like {@link #of(MovementKind, BannerEffect)}, and an attack also takes the source's paladin
     * standard along (it only goes with armies that have a paladin, see {@link #forUnits}).
     *
     * @param standard the standard of the source village; null without one
     */
    public static CommandBanner of(MovementKind kind, BannerEffect source, Standard standard) {
        return switch (kind) {
            case ATTACK ->
                    new CommandBanner(source.war(), source.plunder(), source.haste(), standard);
            case SUPPORT -> new CommandBanner(0, 0, source.haste());
            case RETURN -> throw new IllegalArgumentException("A RETURN is not a command");
        };
    }

    /** The same banners with {@code extra} more travel time bonus (the guild banner, §16.4). */
    public CommandBanner plusHaste(double extra) {
        return new CommandBanner(attack, carry, haste + extra, standard);
    }

    /** The same banners for an army with or without a paladin: only paladins carry a standard. */
    public CommandBanner forUnits(boolean withPaladin) {
        return withPaladin || standard == null
                ? this
                : new CommandBanner(attack, carry, haste, null);
    }
}
