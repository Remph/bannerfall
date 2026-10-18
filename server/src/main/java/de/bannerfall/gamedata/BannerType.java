package de.bannerfall.gamedata;

import java.util.Arrays;
import java.util.List;

/**
 * The nine banners of 02-game-rules §21.2, in the order of the table there (UI lists, DB column
 * {@code banner.type}), and the seven paladin standards of §21.18 (1.4). Never reorder; new types
 * go to the end.
 *
 * <p>A standard's name ends in {@code _STANDARD}: the database keeps one banner and one standard
 * per village by that suffix ({@code V38__paladin_standards.sql}).
 */
public enum BannerType {
    HARVEST,
    BUILDERS,
    DRILL,
    WAR,
    BULWARK,
    HASTE,
    TREASURY,
    PLUNDER,
    OATH,
    /** Spearmen defence (both) + (1.4, §21.18). */
    SPEAR_STANDARD,
    /** Swordsmen defence (both) +. */
    SWORD_STANDARD,
    /** Axemen attack +. */
    AXE_STANDARD,
    /** Light cavalry attack +. */
    LANCE_STANDARD,
    /** Heavy cavalry attack and defence +. */
    KNIGHT_STANDARD,
    /** Wall damage of rams +. */
    RAM_STANDARD,
    /** Building damage of catapults +. */
    SIEGE_STANDARD;

    private static final List<BannerType> VILLAGE_BANNERS =
            Arrays.stream(values()).filter(type -> !type.isStandard()).toList();
    private static final List<BannerType> STANDARDS =
            Arrays.stream(values()).filter(BannerType::isStandard).toList();

    /** A paladin standard (§21.18) rather than a village banner. */
    public boolean isStandard() {
        return name().endsWith("_STANDARD");
    }

    /** The nine village banners, in table order. */
    public static List<BannerType> villageBanners() {
        return VILLAGE_BANNERS;
    }

    /** The seven paladin standards, in table order. */
    public static List<BannerType> standards() {
        return STANDARDS;
    }
}
