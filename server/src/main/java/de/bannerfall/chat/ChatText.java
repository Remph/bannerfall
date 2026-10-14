package de.bannerfall.chat;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.UnsafeChars;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** The text rules of a chat message, shared by the chats and the private messages (02 §13, §17). */
public final class ChatText {

    public static final int MAX_LENGTH = 500;

    /** Control characters except line break and tab (pasted text may have them). */
    private static final Pattern CONTROL =
            Pattern.compile("[" + UnsafeChars.CLASS + "&&[^\\n\\t]]");

    private ChatText() {}

    /**
     * Trimmed, 1 to {@value #MAX_LENGTH} characters.
     *
     * @throws ApiException 400 {@code VALIDATION} otherwise
     */
    public static String checked(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "Empty message");
        }
        // Line breaks and tabs are fine (the chat shows them), other control characters never come
        // from the web app (M14e).
        if (CONTROL.matcher(trimmed).find()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "Messages must not contain control characters");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION,
                    "Messages have at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
