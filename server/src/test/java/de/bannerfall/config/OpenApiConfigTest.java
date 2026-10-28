package de.bannerfall.config;

import static de.bannerfall.TestPlayer.body;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI description the web generates its types from ({@code pnpm gen:api}): what {@link
 * OpenApiConfig} changes in springdoc's output.
 */
@IntegrationTest
class OpenApiConfigTest {

    @Autowired MockMvc mockMvc;

    private String apiDocs;

    @BeforeEach
    void setUp() throws Exception {
        apiDocs = body(Browser.open(mockMvc).get("/v3/api-docs"));
    }

    @Test
    void everyDtoPropertyIsRequired() {
        assertThat(
                        JsonPath.<List<String>>read(
                                apiDocs, "$.components.schemas.VillageRefDto.required"))
                .containsExactlyInAnyOrder("id", "name", "x", "y", "playerId", "playerName");
        // also in the inline part of a subtype (allOf [parent, {properties}])
        assertThat(
                        JsonPath.<List<String>>read(
                                apiDocs, "$.components.schemas.SupportReport.allOf[1].required"))
                .containsExactlyInAnyOrder("from", "to", "units");
    }

    @Test
    void aNullableDtoPropertyIsOneOfTheDtoAndNull() {
        assertThat(
                        JsonPath.<List<Map<String, Object>>>read(
                                apiDocs,
                                "$.components.schemas.BattleReport.allOf[1].properties.defenderSide.oneOf"))
                .containsExactly(
                        Map.of("$ref", "#/components/schemas/DefenderSide"),
                        Map.of("type", "null"));
        assertThat(
                        JsonPath.<List<Map<String, Object>>>read(
                                apiDocs,
                                "$.components.schemas.BuildingInfoDto.properties.next.oneOf"))
                .hasSize(2);
        // a DTO property that can't be null stays a plain $ref
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                apiDocs, "$.components.schemas.ReportSummary.properties.from"))
                .isEqualTo(Map.of("$ref", "#/components/schemas/VillageRefDto"));
    }

    @Test
    void aNullablePolymorphicPropertyIsOneOfItsSubtypesAndNull() {
        // ReportDto.payload (null when a stored payload can't be read): no "type: null" next to
        // oneOf
        assertThat(
                        JsonPath.<List<Map<String, Object>>>read(
                                apiDocs, "$.components.schemas.ReportDto.properties.payload.oneOf"))
                .hasSize(5)
                .contains(
                        Map.of("$ref", "#/components/schemas/BattleReport"),
                        Map.of("type", "null"));
        assertThat(
                        JsonPath.<Map<String, Object>>read(
                                apiDocs, "$.components.schemas.ReportDto.properties.payload"))
                .doesNotContainKey("type");
    }

    @Test
    void reportPayloadsAreToldApartByTheirTypeNames() {
        assertThat(
                        JsonPath.<Map<String, String>>read(
                                apiDocs,
                                "$.components.schemas.ReportPayload.discriminator.mapping"))
                .isEqualTo(
                        Map.of(
                                "BATTLE", "#/components/schemas/BattleReport",
                                "SUPPORT", "#/components/schemas/SupportReport",
                                "CONQUEST", "#/components/schemas/ConquestReport",
                                "TRADE", "#/components/schemas/TradeReport"));
    }

    @Test
    void theStompPayloadsAreDescribedToo() {
        assertThat(
                        JsonPath.<Map<String, String>>read(
                                apiDocs,
                                "$.components.schemas.GameNotification.discriminator.mapping"))
                .containsEntry("VILLAGE_UPDATED", "#/components/schemas/VillageUpdated")
                .containsEntry("INCOMING_ATTACK", "#/components/schemas/IncomingAttack")
                .containsEntry("WORLD_OPENED", "#/components/schemas/WorldOpened")
                .containsEntry("GUILD_INVITED", "#/components/schemas/GuildInvited")
                .containsEntry("GUILD_CHANGED", "#/components/schemas/GuildChanged")
                .containsEntry("GUILD_BANNER_RAISED", "#/components/schemas/GuildBannerRaised")
                .containsEntry("MESSAGE_RECEIVED", "#/components/schemas/MessageReceived")
                .containsEntry("ACHIEVEMENT_EARNED", "#/components/schemas/AchievementEarned")
                .containsEntry("BANNER_FOUND", "#/components/schemas/BannerFound")
                .containsEntry("BANNER_STOLEN", "#/components/schemas/BannerStolen")
                .containsEntry("WORLD_ENDED", "#/components/schemas/WorldEnded")
                .containsEntry("VICTORY_CLOCK", "#/components/schemas/VictoryClock")
                .containsEntry("ATTACKS_RETURNED", "#/components/schemas/AttacksReturned")
                .containsEntry("STORAGE_FULL", "#/components/schemas/StorageFull")
                .hasSize(19);
        // treated like every other DTO: required properties, nullable where it can be null
        assertThat(
                        JsonPath.<List<String>>read(
                                apiDocs, "$.components.schemas.IncomingAttack.allOf[1].required"))
                .containsExactlyInAnyOrder(
                        "targetVillageId", "sourceVillage", "sourcePlayerName", "arriveAt");
        assertThat(
                        JsonPath.<List<String>>read(
                                apiDocs, "$.components.schemas.ChatErrorDto.required"))
                .containsExactlyInAnyOrder("code", "detail");
        assertThat(
                        JsonPath.<List<String>>read(
                                apiDocs, "$.components.schemas.ChatMessageDto.required"))
                .containsExactlyInAnyOrder("id", "playerId", "playerName", "text", "createdAt");
    }
}
