package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.TestGameData;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.npc.NpcWorld.MapVillage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The nine lord variants (09-npc-lords §16): overlays, defaults and validation. */
class NpcVariantsTest {

    private static final GameData DATA = TestGameData.load();
    private static final NpcTemplates TEMPLATES = TestNpcData.templates();
    private static final Map<NpcDifficulty, Difficulty> DIFFICULTIES =
            TestNpcData.balance().balance().difficulties();

    @Test
    void nineVariantsThreePerType() {
        assertThat(TEMPLATES.variants()).hasSize(9);
        assertThat(TEMPLATES.variantsOf(NpcPersonality.OFFENSIVE))
                .containsExactly("CONQUEROR", "HOTHEAD", "ROBBER_KNIGHT");
        assertThat(TEMPLATES.variantsOf(NpcPersonality.DEFENSIVE))
                .containsExactly("CASTELLAN", "MERCHANT", "PROTECTOR");
        assertThat(TEMPLATES.variantsOf(NpcPersonality.BALANCED))
                .containsExactly("KNIGHT", "SETTLER", "VULTURE");
    }

    @Test
    void allNineResolvedTemplatesAreValid() {
        assertThat(NpcTemplateValidation.problems(TEMPLATES, DATA)).isEmpty();
        for (String key : TEMPLATES.variants().keySet()) {
            assertThat(TEMPLATES.ofVariant(key)).as(key).isNotNull();
        }
    }

    @Test
    void theTypesKeepTodaysBehaviourThroughTheDefaults() {
        for (NpcPersonality p : NpcPersonality.values()) {
            NpcTemplates.Template t = TEMPLATES.of(p);
            assertThat(t.raidFirst()).isFalse();
            assertThat(t.humansOnlyOnGrudge()).isFalse();
            assertThat(t.grudgeFactor()).isEqualTo(1.0);
            assertThat(t.retaliations()).isEqualTo(1);
            assertThat(t.supportHorizonFactor()).isEqualTo(1.0);
            assertThat(t.shipFrom()).isEqualTo(0.9);
            assertThat(t.targetMinRatio()).isNull();
            assertThat(t.prefersFalling()).isFalse();
            assertThat(t.spareLastVillage()).isNull();
        }
    }

    @Test
    void aMixStageGivenReplacesOnlyThatStage() {
        NpcTemplates.Template base = TEMPLATES.of(NpcPersonality.OFFENSIVE);
        NpcTemplates.Template robber = TEMPLATES.ofVariant("ROBBER_KNIGHT");

        assertThat(robber.mix().get("MID")).containsEntry(UnitType.LIGHT_CAVALRY, 55);
        assertThat(robber.mix().get("EARLY")).isEqualTo(base.mix().get("EARLY"));
        // base fields not named stay
        assertThat(robber.margin()).isEqualTo(base.margin());
        assertThat(robber.attackUnits()).isEqualTo(base.attackUnits());
        assertThat(robber.bannerFavourites()).isEqualTo(base.bannerFavourites());
        // named fields change
        assertThat(robber.farmUnits())
                .containsExactly(UnitType.LIGHT_CAVALRY, UnitType.MOUNTED_ARCHER);
        assertThat(robber.raidFirst()).isTrue();
        assertThat(robber.conquersPlayers()).isFalse();
        assertThat(robber.nobles()).isEqualTo(2);
    }

    @Test
    void aListGivenReplacesTheWholeList() {
        NpcTemplates.Template conqueror = TEMPLATES.ofVariant("CONQUEROR");

        assertThat(conqueror.stages())
                .extracting(NpcTemplates.Stage::troopShare)
                .containsExactly(0.25, 0.40, 0.55);
        assertThat(conqueror.stages().get(1).belowPoints()).isEqualTo(4500);
        List<Map<BuildingType, Integer>> build = conqueror.build();
        int farm15 = build.indexOf(Map.of(BuildingType.FARM, 15, BuildingType.WAREHOUSE, 15));
        assertThat(build.get(farm15 + 5)).isEqualTo(Map.of(BuildingType.ACADEMY, 1));
        int academyOffensive =
                TEMPLATES
                        .of(NpcPersonality.OFFENSIVE)
                        .build()
                        .indexOf(Map.of(BuildingType.ACADEMY, 1));
        assertThat(farm15 + 5).isLessThan(academyOffensive);
    }

