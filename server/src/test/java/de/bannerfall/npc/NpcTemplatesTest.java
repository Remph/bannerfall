package de.bannerfall.npc;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The NPC files bind and pass the startup validation (09-npc-lords §9); broken ones don't. */
class NpcTemplatesTest {

    private static final GameData DATA = TestGameData.load();
    private static final NpcTemplates TEMPLATES = TestNpcData.templates();

    @Test
    void theRealFilesAreValid() {
        assertThat(NpcTemplateValidation.problems(TEMPLATES, DATA)).isEmpty();
        assertThat(TestNpcData.balance().balance().difficulties()).hasSize(3);
    }

    @Test
    void theFilesBindAsDocumented() {
        NpcTemplates.Template offensive = TEMPLATES.of(NpcPersonality.OFFENSIVE);
        assertThat(offensive.stages()).hasSize(3);
        assertThat(offensive.stages().get(2).belowPoints()).isNull();
        assertThat(offensive.mixOf(offensive.stages().get(0)))
                .containsEntry(UnitType.AXEMAN, 70)
                .containsEntry(UnitType.SPEARMAN, 30);
        assertThat(offensive.villageRatio()).isNull();
        assertThat(TEMPLATES.of(NpcPersonality.BALANCED).villageRatio()).isEqualTo(0.8);
        assertThat(TEMPLATES.of(NpcPersonality.DEFENSIVE).conquersPlayers()).isFalse();
        assertThat(TEMPLATES.of(NpcPersonality.DEFENSIVE).grudgeTroopBonus()).isEqualTo(0.1);
        assertThat(offensive.build().getFirst())
                .containsEntry(BuildingType.WOODCUTTER, 2)
                .containsEntry(BuildingType.IRON_MINE, 2);

        var normal = TestNpcData.balance().balance().difficulties().get(NpcDifficulty.NORMAL);
        assertThat(normal.productionFactor()).isEqualTo(0.9);
        assertThat(normal.buildQuietFromTime().getHour()).isEqualTo(23);
        assertThat(TestNpcData.balance().balance().zone().getId()).isEqualTo("Europe/Berlin");
    }

    @Test
    void stageFollowsThePoints() {
        NpcTemplates.Template template = TEMPLATES.of(NpcPersonality.BALANCED);

        assertThat(template.stageFor(0).name()).isEqualTo("EARLY");
        assertThat(template.stageFor(999).name()).isEqualTo("EARLY");
        assertThat(template.stageFor(1000).name()).isEqualTo("MID");
        assertThat(template.stageFor(6999).name()).isEqualTo("MID");
        assertThat(template.stageFor(7000).name()).isEqualTo("LATE");
    }

    @Test
    void aStepWhoseRequirementComesLaterFailsTheStart() {
        // The academy needs HQ 20, smithy 20 and the market 10, none of which the opening has.
        List<Map<BuildingType, Integer>> build = new ArrayList<>(base().build());
        build.addFirst(Map.of(BuildingType.ACADEMY, 1));

        assertThat(problemsWith(build))
                .anyMatch(p -> p.contains("build[0].ACADEMY") && p.contains("requirements"));
    }

    @Test
    void aBuildListThatDoesNotMaxEverythingFails() {
        List<Map<BuildingType, Integer>> build = new ArrayList<>(base().build());
        build.removeLast(); // BARRACKS 25, MARKET 25

        assertThat(problemsWith(build))
                .anyMatch(p -> p.contains("BARRACKS ends at 20, not at its max level"))
                .anyMatch(p -> p.contains("MARKET ends at 15"));
    }

    @Test
    void theBannerHallNeedsNoStepBecauseLordsCollectNoBanners() {
        // lords use no banners (M32, 02-game-rules §21.8): the hall is no part of their build
        assertThat(base().build().stream().flatMap(step -> step.keySet().stream()))
                .doesNotContain(BuildingType.BANNER_HALL);
        assertThat(NpcTemplateValidation.problems(TEMPLATES, DATA)).isEmpty();
    }

    @Test
    void aLevelAboveTheMaxFails() {
        List<Map<BuildingType, Integer>> build = new ArrayList<>(base().build());
        build.add(Map.of(BuildingType.WALL, 21));

        assertThat(problemsWith(build)).anyMatch(p -> p.contains("WALL: level must be in 1..20"));
    }

    @Test
    void stagesMustAscendAndEndOpen() {
        NpcTemplates.Template base = base();
        List<NpcTemplates.Stage> stages =
                List.of(
                        new NpcTemplates.Stage("EARLY", 7000, 0.3),
                        new NpcTemplates.Stage("MID", 1000, 0.4),
                        new NpcTemplates.Stage("LATE", null, 0.5));

        assertThat(problems(withStages(base, stages))).anyMatch(p -> p.contains("must ascend"));
        assertThat(
                        problems(
                                withStages(
                                        base,
                                        List.of(
                                                new NpcTemplates.Stage("EARLY", 1000, 0.3),
                                                new NpcTemplates.Stage("MID", 7000, 0.4),
                                                new NpcTemplates.Stage("LATE", 9000, 0.5)))))
                .anyMatch(p -> p.contains("only the last stage has no below-points"));
    }

