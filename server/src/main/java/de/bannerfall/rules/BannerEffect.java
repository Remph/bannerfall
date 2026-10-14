package de.bannerfall.rules;

import de.bannerfall.gamedata.BannerType;

/**
 * What the banner flying in a village does right now (02-game-rules §21.1, §21.2): its type, the
 * tier it works at ({@code min(tier, hallLevel)}) and the effect of that tier as a fraction. {@link
 * #NONE} for a village without a banner, with a dormant one (no hall), or in a world with banners
 * switched off. Pure value, the economy rules take its numbers.
 *
 * @param type the banner's type; null for {@link #NONE}
 * @param tier the banner's own tier (1–3)
 * @param effectiveTier the tier it works at; 0 = dormant
 * @param bonus the effect of {@code effectiveTier}, as a fraction; 0 when dormant
 */
public record BannerEffect(BannerType type, int tier, int effectiveTier, double bonus) {

    /** No effect. */
    public static final BannerEffect NONE = new BannerEffect(null, 0, 0, 0);

    /** The fraction this banner adds if it is of {@code wanted} type, otherwise 0. */
    public double of(BannerType wanted) {
        return type == wanted ? bonus : 0;
    }

    /** Production of wood, clay and iron + (Harvest). */
    public double harvest() {
        return of(BannerType.HARVEST);
    }

    /** Build time of new build orders − (Builders). */
    public double builders() {
        return of(BannerType.BUILDERS);
    }

    /** Recruit time of new recruit orders − (Drill). */
    public double drill() {
        return of(BannerType.DRILL);
    }

    /** Warehouse and hiding place capacity + (Treasury). */
    public double treasury() {
        return of(BannerType.TREASURY);
    }

    /** Attack strength of attacks sent from the village + (War). */
    public double war() {
        return of(BannerType.WAR);
    }

    /** Defence of the village + (Bulwark). */
    public double bulwark() {
        return of(BannerType.BULWARK);
    }

    /** Travel time of attacks and supports sent from the village − (Haste). */
    public double haste() {
        return of(BannerType.HASTE);
    }

    /** Carry capacity of attacks sent from the village + (Plunder). */
    public double plunder() {
        return of(BannerType.PLUNDER);
    }

    /** Loyalty each enemy noble takes off the village − (Oath). */
    public double oath() {
        return of(BannerType.OATH);
    }
}
