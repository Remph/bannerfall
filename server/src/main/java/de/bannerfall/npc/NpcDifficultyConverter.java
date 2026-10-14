package de.bannerfall.npc;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The CUSTOM difficulty's values as the JSON of {@code world_settings.npc_custom} (09 §8). */
@Converter
public class NpcDifficultyConverter
        implements AttributeConverter<NpcBalanceSettings.Difficulty, String> {

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build();

    @Override
    public String convertToDatabaseColumn(NpcBalanceSettings.Difficulty values) {
        return values == null ? null : MAPPER.writeValueAsString(values);
    }

    @Override
    public NpcBalanceSettings.Difficulty convertToEntityAttribute(String json) {
        return json == null ? null : MAPPER.readValue(json, NpcBalanceSettings.Difficulty.class);
    }
}
