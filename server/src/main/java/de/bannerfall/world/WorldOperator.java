package de.bannerfall.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who runs this world, for the privacy page: {@code bannerfall.operator.name} and {@code …contact}
 * (env {@code BANNERFALL_OPERATOR_NAME} and {@code BANNERFALL_OPERATOR_CONTACT}). Every world has
 * its own host, so these are settings, not texts in the code. Blank counts as not set; the page
 * then shows only what the game stores.
 */
@Component
public class WorldOperator {

    private final OperatorDto operator;

    WorldOperator(
            @Value("${bannerfall.operator.name:}") String name,
            @Value("${bannerfall.operator.contact:}") String contact) {
        this.operator = new OperatorDto(orNull(name), orNull(contact));
    }

    public OperatorDto get() {
        return operator;
    }

    private static String orNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
