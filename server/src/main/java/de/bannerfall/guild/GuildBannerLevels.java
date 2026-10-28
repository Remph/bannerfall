package de.bannerfall.guild;

import de.bannerfall.player.Player;
import de.bannerfall.rules.GuildEffect;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * What the guild banners do for a village owner right now (02-game-rules §16.4): the lookup the
 * economy and battle code calls through {@code VillageBanners}. It costs no query while the world
 * has guild banners off, for villages of nobody and of NPC lords; for a human's village it is one
 * indexed read. Kept apart from {@link GuildBannerService} (which locks villages and so depends on
 * the village code) so that the village code can use it.
 */
@Component
public class GuildBannerLevels {

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final GuildBannerCatalog catalog;

    GuildBannerLevels(JdbcClient jdbc, WorldService worldService, GuildBannerCatalog catalog) {
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.catalog = catalog;
    }

    /** What the banner of {@code owner}'s guild adds to his villages; none for no guild. */
    public GuildEffect effectOf(Player owner) {
        if (owner == null || owner.isNpc() || !on(worldService.settings())) {
            return GuildEffect.NONE;
        }
        return catalog.effect(
                jdbc.sql(
                                """
                                select b.level from guild_banner b
                                join guild_member m on m.guild_id = b.guild_id
                                where m.player_id = :player
                                """)
                        .param("player", owner.getId())
                        .query(Integer.class)
                        .optional()
                        .orElse(0));
    }

    /**
     * The travel time bonus of a support sent from one player's village to another's (level 2): 0
     * unless both are in the same guild with a banner of level 2 or more, and they are two players.
     */
    public double supportHaste(Player from, Player to) {
        if (from == null
                || to == null
                || from.getId().equals(to.getId())
                || from.isNpc()
                || to.isNpc()
                || !on(worldService.settings())) {
            return 0;
        }
        int level =
                jdbc.sql(
                                """
                                select b.level from guild_banner b
                                join guild_member f on f.guild_id = b.guild_id
                                join guild_member t on t.guild_id = b.guild_id
                                where f.player_id = :from and t.player_id = :to
                                """)
                        .param("from", from.getId())
                        .param("to", to.getId())
                        .query(Integer.class)
                        .optional()
                        .orElse(0);
        return catalog.effect(level).support();
    }

    /** Whether guild banners work in the world: they need guilds as well. */
    static boolean on(WorldSettings settings) {
        return settings.guildsEnabled() && settings.guildBannersEnabled();
    }
}
