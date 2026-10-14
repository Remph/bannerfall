package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Research time and requirements (02-game-rules §5.4). */
class ResearchRulesTest {

    private final GameData data = TestGameData.load();

    @Test
    void theTimeShrinksWithTheSmithyLevelAndTheSpeed() {
        var level2 = data.unit(UnitType.AXEMAN).levelDef(2); // 12 h at Smithy 1

        assertThat(ResearchRules.seconds(level2, 1, 1)).isEqualTo(43_200);
        assertThat(ResearchRules.seconds(level2, 1, 6)).isEqualTo(7_200);
        // 43200 × 0.937^9 = 24 051.4
        assertThat(ResearchRules.seconds(level2, 10, 1)).isEqualTo(24_051);
        assertThat(ResearchRules.seconds(level2, 20, 1)).isLessThan(14_000);
    }

    @Test
    void theTimeIsAtLeastOneSecond() {
        assertThat(ResearchRules.seconds(data.unit(UnitType.AXEMAN).levelDef(2), 20, 100))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void theSmithyLevelMustBeAtLeastOne() {
        assertThatThrownBy(
                        () -> ResearchRules.seconds(data.unit(UnitType.AXEMAN).levelDef(2), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aResearchNeedsTheUnitsOwnRequirementsAndTheLevels() {
        assertThat(ResearchRules.requirements(data.unit(UnitType.SPEARMAN), 2))
                .isEqualTo(Map.of(BuildingType.BARRACKS, 1, BuildingType.SMITHY, 10));
        // the heavy cavalry needs Smithy 15 to be recruited anyway: the higher level counts
        assertThat(ResearchRules.requirements(data.unit(UnitType.HEAVY_CAVALRY), 2))
                .isEqualTo(Map.of(BuildingType.STABLE, 10, BuildingType.SMITHY, 15));
        assertThat(ResearchRules.requirements(data.unit(UnitType.HEAVY_CAVALRY), 3))
                .isEqualTo(Map.of(BuildingType.STABLE, 10, BuildingType.SMITHY, 15));
    }

    @Test
    void thereIsNoResearchBeyondTheLevels() {
        assertThatThrownBy(() -> data.unit(UnitType.SPEARMAN).levelDef(4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> data.unit(UnitType.SPEARMAN).levelDef(1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> data.unit(UnitType.NOBLE).levelDef(2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
