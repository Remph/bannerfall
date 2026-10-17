package de.bannerfall.world;

/**
 * The language of the names the game gives villages (v1.3, 02-game-rules §2): a player's starting
 * village, a conquered barbarian village, an NPC lord's village. A world setting, so every player
 * sees the same name; players can rename their villages as before. Stored as text.
 */
public enum VillageNameLanguage {
    /** "Ben's village". */
    EN("", "'s village"),
    /** "Dorf von Ben". */
    DE("Dorf von ", "");

    /** The length of the village name column. */
    public static final int MAX_NAME_LENGTH = 32;

    private final String prefix;
    private final String suffix;

    VillageNameLanguage(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    /** The name the game gives a village of the player; just his name if that would be too long. */
    public String defaultName(String playerName) {
        String name = prefix + playerName + suffix;
        return name.length() <= MAX_NAME_LENGTH ? name : playerName;
    }

    /** What comes before the player's name in a default name ("Dorf von "), maybe empty. */
    public String prefix() {
        return prefix;
    }

    /** What comes after the player's name in a default name ("'s village"), maybe empty. */
    public String suffix() {
        return suffix;
    }

    /**
     * The SQL {@code like} pattern (escape character {@code \}) of this language's default names:
     * the prefix, at least one character, the suffix.
     */
    public String likePattern() {
        return escapeLike(prefix) + "_%" + escapeLike(suffix);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
