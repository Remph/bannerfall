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
 * @param guild what the owner's guild banner adds in this village (1.7, §16.4); {@link
 *     GuildEffect#NONE} without one. It is separate from the village's own banner: the two add up
 *     where they touch the same number
 */
public record BannerEffect(
        BannerType type, int tier, int effectiveTier, double bonus, GuildEffect guild) {

    /** No effect. */
    public static final BannerEffect NONE = new BannerEffect(null, 0, 0, 0);

    /** A village banner without a guild banner behind it. */
    public BannerEffect(BannerType type, int tier, int effectiveTier, double bonus) {
        this(type, tier, effectiveTier, bonus, GuildEffect.NONE);
    }

    /** The same banner with the owner's guild banner added. */
    public BannerEffect withGuild(GuildEffect guild) {
        return new BannerEffect(type, tier, effectiveTier, bonus, guild);
    }

    /** Production of wood, clay and iron +: the Harvest banner and the guild banner's level 1. */
    public double production() {
        return harvest() + guild.production();
    }

    /** Defence of the village +: the Bulwark banner and the guild banner's level 3. */
    public double defence() {
        return bulwark() + guild.defence();
    }

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
