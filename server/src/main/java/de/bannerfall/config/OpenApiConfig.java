package de.bannerfall.config;

import de.bannerfall.chat.ChatErrorDto;
import de.bannerfall.notify.GameNotification;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Tweaks the generated OpenAPI description that the web client's types come from. */
@Configuration
class OpenApiConfig {

    /**
     * Adds the payloads that only travel over STOMP (04-api <i>WebSocket</i>): no REST endpoint
     * returns them, so springdoc wouldn't describe them, but the web wants their types too. Runs
     * before {@link #allPropertiesRequired}, which then treats them like every other DTO.
     */
    @Bean
    @Order(1)
    OpenApiCustomizer stompPayloads() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters converters = ModelConverters.getInstance(true);
            for (Class<?> type : List.of(GameNotification.class, ChatErrorDto.class)) {
                converters
                        .readAll(new AnnotatedType(type))
                        .forEach(
                                (name, schema) -> {
                                    if (openApi.getComponents().getSchemas() == null
                                            || !openApi.getComponents()
                                                    .getSchemas()
                                                    .containsKey(name)) {
                                        openApi.getComponents().addSchemas(name, schema);
                                    }
                                });
            }
        };
    }

    /**
     * Marks every property of our DTOs as required. Jackson always writes every record component
     * (null included), so a property is never missing. Without this the generated TypeScript types
     * would make every field optional ({@code username?: string}) and the frontend would need
     * checks everywhere. A field that can be null still needs {@code @Schema(nullable = true)}.
     * Spring's ProblemDetail is left alone: its fields really are optional.
     *
     * <p>Two more fixes for the generated types (M8b):
     *
     * <ul>
     *   <li>The subtypes of a polymorphic DTO ({@code ReportPayload}) are {@code allOf: [parent,
     *       {properties}]}: the properties of that inline part are required too.
     *   <li>springdoc writes a nullable property whose type is another DTO as {@code {$ref, type:
     *       null}}, and openapi-typescript drops the {@code null} next to a {@code $ref} (the
     *       "known gap" of M4c). Written as {@code oneOf: [{$ref}, {type: null}]} it arrives as
     *       {@code Dto | null}.
     * </ul>
     */
    @Bean
    @Order(2)
    OpenApiCustomizer allPropertiesRequired() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents()
                    .getSchemas()
                    .forEach(
                            (name, rawSchema) -> {
                                if (name.equals("ProblemDetail")) {
                                    return;
                                }
                                // The map holds raw Schema types; going through Schema<?> keeps
                                // the property names typed as String (no unchecked warning).
                                Schema<?> schema = rawSchema;
                                requireAll(schema);
                                if (schema.getAllOf() != null) {
                                    for (Schema<?> part : schema.getAllOf()) {
                                        requireAll(part);
                                    }
                                }
                            });
        };
    }

    /** All properties required, nullable DTO properties as {@code oneOf [$ref, null]}. */
    private static void requireAll(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        schema.setRequired(new ArrayList<>(properties.keySet()));
        properties.replaceAll((name, property) -> nullableRefAsOneOf(property));
    }

    private static Schema<?> nullableRefAsOneOf(Schema<?> property) {
        boolean nullable =
                Boolean.TRUE.equals(property.getNullable())
                        || (property.getTypes() != null && property.getTypes().contains("null"));
        boolean polymorphic = property.getOneOf() != null && !property.getOneOf().isEmpty();
        if ((property.get$ref() == null && !polymorphic) || !nullable) {
            return property;
        }
        Schema<?> nullType = new JsonSchema();
        nullType.addType("null");
        List<Schema> branches = new ArrayList<>();
        if (polymorphic) {
            // a sealed interface with subtypes (ReportPayload): springdoc writes
            // {type: "null", oneOf: [...]}, which would read as "null and one of ..."
            branches.addAll(property.getOneOf());
        } else {
            branches.add(new JsonSchema().$ref(property.get$ref()));
        }
        branches.add(nullType);
        Schema<?> oneOf = new JsonSchema();
        oneOf.setOneOf(branches);
        oneOf.setDescription(property.getDescription());
        return oneOf;
    }
}
