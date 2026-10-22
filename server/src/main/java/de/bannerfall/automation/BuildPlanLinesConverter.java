package de.bannerfall.automation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The lines of a build plan as the JSON text of {@code build_plan.lines}, with our own Jackson 3
 * mapper (lenient like {@code NpcMemoryConverter}: unknown properties are ignored, a missing number
 * reads as 0, a missing {@code mines} as false): a building line reads {@code {"building": "FARM",
 * "level": 6, "recruit": null, "mines": false}}, the recruit line {@code {"building": null,
 * "level": 0, "recruit": "B", "mines": false}}, the mines line {@code {"building": null, "level":
 * 5, "recruit": null, "mines": true}}.
 */
@Converter
class BuildPlanLinesConverter implements AttributeConverter<List<BuildPlanLine>, String> {

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    private static final TypeReference<List<BuildPlanLine>> LINES = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<BuildPlanLine> lines) {
        return MAPPER.writeValueAsString(lines);
    }

    @Override
    public List<BuildPlanLine> convertToEntityAttribute(String json) {
        return List.copyOf(MAPPER.readValue(json, LINES));
    }
}
