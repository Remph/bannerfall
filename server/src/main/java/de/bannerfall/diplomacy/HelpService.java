package de.bannerfall.diplomacy;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.ResourceAmounts;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.market.MarketService;
import de.bannerfall.message.LordLetter;
import de.bannerfall.npc.NpcHelpKind;
import de.bannerfall.npc.NpcHelpRequests;
import de.bannerfall.npc.NpcHelpSettings;
import de.bannerfall.npc.NpcHelpStore.Request;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.Merchants;
import de.bannerfall.rules.Travel;
import de.bannerfall.unit.UnitService;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRefDto;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The human's help requests (2.1 S4, 04-api <i>Diplomacy</i>, 12-solo-and-allies §9): the open ones
 * for the strip and the sheet, with what the sheet fills in, and "Not this time".
 */
@Service
class HelpService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NpcHelpRequests requests;
    private final PlayerRepository players;
    private final VillageRepository villages;
    private final UnitService unitService;
    private final WorldService worldService;
    private final GameData gameData;
    private final MarketService market;
    private final JdbcClient jdbc;
    private final Clock clock;

    HelpService(
            NpcHelpRequests requests,
            PlayerRepository players,
            VillageRepository villages,
            UnitService unitService,
            WorldService worldService,
            GameData gameData,
            MarketService market,
            JdbcClient jdbc,
            Clock clock) {
        this.requests = requests;
        this.players = players;
        this.villages = villages;
        this.unitService = unitService;
        this.worldService = worldService;
        this.gameData = gameData;
        this.market = market;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The player's open requests, nearest deadline first; none once a deadline has passed. */
    @Transactional(readOnly = true)
    public List<HelpRequestDto> open(long playerId) {
        Instant now = worldService.gameTime(clock.instant());
        List<Village> own = villages.findByPlayerIdOrderByIdAsc(playerId);
        return requests.open(playerId).stream()
                .filter(request -> now.isBefore(request.deadline()))
                .map(request -> dto(request, own, now))
                .toList();
    }

    /**
     * "Not this time": the lord trusts the player 5 less.
     *
     * @throws ApiException {@code HELP_REQUEST_CLOSED} if it isn't his open request any more
     */
    @Transactional
    public List<HelpRequestDto> decline(long playerId, long requestId) {
        Instant now = worldService.gameTime(clock.instant());
        if (!requests.decline(playerId, requestId, now)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.HELP_REQUEST_CLOSED,
                    "Help request " + requestId + " is not open");
        }
        return open(playerId);
    }

    private HelpRequestDto dto(Request request, List<Village> own, Instant now) {
        WorldSettings world = worldService.settings();
        Village village = villages.findById(request.villageId()).orElseThrow();
        // own villages nearest first; the sheet sends from the nearest one that can help (UI
        // review 2.1: the nearest one alone may have no defenders at home or no market)
        List<Village> nearest =
                own.stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (Village v) ->
                                                        v.coordinates()
                                                                .distanceTo(village.coordinates()))
                                        .thenComparingLong(Village::getId))
                        .toList();
        Village from = null;
        Map<UnitType, Integer> fill = Map.of();
        Long seconds = null;
        ResourceAmounts still = ResourceAmounts.ZERO;
        if (request.kind() == NpcHelpKind.SUPPORT) {
            long left =
                    Math.max(0, java.time.Duration.between(now, request.deadline()).toSeconds());
            for (Village candidate : nearest) {
                Map<UnitType, Integer> units =
                        requests.fill(
                                unitService.homeUnits(candidate.getId()).toMap(),
                                request.defenders(),
                                type ->
                                        Travel.travelSeconds(
                                                gameData,
                                                world,
                                                candidate.coordinates(),
                                                village.coordinates(),
                                                Map.of(type, 1)),
                                left);
                if (units.isEmpty()) {
                    continue;
                }
                long travel =
                        Travel.travelSeconds(
                                gameData,
                                world,
                                candidate.coordinates(),
                                village.coordinates(),
                                units);
                if (from == null || travel <= left) {
                    // the first village with defenders, unless a farther one lands in time
                    from = candidate;
                    fill = units;
                    seconds = travel;
                }
                if (travel <= left) {
                    break;
                }
            }
        } else {
            still =
                    new ResourceAmounts(
                            Math.max(0, request.asked().wood() - request.delivered().wood()),
                            Math.max(0, request.asked().clay() - request.delivered().clay()),
                            Math.max(0, request.asked().iron() - request.delivered().iron()));
            from =
                    nearest.stream()
                            .filter(v -> market.freeMerchants(v, now) > 0)
                            .findFirst()
                            .orElse(null);
            if (from != null) {
                seconds =
                        Merchants.travelSeconds(
                                from.coordinates().distanceTo(village.coordinates()), world);
            }
        }
        NpcHelpSettings worth = requests.settings();
        // out of his reach (nothing he sends lands in time): a no costs him nothing (review)
        boolean far = requests.outOfReach(request);
        return new HelpRequestDto(
                request.id(),
                request.kind(),
                request.lordId(),
                players.findById(request.lordId()).map(Player::getName).orElse(""),
                VillageRefDto.of(village),
                request.defenders(),
                request.goal(),
                still,
                request.createdAt(),
                request.deadline(),
                letter(request),
                from == null ? null : VillageRefDto.of(from),
                fill,
                seconds,
                worth.answered(),
                far ? 0 : worth.declined(),
                far ? 0 : worth.ignored());
    }

    /** The request's letter as the player got it (its voice included). */
    private LordLetter letter(Request request) {
        return jdbc.sql(
                        """
                        select letter::text from private_message
                        where sender_id = :lord and recipient_id = :player
                          and letter ->> 'request' = cast(:id as text)
                        order by id desc limit 1
                        """)
                .param("lord", request.lordId())
                .param("player", request.playerId())
                .param("id", request.id())
                .query(String.class)
                .optional()
                .map(json -> JSON.readValue(json, LordLetter.class))
                .orElse(null);
    }
}
