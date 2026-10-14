package de.bannerfall.guild;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.UnsafeChars;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * The text rules of a guild (02-game-rules §16), pure so the tests need no Spring. The web app
 * checks the same rules before it sends a request, the server decides.
 */
final class GuildText {

    static final int NAME_MIN = 3;
    static final int NAME_MAX = 30;
    static final int TAG_MIN = 2;
    static final int TAG_MAX = 5;
    static final int DESCRIPTION_MAX = 500;

    /**
     * Letters and digits of any language, single spaces and {@code ' . _ -} in between; starts and
     * ends with a letter or digit (so a name can't look empty or be padded).
     */
    private static final Pattern NAME =
            Pattern.compile("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}'._-]| (?! ))*[\\p{L}\\p{N}]");

    /** Only ASCII, so a tag looks the same on every phone and in every font. */
    private static final Pattern TAG =
            Pattern.compile("[A-Za-z0-9]{" + TAG_MIN + "," + TAG_MAX + "}");

    /** Control characters except the line break (pasted text may have them). */
    private static final Pattern CONTROL = Pattern.compile("[" + UnsafeChars.CLASS + "&&[^\\n]]");

    private GuildText() {}

    /** The trimmed name. @throws ApiException 400 {@code VALIDATION} */
    static String name(String raw) {
        String name = raw == null ? "" : raw.strip();
        int length = name.codePointCount(0, name.length());
        if (length < NAME_MIN || length > NAME_MAX || !NAME.matcher(name).matches()) {
            throw invalid(
                    "A guild name has "
                            + NAME_MIN
                            + " to "
                            + NAME_MAX
                            + " characters: letters, digits, single spaces and ' . _ -");
        }
        return name;
    }

    /** The trimmed tag. @throws ApiException 400 {@code VALIDATION} */
    static String tag(String raw) {
        String tag = raw == null ? "" : raw.strip();
        if (!TAG.matcher(tag).matches()) {
            throw invalid("A guild tag has " + TAG_MIN + " to " + TAG_MAX + " letters or digits");
        }
        return tag;
    }

    /** The trimmed description, empty allowed. @throws ApiException 400 {@code VALIDATION} */
    static String description(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.codePointCount(0, text.length()) > DESCRIPTION_MAX) {
            throw invalid("A description has at most " + DESCRIPTION_MAX + " characters");
        }
        if (CONTROL.matcher(text).find()) {
            throw invalid("A description must not contain control characters");
        }
        return text;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, message);
    }
}