    @Test
    void theNewFieldsBindAsDesigned() {
        assertThat(TEMPLATES.ofVariant("HOTHEAD").humansOnlyOnGrudge()).isTrue();
        assertThat(TEMPLATES.ofVariant("HOTHEAD").grudgeFactor()).isEqualTo(2.0);
        assertThat(TEMPLATES.ofVariant("HOTHEAD").retaliations()).isEqualTo(2);
        assertThat(TEMPLATES.ofVariant("PROTECTOR").supportHorizonFactor()).isEqualTo(1.5);
        assertThat(TEMPLATES.ofVariant("MERCHANT").shipFrom()).isEqualTo(0.8);
        assertThat(TEMPLATES.ofVariant("KNIGHT").targetMinRatio()).isEqualTo(0.9);
        assertThat(TEMPLATES.ofVariant("KNIGHT").spareLastVillage()).isTrue();
        assertThat(TEMPLATES.ofVariant("VULTURE").prefersFalling()).isTrue();
        assertThat(TEMPLATES.ofVariant("CASTELLAN").villageRatio()).isEqualTo(0.3);
    }

    @Test
    void aLordPlaysItsVariantAndAnUnknownKeyFallsBack() {
        NpcBrain hothead = new NpcBrain(1, NpcPersonality.OFFENSIVE, "HOTHEAD");
        NpcBrain old = new NpcBrain(2, NpcPersonality.OFFENSIVE);
        NpcBrain unknown = new NpcBrain(3, NpcPersonality.DEFENSIVE, "DRAGON");
        NpcBrain wrongType = new NpcBrain(4, NpcPersonality.DEFENSIVE, "HOTHEAD");

        assertThat(TEMPLATES.of(hothead)).isSameAs(TEMPLATES.ofVariant("HOTHEAD"));
        assertThat(TEMPLATES.of(old)).isSameAs(TEMPLATES.of(NpcPersonality.OFFENSIVE));
        assertThat(TEMPLATES.of(unknown)).isSameAs(TEMPLATES.of(NpcPersonality.DEFENSIVE));
        assertThat(TEMPLATES.of(wrongType)).isSameAs(TEMPLATES.of(NpcPersonality.DEFENSIVE));
        assertThat(TEMPLATES.variantKey(hothead)).isEqualTo("HOTHEAD");
        assertThat(TEMPLATES.variantKey(old)).isNull();
    }

    @Test
    void theNewFieldsAreValidated() {
        NpcTemplates.Variant broken =
                new NpcTemplates.Variant(
                        NpcPersonality.BALANCED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        3.5,
                        0,
                        2.5,
                        0.5,
                        1.2,
                        null,
                        null);
        NpcTemplates.Variant noBase =
                new NpcTemplates.Variant(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null);
        var templates =
                new NpcTemplates(TEMPLATES.templates(), Map.of("BROKEN", broken, "PLAIN", noBase));

        assertThat(NpcTemplateValidation.problems(templates, DATA))
                .containsExactlyInAnyOrder(
                        "BROKEN.grudge-factor must be in 1..3",
                        "BROKEN.retaliations must be in 1..3",
                        "BROKEN.support-horizon-factor must be in 1..2",
                        "BROKEN.ship-from must be in 0.75..0.95",
                        "BROKEN.target-min-ratio must be in 0..1",
                        "PLAIN: a variant can't be called like a personality or PLAIN",
                        "PLAIN.base: missing or without a template");
    }

    // ---- each new field's effect (09 §16.4) ----

