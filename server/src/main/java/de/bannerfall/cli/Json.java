package de.bannerfall.cli;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import tools.jackson.databind.JsonNode;

/** Small readers for the API's JSON. */
final class Json {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private Json() {}

    /** The field as text; null when it is missing or null. */
    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    /** The field as text; "–" when it is missing or null. */
    static String show(JsonNode node, String field) {
        String text = text(node, field);
        return text == null ? "–" : text;
    }

    /** "for good" for a ban to the year 9999 (M14e), else "until " and the time. */
    static String banEnd(JsonNode node, String field) {
        String text = text(node, field);
        return text != null && Instant.parse(text).isAfter(Instant.parse("9000-01-01T00:00:00Z"))
                ? "for good"
                : "until " + time(node, field);
    }

    /** An ISO time field in the container's time zone (TZ); "–" when null. */
    static String time(JsonNode node, String field) {
        String text = text(node, field);
        return text == null ? "–" : TIME.format(Instant.parse(text));
    }
}
