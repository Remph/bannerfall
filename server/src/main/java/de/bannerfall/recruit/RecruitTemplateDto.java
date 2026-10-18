package de.bannerfall.recruit;

import de.bannerfall.gamedata.UnitType;
import java.util.Map;

/**
 * A recruit template (04-api <i>Recruitment</i>, 1.4).
 *
 * @param units the goal per unit type, only the types above 0
 */
public record RecruitTemplateDto(
        RecruitTemplateLetter letter, String name, Map<UnitType, Integer> units) {

    static RecruitTemplateDto of(RecruitTemplate template) {
        return new RecruitTemplateDto(
                template.getLetter(), template.getName(), template.getUnits().toMap());
    }
}