    @Test
    void humansOnlyOnGrudgeLeavesAHumanAloneUntilTheGrudge() {
        NpcTemplates.Template hothead = TEMPLATES.ofVariant("HOTHEAD");
        MapVillage human = NpcTargetingTest.human(100, 100, 3);

        assertThat(NpcTargeting.targetAllowed(hothead, 1000, 5000, human, false)).isFalse();
        assertThat(NpcTargeting.targetAllowed(hothead, 1000, 5000, human, true)).isTrue();
        // lords and barbarians as the type
        assertThat(
                        NpcTargeting.targetAllowed(
                                hothead, 1000, 5000, NpcTargetingTest.lord(100, 100), false))
                .isTrue();
        assertThat(
                        NpcTargeting.targetAllowed(
                                hothead, 1000, 5000, NpcTargetingTest.barbarian(100), false))
                .isTrue();
        // the type: no such limit
        assertThat(
                        NpcTargeting.targetAllowed(
                                TEMPLATES.of(NpcPersonality.OFFENSIVE), 1000, 5000, human, false))
                .isTrue();
    }

    @Test
    void targetMinRatioSparesSmallPlayersEvenOnAGrudgeAndReplacesTheRatios() {
        NpcTemplates.Template knight = TEMPLATES.ofVariant("KNIGHT");

        // below 90 % of the lord's 1,000 points: never, a grudge doesn't lift it
        assertThat(
                        NpcTargeting.targetAllowed(
                                knight, 1000, 1000, NpcTargetingTest.human(500, 899, 2), false))
                .isFalse();
        assertThat(
                        NpcTargeting.targetAllowed(
                                knight, 1000, 1000, NpcTargetingTest.human(500, 899, 2), true))
                .isFalse();
        assertThat(
                        NpcTargeting.targetAllowed(
                                knight, 1000, 1000, NpcTargetingTest.lord(500, 899), false))
                .isFalse();
        // from 90 %: yes, though the type's village-ratio (0.8) and human-player-ratio (0.75)
        // would refuse a village and an owner this big
        assertThat(
                        NpcTargeting.targetAllowed(
                                knight, 1000, 1000, NpcTargetingTest.human(5000, 900, 2), false))
                .isTrue();
        assertThat(
                        NpcTargeting.targetAllowed(
                                TEMPLATES.of(NpcPersonality.BALANCED),
                                1000,
                                1000,
                                NpcTargetingTest.human(5000, 900, 2),
                                false))
                .isFalse();
    }

    @Test
    void spareLastVillageOverridesTheDifficulty() {
        Difficulty hard = DIFFICULTIES.get(NpcDifficulty.HARD);
        MapVillage last = NpcTargetingTest.human(500, 500, 1);

        assertThat(NpcBalance.spared(hard, last)).isFalse();
        assertThat(NpcBalance.spared(hard, TEMPLATES.of(NpcPersonality.BALANCED), last)).isFalse();
        assertThat(NpcBalance.spared(hard, TEMPLATES.ofVariant("KNIGHT"), last)).isTrue();
        assertThat(
                        NpcBalance.spared(
                                hard,
                                TEMPLATES.ofVariant("KNIGHT"),
                                NpcTargetingTest.human(500, 500, 2)))
                .isFalse();
    }

    @Test
    void shipFromLowersTheShareAVillageShipsFrom() {
        int capacity = 10_000;
        ResourceAmounts stock = new ResourceAmounts(8_500, 8_000, 7_999);

        assertThat(NpcShipments.spare(stock, capacity, ResourceAmounts.ZERO))
                .isEqualTo(ResourceAmounts.ZERO);
        // the Merchant Lord from 80 %, still down to 70 %
        assertThat(
                        NpcShipments.spare(
                                stock,
                                capacity,
                                ResourceAmounts.ZERO,
                                TEMPLATES.ofVariant("MERCHANT").shipFrom()))
                .isEqualTo(new ResourceAmounts(1_500, 1_000, 0));
    }

