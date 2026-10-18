package de.bannerfall.farm;

import de.bannerfall.gamedata.UnitType;
import java.util.Map;

/**
 * A farm template (04-api <i>Farm helper</i>, 1.4).
 *
 * @param units only the types that are sent
 */
public record FarmTemplateDto(FarmTemplateLetter letter, Map<UnitType, Integer> units) {

    static FarmTemplateDto of(FarmTemplate template) {
        return new FarmTemplateDto(template.getLetter(), template.getUnits().toMap());
    }
}
