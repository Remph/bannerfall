package de.bannerfall.diplomacy;

import de.bannerfall.guild.GuildRefDto;
import de.bannerfall.npc.LordVariantDto;
import de.bannerfall.village.VillageRefDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * An NPC lord on the diplomacy page (2.1 S2b, 12-solo-and-allies §7), seen by one human.
 *
 * @param guild the lord's guild: the human's own for an ally, a lord guild otherwise; null without
 * @param ally the lord is in the human's guild
 * @param trust the lord's trust in the human now (−100 to 100); null outside a solo world, where no
 *     lord can become his ally (2.1 feature review, finding 03)
 * @param bar the trust it joins the human's guild at; null for an ally and before the human has met
 *     the lord (first contact, like {@code variant}: the bar gives its personality away)
 * @param changes the last three changes of its trust, newest first; empty outside a solo world
 * @param home its strongest village (where it is)
 * @param invitationId the open invitation of the human's guild to it; null if none
 * @param inviteAfter it said no: the next invitation waits until then (game time); null if it
 *     needn't
 * @param variant its variant once the human has met it (09 §16.7); null before
 * @param order the human's current order to it (an ally, 2.1 S3); null if none
 */
public record LordDto(
        long playerId,
        String name,
        @Schema(nullable = true) GuildRefDto guild,
        boolean ally,
        @Schema(nullable = true) Integer trust,
        @Schema(nullable = true) Integer bar,
        List<TrustChangeDto> changes,
        int villages,
        long points,
        @Schema(nullable = true) VillageRefDto home,
        @Schema(nullable = true) Long invitationId,
        @Schema(nullable = true) Instant inviteAfter,
        @Schema(nullable = true) LordVariantDto variant,
        @Schema(nullable = true) OrderDto order) {}
