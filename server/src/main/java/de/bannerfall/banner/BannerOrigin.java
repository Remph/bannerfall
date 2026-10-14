package de.bannerfall.banner;

/** How a banner came into the world (02-game-rules §21.9, {@code banner.origin}). */
public enum BannerOrigin {
    /** Dropped after a won battle (M32b). */
    FOUND,
    /** Made from three banners of the tier below (M32c). */
    FUSED,
    /** Handed out by the host with {@code bannerfall-admin banner grant}. */
    GRANTED
}
