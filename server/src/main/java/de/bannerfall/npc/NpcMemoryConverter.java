package de.bannerfall.npc;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link NpcMemory} as the JSON text of {@code npc_brain.memory}, with our own Jackson 3 mapper
 * (same reasons and same leniency as {@code ReportPayloadConverter}: unknown properties are
 * ignored, a missing number reads as 0), so memory written by an older version stays readable.
 */
@Converter
public class NpcMemoryConverter implements AttributeConverter<NpcMemory, String> {

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    @Override
    public String convertToDatabaseColumn(NpcMemory memory) {
        return MAPPER.writeValueAsString(memory);
    }

    @Override
    public NpcMemory convertToEntityAttribute(String json) {
        return MAPPER.readValue(json, NpcMemory.class);
    }
}
