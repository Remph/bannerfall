package de.bannerfall.npc;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The court a lord guild stands for in a solo world (2.1 S2b, 12-solo-and-allies §6): one court per
 * entry of the lord guild name list (09 §13.1), "Ordo Corvi" is the Raven Court ({@code RAVEN}),
 * "Ordo Corvi II" the Raven Court II. The client writes the court's name in the reader's language
 * from the key ("The Raven Court" / "Der Rabenhof").
 */
public final class CourtNames {

    /** The court key of each lord guild name (the list of {@link NpcGuildForming#NAMES}). */
    static final Map<String, String> KEYS =
            Map.of(
                    "Ordo Corvi", "RAVEN",
                    "Ordo Leonis", "LION",
                    "Ordo Aquilae", "EAGLE",
                    "Ordo Ursi", "BEAR",
                    "Ordo Lupi", "WOLF",
                    "Ordo Draconis", "DRAGON",
                    "Ordo Cervi", "STAG",
                    "Ordo Falconis", "FALCON",
                    "Ordo Tauri", "BULL",
                    "Ordo Vulpis", "FOX");

    /** A name of the list, with the numeral of a later round ("Ordo Corvi II"). */
    private static final Pattern NAME = Pattern.compile("(Ordo \\p{L}+)(?: ([IVXLCDM]+))?");

    private CourtNames() {}

    /**
     * A court's key and its numeral.
     *
     * @param numeral "II", "III", … for a name of a later round; null for the first
     */
    public record Court(String key, String numeral) {}

    /**
     * The court of a lord guild named {@code guildName}; empty for a name that is not on the list
     * (a guild the host named, 09 §13.7): the client then says "the court of" and the guild's name.
     */
    public static Optional<Court> of(String guildName) {
        if (guildName == null) {
            return Optional.empty();
        }
        Matcher matcher = NAME.matcher(guildName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String key = KEYS.get(matcher.group(1));
        return key == null ? Optional.empty() : Optional.of(new Court(key, matcher.group(2)));
    }
}
