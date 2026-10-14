package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bannerfall.gamedata.BuildingType;
import org.junit.jupiter.api.Test;

class BuildingLevelsTest {

    @Test
    void everyTypeHasItsOwnField() {
        var levels = new BuildingLevels();
        BuildingType[] types = BuildingType.values();
        for (int i = 0; i < types.length; i++) {
            levels.set(types[i], i + 1);
        }

        for (int i = 0; i < types.length; i++) {
            assertThat(levels.get(types[i])).as(types[i].name()).isEqualTo(i + 1);
        }
    }

    @Test
    void startsAtZeroAndAddRaisesTheLevel() {
        var levels = new BuildingLevels();
        assertThat(levels.get(BuildingType.WALL)).isZero();

        levels.add(BuildingType.WALL, 2);

        assertThat(levels.get(BuildingType.WALL)).isEqualTo(2);
    }

    @Test
    void toMapHasAllTypesInCanonicalOrder() {
        var levels = new BuildingLevels();
        levels.set(BuildingType.FARM, 3);

        var map = levels.toMap();

        assertThat(map.keySet()).containsExactly(BuildingType.values());
        assertThat(map.get(BuildingType.FARM)).isEqualTo(3);
        assertThat(map.get(BuildingType.WALL)).isZero();
    }

    @Test
    void negativeLevelIsRejected() {
        var levels = new BuildingLevels();
        assertThatThrownBy(() -> levels.set(BuildingType.WALL, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
