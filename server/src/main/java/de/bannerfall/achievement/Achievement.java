package de.bannerfall.achievement;

/**
 * The achievements of 02-game-rules §19, in the order the web lists them. The name is the key in
 * the database and the API; the texts are the web's (EN + DE). {@link #LEVELED} ones have three
 * levels with the thresholds of {@code gamedata/achievements.yml}.
 */
public enum Achievement {
    // one-time
    PIONEER(Category.TIME, false),
    FOREMAN(Category.BUILDING, false),
    SCHOLAR(Category.BUILDING, false),
    SIEGECRAFT(Category.ARMY, false),
    NOBLE_BLOOD(Category.ARMY, false),
    FIRST_BLOOD(Category.COMBAT, false),
    BREACH(Category.COMBAT, false),
    // battles (1.4, 02-game-rules §19.1)
    PYRRHIC_VICTORY(Category.COMBAT, false),
    AVENGER(Category.COMBAT, false),
    DEMOLISHER(Category.COMBAT, false),
    LUCKY_STRIKE(Category.COMBAT, false),
    SHIELD(Category.DEFENCE, false),
    SPY_CATCHER(Category.DEFENCE, false),
    LAST_STAND(Category.DEFENCE, false),
    BROTHERS_IN_ARMS(Category.DEFENCE, false),
    CONQUEROR(Category.CONQUEST, false),
    TOPPLER(Category.CONQUEST, false),
    LORD_BREAKER(Category.CONQUEST, false),
    FALLEN(Category.CONQUEST, false),
    PHOENIX(Category.CONQUEST, false),
    FIRST_DEAL(Category.TRADE, false),
    FIRST_WORDS(Category.SOCIAL, false),
    GUILD_FOUNDER(Category.SOCIAL, false),
    GUILD_MEMBER(Category.SOCIAL, false),
    // banners (M32c, 02-game-rules §21.8)
    STANDARD_BEARER(Category.BANNER, false),
    COLOURS_RAISED(Category.BANNER, false),
    COLOURS_LOST(Category.BANNER, false),
    FULL_SET(Category.BANNER, false),
    // with levels
    GROWTH(Category.BUILDING, true),
    LANDLORD(Category.CONQUEST, true),
    STANDING_ARMY(Category.ARMY, true),
    VICTOR(Category.COMBAT, true),
    PLUNDERER(Category.COMBAT, true),
    SLAYER(Category.COMBAT, true),
    BULWARK(Category.DEFENCE, true),
    WARDEN(Category.DEFENCE, true),
    MERCHANT_PRINCE(Category.TRADE, true),
    VETERAN(Category.TIME, true),
    BANNER_COLLECTOR(Category.BANNER, true),
    /** Not the unit of the same name: the achievement for banners taken (02 §21.8). */
    BANNER_THIEF(Category.BANNER, true);

    /** How many levels a levelled achievement has: bronze, silver, gold. */
    public static final int LEVELS = 3;

    /** Only for the badge art and the grouping in the web. */
    public enum Category {
        BUILDING,
        ARMY,
        COMBAT,
        DEFENCE,
        CONQUEST,
        TRADE,
        SOCIAL,
        TIME,
        BANNER
    }

    private final Category category;
    private final boolean leveled;

    Achievement(Category category, boolean leveled) {
        this.category = category;
        this.leveled = leveled;
    }

    public Category category() {
        return category;
    }

    public boolean leveled() {
        return leveled;
    }
}
