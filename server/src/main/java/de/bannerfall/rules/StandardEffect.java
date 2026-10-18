package de.bannerfall.rules;

import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.UnitType;

/**
 * What a paladin standard does for one army or one defending stack (1.4, 02-game-rules §21.18): its
 * kind, its own tier, the tier it works at ({@link BannerRules#standardTier}) and the effect of
 * that tier as a fraction. It covers only the units of the army or stack that carries it. {@link
 * #NONE} without a standard, without a paladin in the army or stack, or while it sleeps. Pure
 * value, {@link Combat} takes its numbers.
 *
 * @param type the standard's kind; null for {@link #NONE}
 * @param tier its own tier (1–3)
 * @param effectiveTier the tier it works at; 0 = it does nothing
 * @param bonus the effect of {@code effectiveTier}, as a fraction; 0 when it does nothing
 */
public record StandardEffect(BannerType type, int tier, int effectiveTier, double bonus) {

    /** No standard. */
    public static final StandardEffect NONE = new StandardEffect(null, 0, 0, 0);

    public StandardEffect {
        if (type != null && !type.isStandard()) {
            throw new IllegalArgumentException(type + " is no paladin standard");
        }
        if (!(bonus >= 0 && bonus < 1)) {
            throw new IllegalArgumentException("bonus must be in [0, 1): " + bonus);
        }
    }

    /** Whether it does anything. */
    public boolean works() {
        return bonus > 0;
    }

    /** The attack bonus of one attacking unit type (Axe, Lance, Knight). */
    public double attack(UnitType unit) {
        return switch (unit) {
            case AXEMAN -> of(BannerType.AXE_STANDARD);
            case LIGHT_CAVALRY -> of(BannerType.LANCE_STANDARD);
            case HEAVY_CAVALRY -> of(BannerType.KNIGHT_STANDARD);
            default -> 0;
        };
    }

    /** The defence bonus (against infantry and cavalry) of one defending unit type. */
    public double defence(UnitType unit) {
        return switch (unit) {
            case SPEARMAN -> of(BannerType.SPEAR_STANDARD);
            case SWORDSMAN -> of(BannerType.SWORD_STANDARD);
            case HEAVY_CAVALRY -> of(BannerType.KNIGHT_STANDARD);
            default -> 0;
        };
    }

    /** The wall damage bonus of the army's rams (Ram Standard). */
    public double rams() {
        return of(BannerType.RAM_STANDARD);
    }

    /** The building damage bonus of the army's catapults (Siege Standard). */
    public double catapults() {
        return of(BannerType.SIEGE_STANDARD);
    }

    private double of(BannerType wanted) {
        return type == wanted ? bonus : 0;
    }
}
