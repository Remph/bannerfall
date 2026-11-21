package de.bannerfall.chronicle;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What the chronicle knows of a moment (2.1 I1): each {@link ChronicleKind} fills the fields its
 * comment names, the rest stay null. Stored as {@code chronicle_event.data}; new fields must be
 * nullable so older rows still read.
 *
 * @param village the village it happened at (a conquest, an order, a help request)
 * @param keep the village is a banner keep (a conquest)
 * @param from the village's owner before a conquest; null for a barbarian village
 * @param to the conqueror; for {@code CONQUESTS} the day's busiest one
 * @param eliminated the conquest took the last village of {@code from}
 * @param first set on read: the world's first conquest
 * @param side the guild, the court, the leader, the victory clock's side or the winner
 * @param other the marked enemy guild, or the court before
 * @param lord the NPC lord of an alliance, an order or a help request
 * @param player the human it was about (the founder of a guild, who ordered, who was asked)
 * @param attacked an ally left because the human attacked it
 * @param winsAt when the victory clock's side wins (game time when written)
 * @param reason how the world ended ({@code DOMINATION}, {@code KEEPS}, {@code END_DATE})
 * @param count the conquests of a {@code CONQUESTS} line
 * @param topCount how many of them {@code to} made
 */
public record ChronicleFacts(
        @Schema(nullable = true) ChronicleVillage village,
        @Schema(nullable = true) Boolean keep,
        @Schema(nullable = true) ChroniclePlayer from,
        @Schema(nullable = true) ChroniclePlayer to,
        @Schema(nullable = true) Boolean eliminated,
        @Schema(nullable = true) Boolean first,
        @Schema(nullable = true) ChronicleSide side,
        @Schema(nullable = true) ChronicleSide other,
        @Schema(nullable = true) ChroniclePlayer lord,
        @Schema(nullable = true) ChroniclePlayer player,
        @Schema(nullable = true) Boolean attacked,
        @Schema(nullable = true) Instant winsAt,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true) Integer count,
        @Schema(nullable = true) Integer topCount) {

    /** Nothing yet; the {@code with…} methods fill it. */
    public static ChronicleFacts empty() {
        return new ChronicleFacts(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    public ChronicleFacts withConquest(
            ChronicleVillage newVillage,
            boolean newKeep,
            ChroniclePlayer newFrom,
            ChroniclePlayer newTo,
            boolean newEliminated) {
        return new ChronicleFacts(
                newVillage,
                newKeep,
                newFrom,
                newTo,
                newEliminated,
                first,
                side,
                other,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withFirst() {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                true,
                side,
                other,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withVillage(ChronicleVillage newVillage) {
        return new ChronicleFacts(
                newVillage,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withSide(ChronicleSide newSide) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                newSide,
                other,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withOther(ChronicleSide newOther) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                newOther,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withLord(ChroniclePlayer newLord) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                newLord,
                player,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withPlayer(ChroniclePlayer newPlayer) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                lord,
                newPlayer,
                attacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withAttacked(boolean newAttacked) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                lord,
                player,
                newAttacked,
                winsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withWinsAt(Instant newWinsAt) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                lord,
                player,
                attacked,
                newWinsAt,
                reason,
                count,
                topCount);
    }

    public ChronicleFacts withReason(String newReason) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                to,
                eliminated,
                first,
                side,
                other,
                lord,
                player,
                attacked,
                winsAt,
                newReason,
                count,
                topCount);
    }

    public ChronicleFacts withCount(ChroniclePlayer busiest, int newCount, int newTopCount) {
        return new ChronicleFacts(
                village,
                keep,
                from,
                busiest,
                eliminated,
                first,
                side,
                other,
                lord,
                player,
                attacked,
                winsAt,
                reason,
                newCount,
                newTopCount);
    }
}
