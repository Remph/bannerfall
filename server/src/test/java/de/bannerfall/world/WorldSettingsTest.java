package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.npc.NpcDifficulty;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The defaults are the table of 02-game-rules §1. */
class WorldSettingsTest {

    @Test
    void defaultsMatchTheRulebook() {
        var world = WorldSettings.defaults();

        assertThat(world.name()).isEqualTo("Bannerfall 1");
        assertThat(world.speed()).isEqualTo(1.0);
        assertThat(world.unitSpeed()).isEqualTo(1.0);
        assertThat(world.mapSize()).isEqualTo(100);
        assertThat(world.startResources()).isEqualTo(500);
        assertThat(world.luckRange()).isEqualTo(0.25);
        assertThat(world.beginnerProtectionHours()).isEqualTo(72);
        assertThat(world.nobleMaxDistance()).isEqualTo(70);
        assertThat(world.loyaltyRegenPerHour()).isEqualTo(1.0);
        assertThat(world.buildQueueSize()).isEqualTo(5);
        assertThat(world.buildQueueMax()).isEqualTo(10);
        assertThat(world.recruitQueueSize()).isEqualTo(5);
        assertThat(world.cancelWindowSeconds()).isEqualTo(600);
        assertThat(world.barbarianInitialCount()).isEqualTo(30);
        assertThat(world.barbariansPerNewPlayer()).isEqualTo(3);
        assertThat(world.barbarianGrowthEnabled()).isTrue();
        assertThat(world.tradeMaxRatio()).isEqualTo(3.0);
        assertThat(world.marketExchangeRatio()).isEqualTo(1.5);
        assertThat(world.inviteCode()).isEqualTo("change-me");
        assertThat(world.npcLordCount()).isEqualTo(4);
        assertThat(world.npcDifficulty()).isEqualTo(NpcDifficulty.NORMAL);
        assertThat(world.npcThinkMinutes()).isEqualTo(20);
        assertThat(world.npcCustom()).isNull();
        // M29: the defaults keep a world as it was
        assertThat(world.barbarianWallsEnabled()).isFalse();
        assertThat(world.barbarianWallStartDays()).isEqualTo(3);
        assertThat(world.barbarianWallMaxLevel()).isEqualTo(5);
        assertThat(world.barbarianWallsRebuild()).isFalse();
        assertThat(world.unitLevelsEnabled()).isFalse();
        // M31: one extra paladin slot per 4 villages, at most 5
        assertThat(world.paladinVillagesPerSlot()).isEqualTo(4);
        assertThat(world.paladinMaxSlots()).isEqualTo(5);
    }

    @Test
    void withSpeedAndWithUnitSpeedChangeOnlyThatSetting() {
        var base = WorldSettings.defaults();

        assertThat(base.withSpeed(3)).isEqualTo(WorldSettings.builder().speed(3).build());
        assertThat(base.withUnitSpeed(2).unitSpeed()).isEqualTo(2.0);
        assertThat(base.withUnitSpeed(2).speed()).isEqualTo(1.0);
    }

    // ---- the builder (1.3): every setting by name, none forgotten ----

    @Test
    void everySettingDiffersFromItsDefaultInTheUnusualSettings() throws Exception {
        var defaults = WorldSettings.defaults();
        var unusual = unusualSettings();

        for (RecordComponent component : COMPONENTS) {
            assertThat(component.getAccessor().invoke(unusual))
                    .as(component.getName())
                    .isNotEqualTo(component.getAccessor().invoke(defaults));
        }
    }

    @Test
    void eachBuilderMethodSetsItsOwnSetting() throws Exception {
        var unusual = unusualSettings();

        for (RecordComponent component : COMPONENTS) {
            Object value = component.getAccessor().invoke(unusual);
            WorldSettings.Builder builder = WorldSettings.builder();
            WorldSettings.Builder.class
                    .getMethod(component.getName(), component.getType())
                    .invoke(builder, value);

            assertThat(builder.build())
                    .as(component.getName())
                    .isEqualTo(withComponent(WorldSettings.defaults(), component, value));
        }
    }

    @Test
    void toBuilderKeepsEverySetting() throws Exception {
        var unusual = unusualSettings();

        assertThat(unusual.toBuilder().build()).isEqualTo(unusual);
    }

    @Test
    void theEntityHandsOnEverySetting() throws Exception {
        var unusual = unusualSettings();
        var entity = new WorldSettingsEntity();
        for (RecordComponent component : COMPONENTS) {
            Field field = WorldSettingsEntity.class.getDeclaredField(component.getName());
            field.setAccessible(true);
            field.set(entity, component.getAccessor().invoke(unusual));
        }

        assertThat(entity.toSettings()).isEqualTo(unusual);
    }

    private static final RecordComponent[] COMPONENTS = WorldSettings.class.getRecordComponents();

    /** Settings where every value differs from the default, made without the builder. */
    private static WorldSettings unusualSettings() throws Exception {
        Object[] values = new Object[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            values[i] = unusual(COMPONENTS[i].getType(), i);
        }
        return canonical(WorldSettings.class).newInstance(values);
    }

    private static WorldSettings withComponent(
            WorldSettings base, RecordComponent changed, Object value) throws Exception {
        Object[] values = new Object[COMPONENTS.length];
        for (int i = 0; i < COMPONENTS.length; i++) {
            values[i] =
                    COMPONENTS[i].equals(changed)
                            ? value
                            : COMPONENTS[i].getAccessor().invoke(base);
        }
        return canonical(WorldSettings.class).newInstance(values);
    }

    /** A value no default uses: odd numbers, flipped booleans, the last enum constant, a record. */
    private static Object unusual(Class<?> type, int index) throws Exception {
        if (type == int.class) {
            return 1000 + index;
        }
        if (type == double.class) {
            return 1000.5 + index;
        }
        if (type == boolean.class) {
            Object byDefault = COMPONENTS[index].getAccessor().invoke(WorldSettings.defaults());
            return !(Boolean) byDefault;
        }
        if (type == String.class) {
            return "unusual-" + index;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants[constants.length - 1];
        }
        if (type.isRecord()) {
            RecordComponent[] parts = type.getRecordComponents();
            Object[] values = new Object[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = unusualPart(parts[i].getType(), i);
            }
            return canonical(type).newInstance(values);
        }
        throw new IllegalStateException("no unusual value for " + type);
    }

    private static Object unusualPart(Class<?> type, int index) {
        if (type == int.class) {
            return 2000 + index;
        }
        if (type == double.class) {
            return 0.25 + index;
        }
        if (type == boolean.class) {
            return true;
        }
        if (type == String.class) {
            return "0" + (index % 10) + ":30";
        }
        throw new IllegalStateException("no unusual value for " + type);
    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> canonical(Class<T> type) throws Exception {
        Class<?>[] types =
                Arrays.stream(type.getRecordComponents())
                        .map(RecordComponent::getType)
                        .toArray(Class<?>[]::new);
        return (Constructor<T>) type.getDeclaredConstructor(types);
    }
}
