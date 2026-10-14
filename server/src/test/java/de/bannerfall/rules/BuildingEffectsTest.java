package de.bannerfall.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.rules.BuildingEffect.Kind;
import de.bannerfall.world.WorldSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingEffectsTest {

    private static final WorldSettings WORLD = WorldSettings.defaults();

    private static BuildingEffect only(BuildingType type, int fromLevel, WorldSettings world) {
        List<BuildingEffect> effects = BuildingEffects.upgrade(type, fromLevel, world);
        assertThat(effects).hasSize(1);
        return effects.get(0);
    }

    @Test
    void aMineShowsProductionBeforeAndAfterWithTheWorldSpeed() {
        BuildingEffect effect = only(BuildingType.WOODCUTTER, 4, WORLD.withSpeed(6));

        assertThat(effect.kind()).isEqualTo(Kind.PRODUCTION_PER_HOUR);
        // 02-game-rules §3: level 5 makes 55.26/h at speed 1
        assertThat(effect.to()).isCloseTo(55.26 * 6, within(0.03));
        assertThat(effect.from()).isCloseTo(Production.perHour(4, 6, 1.0), within(1e-9));
    }

    @Test
    void aRazedMineStartsFromTheBaseProduction() {
        assertThat(only(BuildingType.CLAY_PIT, 0, WORLD).from()).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void storageAndFarmUseTheSameCapacitiesAsTheGame() {
        BuildingEffect warehouse = only(BuildingType.WAREHOUSE, 9, WORLD);
        assertThat(warehouse.kind()).isEqualTo(Kind.WAREHOUSE_CAPACITY);
        assertThat(warehouse.from()).isEqualTo(Storage.warehouseCapacity(9));
        assertThat(warehouse.to()).isEqualTo(6397); // §3: level 10

        BuildingEffect farm = only(BuildingType.FARM, 1, WORLD);
        assertThat(farm.kind()).isEqualTo(Kind.FARM_CAPACITY);
        assertThat(farm.from()).isEqualTo(240);
        assertThat(farm.to()).isEqualTo(Population.farmCapacity(2));

        BuildingEffect hiding = only(BuildingType.HIDING_PLACE, 0, WORLD);
        assertThat(hiding.from()).isEqualTo(0);
        assertThat(hiding.to()).isEqualTo(150);
    }

    @Test
    void aBuildingBelowItsFirstLevelHasNothingToCompareWith() {
        // warehouse and farm have no capacity at level 0, and a Barracks recruits nothing
        assertThat(only(BuildingType.WAREHOUSE, 0, WORLD).from()).isNull();
        assertThat(only(BuildingType.FARM, 0, WORLD).from()).isNull();
        BuildingEffect barracks = only(BuildingType.BARRACKS, 0, WORLD);
        assertThat(barracks.from()).isNull();
        assertThat(barracks.to()).isCloseTo(100.0, within(1e-9));
    }

    @Test
    void theTownHallShrinksBuildTimeByItsFactorPerLevel() {
        BuildingEffect effect = only(BuildingType.HEADQUARTERS, 1, WORLD);

        assertThat(effect.kind()).isEqualTo(Kind.BUILD_TIME_PERCENT);
        assertThat(effect.from()).isCloseTo(95.2, within(1e-9));
        assertThat(effect.to()).isCloseTo(100 * 0.952 * 0.952, within(1e-9));
    }

    @Test
    void recruitBuildingsShrinkTheRecruitTimeAboveLevelOne() {
        BuildingEffect effect = only(BuildingType.STABLE, 1, WORLD);

        assertThat(effect.kind()).isEqualTo(Kind.RECRUIT_TIME_PERCENT);
        assertThat(effect.from()).isCloseTo(100.0, within(1e-9));
        assertThat(effect.to()).isCloseTo(93.7, within(1e-9));
    }

    @Test
    void theMarketCountsMerchantsLikeTheGame() {
        BuildingEffect effect = only(BuildingType.MARKET, 10, WORLD);

        assertThat(effect.from()).isEqualTo(10);
        assertThat(effect.to()).isEqualTo(11); // 10 + (11 - 10)^2
    }

    @Test
    void theWallAddsAPercentageAndFlatPointsThatMatchTheBattleFormula() {
        List<BuildingEffect> effects = BuildingEffects.upgrade(BuildingType.WALL, 4, WORLD);

        assertThat(effects)
                .extracting(BuildingEffect::kind)
                .containsExactly(Kind.WALL_DEFENSE_PERCENT, Kind.WALL_DEFENSE_FLAT);
        assertThat(effects.get(0).to()).isEqualTo(25.0);
        assertThat(effects.get(1).from()).isEqualTo(200.0);
        // the same numbers as Combat.defenseStrength: (d + 20 + flat) × (1 + percent / 100)
        double defense = 100;
        BuildingEffect percent = effects.get(0);
        BuildingEffect flat = effects.get(1);
        assertThat((defense + 20 + flat.to()) * (1 + percent.to() / 100))
                .isCloseTo(Combat.defenseStrength(defense, 5), within(1e-9));
    }

    @Test
    void unlockOnlyBuildingsHaveNoEffects() {
        for (BuildingType type :
                List.of(BuildingType.SMITHY, BuildingType.ACADEMY, BuildingType.RALLY_POINT)) {
            assertThat(BuildingEffects.upgrade(type, 0, WORLD)).isEmpty();
        }
    }

    @Test
    void everyBuildingTypeIsHandled() {
        for (BuildingType type : BuildingType.values()) {
            assertThat(BuildingEffects.upgrade(type, 3, WORLD)).isNotNull();
        }
    }

    @Test
    void aNegativeLevelIsRejected() {
        assertThatThrownBy(() -> BuildingEffects.upgrade(BuildingType.FARM, -1, WORLD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
