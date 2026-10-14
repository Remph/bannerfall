package de.bannerfall.village;

import de.bannerfall.achievement.Achievement;
import de.bannerfall.achievement.AchievementService;
import de.bannerfall.common.Coordinates;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.GameRuleException;
import de.bannerfall.common.RandomSource;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.BuildRules;
import de.bannerfall.rules.MapPlacement;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Puts players' villages on the map (02-game-rules §2); barbarians: {@code BarbarianService}. */
@Service
public class SpawnService {

    static final int MAX_NAME_LENGTH = 32;
    private static final String NAME_SUFFIX = "'s village";

    private final VillageRepository villages;
    private final UnitStackRepository stacks;
    private final PlayerRepository players;
    private final WorldService worldService;
    private final GameData gameData;
    private final RandomSource random;
    private final Clock clock;
    private final AchievementService achievements;

    SpawnService(
            VillageRepository villages,
            UnitStackRepository stacks,
            PlayerRepository players,
            WorldService worldService,
            GameData gameData,
            RandomSource random,
            Clock clock,
            AchievementService achievements) {
        this.villages = villages;
        this.stacks = stacks;
        this.players = players;
        this.worldService = worldService;
        this.gameData = gameData;
        this.random = random;
        this.clock = clock;
        this.achievements = achievements;
    }

    /**
     * Creates the first village of a player at a free player spot: start levels from the game data,
     * {@code startResources} of each resource, full loyalty, an empty home stack. Updates the
     * player's points and village count, so {@code player} must be managed by the current
     * transaction.
     *
     * @throws GameRuleException {@code WORLD_FULL} if no free spot is found
     */
    @Transactional
    public Village createStartingVillage(Player player) {
        return tryCreateStartingVillage(player)
                .orElseThrow(
                        () ->
                                new GameRuleException(
                                        ErrorCode.WORLD_FULL, "No free spot for a new village"));
    }

    /**
     * Like {@link #createStartingVillage}, but answers empty instead of throwing when the map has
     * no room: for callers that want to go on (the NPC lords at world start) and can't afford the
     * rollback-only mark an exception leaves on the transaction.
     */
    @Transactional
    public Optional<Village> tryCreateStartingVillage(Player player) {
        worldService.lockMapForPlacement();
        WorldSettings world = worldService.settings();

        Set<Coordinates> occupied = new HashSet<>(villages.findAllCoordinates());
        int playerVillages = Math.toIntExact(villages.countByPlayerIsNotNull());
        Optional<Coordinates> found =
                MapPlacement.findPlayerSpot(
                        world.mapSize(), playerVillages, occupied::contains, random);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        BuildingLevels buildings = startLevels();
        int points = BuildRules.villagePoints(gameData, buildings.toMap());
        Village village =
                villages.save(
                        new Village(
                                player,
                                defaultName(player.getName()),
                                found.get(),
                                world.startResources(),
                                buildings,
                                points,
                                clock.instant()));
        stacks.save(UnitStack.homeOf(village));
        player.addVillage(points);
        achievements.checkState(player); // Landlord (02-game-rules §19)
        return Optional.of(village);
    }

    /**
     * A player who lost his last village starts again (02-game-rules §10): a new starting village
     * at a fresh player spot, like at registration, and new beginner protection from now on. No
     * barbarians are spawned around it (a lost village would otherwise bring fresh ones to farm).
     *
     * @throws GameRuleException {@code RESPAWN_NOT_ALLOWED} if the player still has a village or is
     *     an NPC lord; {@code WORLD_FULL} if no free spot is found
     */
    @Transactional
    public Village respawn(long playerId) {
        // The map lock first: two respawns of the same player at once wait here, and the second
        // one sees the village of the first.
        worldService.lockMapForPlacement();
        Player player = players.findById(playerId).orElseThrow();
        if (player.isNpc() || villages.countByPlayerId(playerId) > 0) {
            throw new GameRuleException(
                    ErrorCode.RESPAWN_NOT_ALLOWED, "Only a player without villages can respawn");
        }
        Duration protection = Duration.ofHours(worldService.settings().beginnerProtectionHours());
        player.protectUntil(clock.instant().plus(protection));
        Village village = createStartingVillage(player);
        achievements.grant(player, Achievement.PHOENIX);
        return village;
    }

    /**
     * "Ben's village"; just the player name if that would be too long for the column. Also the name
     * of a conquered barbarian village (02-game-rules §10).
     */
    public static String defaultName(String playerName) {
        String name = playerName + NAME_SUFFIX;
        return name.length() <= MAX_NAME_LENGTH ? name : playerName;
    }

    private BuildingLevels startLevels() {
        var levels = new BuildingLevels();
        for (BuildingType type : BuildingType.values()) {
            levels.set(type, gameData.building(type).startLevel());
        }
        return levels;
    }
}
