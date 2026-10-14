package de.bannerfall.village;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.player.Player;
import de.bannerfall.world.WorldSettings;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The pure part of the catch-up (01-architecture §4.4, 02-game-rules §3 and §10). */
class VillageStateServiceTest {

    private static final WorldSettings WORLD = WorldSettings.defaults();
    private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant T2H = T0.plus(Duration.ofHours(2));

    /** Woodcutter 1 (30/h), clay pit 5 (55.26/h), iron mine 0 (5/h), warehouse 1 (cap 1000). */
    private static Village village() {
        var levels = new BuildingLevels();
        levels.set(BuildingType.WOODCUTTER, 1);
        levels.set(BuildingType.CLAY_PIT, 5);
        levels.set(BuildingType.WAREHOUSE, 1);
        levels.set(BuildingType.FARM, 1);
        return new Village(null, "Test", new Coordinates(50, 50), 500, levels, 0, T0);
    }

    @Test
    void twoHoursOfProduction() {
        Village village = village();

        VillageStateService.advance(village, T2H, WORLD);

        assertThat(village.getWood()).isCloseTo(560, within(1e-9));
        assertThat(village.getClay()).isCloseTo(500 + 2 * 55.26, within(0.01));
        assertThat(village.getIron()).isCloseTo(510, within(1e-9));
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T2H);
    }

    @Test
    void theOwnersProductionFactorMultipliesProductionOfNpcVillagesOnly() {
        Village npcVillage = village();
        Village humanVillage = village();
        Village barbarianVillage = village();
        setOwner(npcVillage, Player.npcLord("Lord Test", 0.5, T0));
        setOwner(humanVillage, Player.human(1, "human", T0, T0));

        VillageStateService.advance(npcVillage, T2H, WORLD);
        VillageStateService.advance(humanVillage, T2H, WORLD);
        VillageStateService.advance(barbarianVillage, T2H, WORLD);

        // woodcutter 1 = 30/h: 2 hours = 60 (human, barbarian) or 30 (factor 0.5)
        assertThat(npcVillage.getWood()).isCloseTo(530, within(1e-9));
        assertThat(humanVillage.getWood()).isCloseTo(560, within(1e-9));
        assertThat(barbarianVillage.getWood()).isCloseTo(560, within(1e-9));
    }

    private static void setOwner(Village village, Player owner) {
        try {
            var field = Village.class.getDeclaredField("player");
            field.setAccessible(true);
            field.set(village, owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void worldSpeedMultipliesProduction() {
        Village village = village();

        VillageStateService.advance(village, T2H, WORLD.withSpeed(3));

        assertThat(village.getWood()).isCloseTo(680, within(1e-9));
    }

    @Test
    void productionStopsAtTheWarehouseCapacity() {
        Village village = village();
        village.setResources(990, 500, 500, T0);

        VillageStateService.advance(village, T2H, WORLD);

        assertThat(village.getWood()).isEqualTo(1000);
    }

    @Test
    void resourcesAboveTheCapacityAreNotReduced() {
        Village village = village();
        village.setResources(1500, 500, 500, T0);

        VillageStateService.advance(village, T2H, WORLD);

        assertThat(village.getWood()).isEqualTo(1500);
        assertThat(village.getClay()).isGreaterThan(500);
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T2H);
    }

    @Test
    void catchingUpInStepsGivesTheSameResult() {
        Village once = village();
        Village twice = village();

        VillageStateService.advance(once, T2H, WORLD);
        VillageStateService.advance(twice, T0.plus(Duration.ofHours(1)), WORLD);
        VillageStateService.advance(twice, T2H, WORLD);
        VillageStateService.advance(twice, T2H, WORLD); // idempotent

        assertThat(twice.getWood()).isCloseTo(once.getWood(), within(1e-9));
        assertThat(twice.getClay()).isCloseTo(once.getClay(), within(1e-9));
        assertThat(twice.getIron()).isCloseTo(once.getIron(), within(1e-9));
    }

    @Test
    void neverGoesBackwards() {
        Village village = village();
        village.setLoyalty(25, T0);
        VillageStateService.advance(village, T2H, WORLD);
        double wood = village.getWood();
        double loyalty = village.getLoyalty();

        VillageStateService.advance(village, T0.plus(Duration.ofHours(1)), WORLD);

        assertThat(village.getWood()).isEqualTo(wood);
        assertThat(village.getLoyalty()).isEqualTo(loyalty);
        assertThat(village.getResourcesUpdatedAt()).isEqualTo(T2H);
        assertThat(village.getLoyaltyUpdatedAt()).isEqualTo(T2H);
    }

    @Test
    void loyaltyRegeneratesUpToOneHundred() {
        Village village = village();
        assertThat(village.getLoyalty()).isEqualTo(100);

        village.setLoyalty(25, T0);
        VillageStateService.advance(village, T2H, WORLD.withSpeed(3));
        assertThat(village.getLoyalty()).isCloseTo(31, within(1e-9));

        VillageStateService.advance(village, T2H.plus(Duration.ofDays(4)), WORLD);
        assertThat(village.getLoyalty()).isEqualTo(100);
    }
}
