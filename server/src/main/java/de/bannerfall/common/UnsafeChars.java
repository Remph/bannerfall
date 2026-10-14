package de.bannerfall.common;

/** Characters that player text (chat, messages, guild and village names) must not contain. */
public final class UnsafeChars {

    /**
     * A regex character class body: the control characters (C0, DEL and C1), the line and paragraph
     * separators, and the bidi embeddings, overrides and isolates (U+202A–202E, U+2066–2069). The
     * last ones make text show reversed for every reader. {@code \p{Cntrl}} alone covers only C0
     * and DEL (code review 1.2).
     */
    public static final String CLASS = "\\p{Cc}\\u2028\\u2029\\u202A-\\u202E\\u2066-\\u2069";

    private UnsafeChars() {}
}
