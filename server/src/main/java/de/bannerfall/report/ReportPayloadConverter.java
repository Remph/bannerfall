package de.bannerfall.report;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a {@link ReportPayload} into the JSON text of the {@code jsonb} column and back, with our
 * own Jackson 3 mapper. Hibernate could map the record by itself, but it picks Jackson 2 or 3
 * depending on what happens to be on the classpath (springdoc brings Jackson 2); this way the
 * stored format can't change behind our back.
 *
 * <p>Old reports stay readable when the records change: unknown properties are ignored (a removed
 * component), and a missing number reads as 0 (a new {@code int} component; Jackson 3 refuses that
 * by default).
 *
 * <p>A stored payload that can't be read at all (an unknown {@code type}, a record that changed
 * incompatibly) reads as {@code null} instead of failing the load: otherwise one such row would
 * break the whole report list with a 500. The list shows a placeholder line for it, opening it says
 * the report can't be shown, and everything else can still delete it.
 */
@Converter
public class ReportPayloadConverter implements AttributeConverter<ReportPayload, String> {

    private static final Logger log = LoggerFactory.getLogger(ReportPayloadConverter.class);

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    @Override
    public String convertToDatabaseColumn(ReportPayload payload) {
        return MAPPER.writeValueAsString(payload);
    }

    @Override
    public ReportPayload convertToEntityAttribute(String json) {
        try {
            return MAPPER.readValue(json, ReportPayload.class);
        } catch (JacksonException e) {
            log.warn("A stored report payload can't be read: {}", e.getOriginalMessage());
            return null;
        }
    }
}
