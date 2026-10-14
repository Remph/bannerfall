package de.bannerfall.notify;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.report.ReportKind;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What the server tells one player live over {@code /user/queue/events} (04-api <i>WebSocket</i>).
 * Only a hint for the web client to refetch or show a toast: everything in it can be read through
 * the REST API too, and a client that was offline simply loads the current state. The property
 * {@code type} tells the records apart.
 *
 * <p>Sent with {@link PlayerNotification} after the transaction commits ({@link
 * NotificationPublisher}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameNotification.VillageUpdated.class, name = "VILLAGE_UPDATED"),
    @JsonSubTypes.Type(value = GameNotification.IncomingAttack.class, name = "INCOMING_ATTACK"),
    @JsonSubTypes.Type(value = GameNotification.ReportCreated.class, name = "REPORT_CREATED"),
    @JsonSubTypes.Type(value = GameNotification.BuildComplete.class, name = "BUILD_COMPLETE"),
    @JsonSubTypes.Type(value = GameNotification.SupportArrived.class, name = "SUPPORT_ARRIVED"),
    @JsonSubTypes.Type(value = GameNotification.VillageLost.class, name = "VILLAGE_LOST"),
    @JsonSubTypes.Type(value = GameNotification.VillageConquered.class, name = "VILLAGE_CONQUERED"),
    @JsonSubTypes.Type(value = GameNotification.WorldOpened.class, name = "WORLD_OPENED"),
    @JsonSubTypes.Type(value = GameNotification.GuildInvited.class, name = "GUILD_INVITED"),
    @JsonSubTypes.Type(value = GameNotification.GuildChanged.class, name = "GUILD_CHANGED"),
    @JsonSubTypes.Type(value = GameNotification.MessageReceived.class, name = "MESSAGE_RECEIVED"),
    @JsonSubTypes.Type(
            value = GameNotification.AchievementEarned.class,
            name = "ACHIEVEMENT_EARNED"),
    @JsonSubTypes.Type(value = GameNotification.BannerFound.class, name = "BANNER_FOUND"),
    @JsonSubTypes.Type(value = GameNotification.BannerStolen.class, name = "BANNER_STOLEN")
})
// The same names for the OpenAPI description (added by OpenApiConfig: no REST endpoint returns
// it), so the generated TS types say type: "VILLAGE_UPDATED".
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(
                    value = "VILLAGE_UPDATED",
                    schema = GameNotification.VillageUpdated.class),
            @DiscriminatorMapping(
                    value = "INCOMING_ATTACK",
                    schema = GameNotification.IncomingAttack.class),
            @DiscriminatorMapping(
                    value = "REPORT_CREATED",
                    schema = GameNotification.ReportCreated.class),
            @DiscriminatorMapping(
                    value = "BUILD_COMPLETE",
                    schema = GameNotification.BuildComplete.class),
            @DiscriminatorMapping(
                    value = "SUPPORT_ARRIVED",
                    schema = GameNotification.SupportArrived.class),
            @DiscriminatorMapping(
                    value = "VILLAGE_LOST",
                    schema = GameNotification.VillageLost.class),
            @DiscriminatorMapping(
                    value = "VILLAGE_CONQUERED",
                    schema = GameNotification.VillageConquered.class),
            @DiscriminatorMapping(
                    value = "WORLD_OPENED",
                    schema = GameNotification.WorldOpened.class),
            @DiscriminatorMapping(
                    value = "GUILD_INVITED",
                    schema = GameNotification.GuildInvited.class),
            @DiscriminatorMapping(
                    value = "GUILD_CHANGED",
                    schema = GameNotification.GuildChanged.class),
            @DiscriminatorMapping(
                    value = "MESSAGE_RECEIVED",
                    schema = GameNotification.MessageReceived.class),
            @DiscriminatorMapping(
                    value = "ACHIEVEMENT_EARNED",
                    schema = GameNotification.AchievementEarned.class),
            @DiscriminatorMapping(
                    value = "BANNER_FOUND",
                    schema = GameNotification.BannerFound.class),
            @DiscriminatorMapping(
                    value = "BANNER_STOLEN",
                    schema = GameNotification.BannerStolen.class)
        })
public sealed interface GameNotification {

    /**
     * Something about the village changed that its owner didn't do himself in a request: a battle
     * (units, wall, buildings, resources, movements), units coming home, a support sent away or a
     * movement towards it cancelled.
     */
    record VillageUpdated(long villageId) implements GameNotification {}

    /**
     * An attack on one of the player's villages was sent. The units stay hidden, like in {@code GET
     * .../incoming}.
     *
     * @param sourcePlayerName null for a barbarian village
     */
    record IncomingAttack(
            long targetVillageId,
            VillageRefDto sourceVillage,
            @Schema(nullable = true) String sourcePlayerName,
            Instant arriveAt)
            implements GameNotification {}

    /**
     * The player got a new report.
     *
     * @param sharedBy the name of the player who sent it (M40); null for a report of the game
     */
    record ReportCreated(long reportId, ReportKind kind, @Schema(nullable = true) String sharedBy)
            implements GameNotification {}

    /** A building of the player's village reached {@code level}. */
    record BuildComplete(long villageId, BuildingType building, int level)
            implements GameNotification {}

    /**
     * A support arrived: for the supported village's owner {@code villageId} is that village, for
     * the sender it is the home village of the units (its troops overview shows them elsewhere).
     */
    record SupportArrived(long villageId) implements GameNotification {}

    /**
     * The player's village was conquered by someone else (02-game-rules §10); it belongs to the
     * conqueror now. With no villages left the player has to respawn ({@code needsRespawn}).
     */
    record VillageLost(long villageId) implements GameNotification {}

    /** The player conquered the village; it is one of his villages now. */
    record VillageConquered(long villageId) implements GameNotification {}

    /**
     * The pre-game is over (M14g): the world has opened and the player has a village now (or {@code
     * needsRespawn} if the map had no room). The client loads the world and the player again.
     */
    record WorldOpened() implements GameNotification {}

    /** A guild leader invited the player (M15a); the client shows a toast and loads the list. */
    record GuildInvited(long guildId, String guildName) implements GameNotification {}

    /**
     * Something changed in the player's guild or about his place in it: somebody joined, left or
     * was kicked, the lead moved, the guild was disbanded, the description changed, an invitation
     * was withdrawn (M15a). The client loads the guild again.
     */
    record GuildChanged() implements GameNotification {}

    /**
     * Another player sent the player a private message (M17); the client shows a toast, bumps the
     * counter and loads the conversation if it is open. Not sent for a blocked sender.
     */
    record MessageReceived(long fromPlayerId, String fromPlayerName) implements GameNotification {}

    /**
     * The player earned an achievement, or the next level of one (M16, 02-game-rules §19); the
     * client shows a toast and loads the lists again. {@code level} is 1 for a one-time one, which
     * is not {@code leveled}.
     */
    record AchievementEarned(String achievement, boolean leveled, int level)
            implements GameNotification {}

    /**
     * A won battle dropped a banner into the player's stash (M32b, 02-game-rules §21.7); the client
     * shows a toast.
     *
     * @param village the village the battle was fought at
     */
    record BannerFound(BannerType bannerType, int tier, VillageRefDto village)
            implements GameNotification {}

    /**
     * Banner thieves took a banner (M32b, 02-game-rules §21.6.2): sent to the player who lost it
     * ({@code lost}) and to the thief's owner, who has it in his stash now.
     *
     * @param village the village it flew in
     */
    record BannerStolen(BannerType bannerType, int tier, VillageRefDto village, boolean lost)
            implements GameNotification {}
}