    @Test
    void aMixWithNoblesOrUnrecruitableUnitsFails() {
        NpcTemplates.Template base = base();
        Map<String, Map<UnitType, Integer>> mix = new java.util.HashMap<>(base.mix());
        mix.put("EARLY", Map.of(UnitType.NOBLE, 10, UnitType.SPEARMAN, 90));
        mix.put("MID", Map.of());
        mix.put("LATE", Map.of(UnitType.PALADIN, 5, UnitType.SPEARMAN, 95));

        assertThat(problems(withMix(base, mix)))
                .anyMatch(p -> p.contains("mix.EARLY.NOBLE: nobles are never in the mix"))
                .anyMatch(p -> p.contains("mix.LATE.PALADIN: paladins are never in the mix"))
                .anyMatch(p -> p.contains("mix.MID: missing"));
    }

    @Test
    void lordsNeverTrainOrSendBannerThieves() {
        // NPC lords don't use banners (M32b, 02-game-rules §21.11)
        NpcTemplates.Template base = base();
        Map<String, Map<UnitType, Integer>> mix = new java.util.HashMap<>(base.mix());
        mix.put("LATE", Map.of(UnitType.BANNER_THIEF, 5, UnitType.SPEARMAN, 95));
        var withThieves =
                new NpcTemplates.Template(
                        base.stages(),
                        mix,
                        base.farmUnits(),
                        List.of(UnitType.AXEMAN, UnitType.BANNER_THIEF),
                        base.supportUnits(),
                        base.homeGuard(),
                        base.supportShare(),
                        base.margin(),
                        base.villageRatio(),
                        base.humanPlayerRatio(),
                        base.grudgeOpens(),
                        base.grudgeTroopBonus(),
                        base.conquersPlayers(),
                        base.farmRadius(),
                        base.conquestRadius(),
                        base.nobles(),
                        base.build());

        assertThat(problems(withThieves))
                .anyMatch(p -> p.contains("mix.LATE.BANNER_THIEF: banner thieves are never"))
                .anyMatch(p -> p.contains("banner thieves are never in a unit list"));
    }

    @Test
    void aMissingTemplateFails() {
        var templates = new NpcTemplates(Map.of(NpcPersonality.BALANCED, base()));

        assertThat(NpcTemplateValidation.problems(templates, DATA))
                .contains("OFFENSIVE: template missing", "DEFENSIVE: template missing");
    }

    private static NpcTemplates.Template base() {
        return TEMPLATES.of(NpcPersonality.BALANCED);
    }

    private static List<String> problemsWith(List<Map<BuildingType, Integer>> build) {
        NpcTemplates.Template b = base();
        return problems(
                new NpcTemplates.Template(
                        b.stages(),
                        b.mix(),
                        b.farmUnits(),
                        b.attackUnits(),
                        b.supportUnits(),
                        b.homeGuard(),
                        b.supportShare(),
                        b.margin(),
                        b.villageRatio(),
                        b.humanPlayerRatio(),
                        b.grudgeOpens(),
                        b.grudgeTroopBonus(),
                        b.conquersPlayers(),
                        b.farmRadius(),
                        b.conquestRadius(),
                        b.nobles(),
                        build));
    }

    private static NpcTemplates.Template withStages(
            NpcTemplates.Template b, List<NpcTemplates.Stage> stages) {
        return new NpcTemplates.Template(
                stages,
                b.mix(),
                b.farmUnits(),
                b.attackUnits(),
                b.supportUnits(),
                b.homeGuard(),
                b.supportShare(),
                b.margin(),
                b.villageRatio(),
                b.humanPlayerRatio(),
                b.grudgeOpens(),
                b.grudgeTroopBonus(),
                b.conquersPlayers(),
                b.farmRadius(),
                b.conquestRadius(),
                b.nobles(),
                b.build());
    }

    private static NpcTemplates.Template withMix(
            NpcTemplates.Template b, Map<String, Map<UnitType, Integer>> mix) {
        return new NpcTemplates.Template(
                b.stages(),
                mix,
                b.farmUnits(),
                b.attackUnits(),
                b.supportUnits(),
                b.homeGuard(),
                b.supportShare(),
                b.margin(),
                b.villageRatio(),
                b.humanPlayerRatio(),
                b.grudgeOpens(),
                b.grudgeTroopBonus(),
                b.conquersPlayers(),
                b.farmRadius(),
                b.conquestRadius(),
                b.nobles(),
                b.build());
    }

    /** The problems of a BALANCED template replaced by {@code template}. */
    private static List<String> problems(NpcTemplates.Template template) {
        var map = new EnumMap<>(TEMPLATES.templates());
        map.put(NpcPersonality.BALANCED, template);
        return NpcTemplateValidation.problems(new NpcTemplates(map), DATA).stream()
                .filter(p -> p.startsWith("BALANCED"))
                .toList();
    }
}