    @Test
    void raidFirstAndPrefersFallingMoveTargetsAheadOfNearerOnes() {
        MapVillage barbarianNear = new MapVillage(1, 1, 0, null, false, 100, 100, 0, 0, null);
        MapVillage raid = new MapVillage(2, 5, 0, 20L, false, 100, 100, 900, 2, null);
        MapVillage falling = new MapVillage(3, 9, 0, 30L, false, 100, 100, 900, 2, null);
        List<MapVillage> all = List.of(falling, raid, barbarianNear);
        Coordinates home = new Coordinates(0, 0);

        assertThat(
                        NpcTargeting.farmOrder(
                                all,
                                v -> home.distanceTo(v.coordinates()),
                                v -> false,
                                v -> false,
                                v -> false))
                .containsExactly(barbarianNear, raid, falling);
        assertThat(
                        NpcTargeting.farmOrder(
                                all,
                                v -> home.distanceTo(v.coordinates()),
                                v -> false,
                                v -> false,
                                v -> v == raid))
                .containsExactly(raid, barbarianNear, falling);
        assertThat(
                        NpcTargeting.farmOrder(
                                all,
                                v -> home.distanceTo(v.coordinates()),
                                v -> false,
                                v -> Long.valueOf(30).equals(v.playerId()),
                                v -> v == raid))
                .containsExactly(falling, raid, barbarianNear);
    }

    @Test
    void sharedGrudgesLastLongerWithAGrudgeFactor() {
        Instant at = Instant.parse("2026-11-19T08:00:00Z");
        long lord = 1;
        long mate = 2;
        long human = 3;
        MapVillage source = new MapVillage(9, 10, 0, human, false, 300, 100, 900, 2, null);
        Instant until = at.plus(Duration.ofHours(10));
        var memory = NpcMemory.empty().draft();

        NpcIntel.shareGrudges(
                memory,
                lord,
                Set.of(mate),
                List.of(Map.of(human, new NpcMemory.Grudge(until, 9, false, 0))),
                List.of(new Coordinates(0, 0)),
                Map.of(9L, source),
                20,
                at,
                Duration.ofHours(16));

        assertThat(memory.grudges.get(human).until()).isEqualTo(until.plus(Duration.ofHours(16)));
        assertThat(memory.grudges.get(human).shared()).isTrue();
    }

    @Test
    void ownGrudgesLastGrudgeHoursTimesTheFactor() {
        // NpcIntel.read passes grudge-hours × grudge-factor to learn (the Hothead: 2 × 48 h)
        Instant at = Instant.parse("2026-11-19T08:00:00Z");
        var memory = NpcMemory.empty().draft();
        var attack =
                NpcIntelTest.scoutsOnTheLord(
                        new de.bannerfall.village.VillageRefDto(9, "H", 10, 0, 3L, "H"), 5, 5);

        NpcIntel.learn(
                memory,
                NpcIntelTest.LORD,
                de.bannerfall.report.ReportKind.DEFENSE,
                at,
                attack,
                DATA,
                1,
                48 * TEMPLATES.ofVariant("HOTHEAD").grudgeFactor());

        assertThat(memory.grudges.get(3L).until()).isEqualTo(at.plus(Duration.ofHours(96)));
        assertThat(memory.grudges.get(3L).retaliations()).isZero();
    }

    // ---- spread (09 §16.6) ----

    @Test
    void theDrawTakesTheLeastUsedVariantSoNoneRepeatsBeforeAllWereUsed() {
        List<String> offensive = TEMPLATES.variantsOf(NpcPersonality.OFFENSIVE);
        List<String> taken = new ArrayList<>(List.of("OFFENSIVE", "SETTLER"));
        Map<String, Integer> seen = new HashMap<>();
        var random = FixedRandomSource.sequence(0.99, 0.0, 0.5, 0.7, 0.2, 0.9);

        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 3; i++) {
                String drawn =
                        NpcLordService.drawVariant(
                                NpcPersonality.OFFENSIVE, offensive, taken, random);
                taken.add(drawn);
                seen.merge(drawn, 1, Integer::sum);
            }
            // after each full round every variant is used equally often
            int expected = round + 1;
            assertThat(seen)
                    .containsOnlyKeys(offensive)
                    .allSatisfy((k, n) -> assertThat(n).isEqualTo(expected));
        }
    }

    @Test
    void aPersonalityWithoutVariantsDrawsItsPlainTemplate() {
        assertThat(
                        NpcLordService.drawVariant(
                                NpcPersonality.DEFENSIVE,
                                List.of(),
                                List.of(),
                                FixedRandomSource.constant(0.5)))
                .isEqualTo("DEFENSIVE");
    }
}
