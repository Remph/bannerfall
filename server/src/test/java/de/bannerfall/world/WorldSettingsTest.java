package de.bannerfall.world;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.npc.NpcDifficulty;
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

        assertThat(base.withSpeed(3))
                .isEqualTo(
                        new WorldSettings(
                                "Bannerfall 1",
                                3.0,
                                1.0,
                                100,
                                500,
                                0.25,
                                72,
                                70,
                                1.0,
                                5,
                                10,
                                5,
                                600,
                                30,
                                3,
                                true,
                                3.0,
                                "change-me",
                                4,
                                NpcDifficulty.NORMAL,
                                20,
                                null,
                                false,
                                10,
                                false,
                                3,
                                5,
                                false,
                                false,
                                4,
                                5,
                                true,
                                1.0));
        assertThat(base.withUnitSpeed(2).unitSpeed()).isEqualTo(2.0);
        assertThat(base.withUnitSpeed(2).speed()).isEqualTo(1.0);
    }
}
