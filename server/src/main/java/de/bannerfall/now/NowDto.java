package de.bannerfall.now;

import de.bannerfall.building.BuildOrderDto;
import de.bannerfall.market.TransportDto;
import de.bannerfall.movement.IncomingDto;
import de.bannerfall.movement.MovementDto;
import de.bannerfall.recruit.DismissOrderDto;
import de.bannerfall.recruit.RecruitOrderDto;
import de.bannerfall.research.ResearchOrderDto;
import de.bannerfall.village.VillageRefDto;
import java.util.List;

/**
 * GET /api/now (04-api <i>Now</i>): everything that is running in the player's villages, in one
 * answer, so the Now column, the drawer and the phone sheet need one request instead of five per
 * village. Only what is not done yet; nothing is changed, so nothing is caught up.
 *
 * @param villages every village of the player, oldest first, each with its running things
 */
public record NowDto(List<NowVillageDto> villages) {

    /**
     * The running things of one village.
     *
     * @param builds the build queue in running order (the first one runs, the others wait)
     * @param training the open recruit orders, each building's queue in running order; {@code
     *     completedCount} is the number of units done right now
     * @param dismissals the open dismiss orders, like {@code training}
     * @param research the researches that were started in this village
     * @param outgoing attacks and supports this village has sent, still on the way, next first
     * @param incoming everything arriving here, next first: attacks, supports, own units returning
     * @param merchants the village's merchants that are away (on the way or coming back)
     * @param incomingMerchants other players' merchants on their way here, next first (2.1); the
     *     player's own merchants between his villages are only in the sender's {@code merchants}
     */
    public record NowVillageDto(
            VillageRefDto village,
            List<BuildOrderDto> builds,
            List<RecruitOrderDto> training,
            List<DismissOrderDto> dismissals,
            List<ResearchOrderDto> research,
            List<MovementDto> outgoing,
            List<IncomingDto> incoming,
            List<TransportDto> merchants,
            List<TransportDto> incomingMerchants) {}
}
