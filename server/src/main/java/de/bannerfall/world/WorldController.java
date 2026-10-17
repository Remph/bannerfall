package de.bannerfall.world;

import de.bannerfall.common.AppVersion;
import de.bannerfall.notify.PlayerConnections;
import de.bannerfall.player.PlayerRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class WorldController {

    private final WorldService worldService;
    private final PlayerRepository players;
    private final PlayerConnections connections;
    private final RegisteredPlayersCount registeredPlayers;
    private final AppVersion appVersion;
    private final WorldOperator operator;

    WorldController(
            WorldService worldService,
            PlayerRepository players,
            PlayerConnections connections,
            RegisteredPlayersCount registeredPlayers,
            AppVersion appVersion,
            WorldOperator operator) {
        this.worldService = worldService;
        this.players = players;
        this.connections = connections;
        this.registeredPlayers = registeredPlayers;
        this.appVersion = appVersion;
        this.operator = operator;
    }

    @GetMapping("/world")
    @Transactional(readOnly = true)
    WorldDto world() {
        WorldSettings settings = worldService.settings();
        return new WorldDto(
                settings.name(),
                settings.speed(),
                settings.unitSpeed(),
                settings.mapSize(),
                settings.buildQueueSize(),
                settings.buildQueueMax(),
                settings.recruitQueueSize(),
                settings.tradeMaxRatio(),
                worldService.startedAt(),
                settings.startResources(),
                settings.luckRange(),
                settings.beginnerProtectionHours(),
                settings.nobleMaxDistance(),
                settings.loyaltyRegenPerHour(),
                settings.cancelWindowSeconds(),
                settings.barbarianGrowthEnabled(),
                settings.npcLordCount(),
                settings.npcDifficulty(),
                settings.guildsEnabled(),
                settings.guildMaxMembers(),
                Math.min(settings.npcGuildMaxMembers(), settings.guildMaxMembers()),
                settings.barbarianWallsEnabled(),
                settings.barbarianWallStartDays(),
                settings.barbarianWallMaxLevel(),
                settings.barbarianWallsRebuild(),
                settings.unitLevelsEnabled(),
                settings.paladinVillagesPerSlot(),
                settings.paladinMaxSlots(),
                settings.bannersEnabled(),
                settings.bannerDropFactor(),
                settings.marketExchangeRatio(),
                settings.victoryMode(),
                settings.dominationPercent(),
                settings.victoryHoldHours(),
                settings.keepCount(),
                settings.keepPercent(),
                worldService.endsAt(),
                worldService.endedAt(),
                worldService.phase(),
                worldService.opensAt(),
                players.countRegisteredHumans(),
                appVersion.get());
    }

    /**
     * Public (the login page and, since M19, the landing page on another origin, see {@code
     * StatusCors}): no login needed, nothing but the name, the phase, the time, two counts and the
     * version. No transaction on purpose: anybody may call this, so it must not take a database
     * connection per request ({@link RegisteredPlayersCount} counts at most every few seconds).
     */
    @GetMapping("/world/status")
    PublicWorldDto status() {
        return new PublicWorldDto(
                worldService.settings().name(),
                worldService.phase(),
                worldService.opensAt(),
                registeredPlayers.get(),
                connections.onlinePlayers(),
                appVersion.get());
    }

    /**
     * Public like the status: who runs this world, for the privacy page that guests open from the
     * login and register pages. Settings only, no database.
     */
    @GetMapping("/world/operator")
    OperatorDto operator() {
        return operator.get();
    }
}
