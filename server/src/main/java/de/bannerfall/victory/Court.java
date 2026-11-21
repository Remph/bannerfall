package de.bannerfall.victory;

import de.bannerfall.chronicle.Chronicle;
import de.bannerfall.chronicle.ChronicleFacts;
import de.bannerfall.chronicle.ChronicleKind;
import de.bannerfall.chronicle.ChronicleSide;
import de.bannerfall.message.LordLetter;
import de.bannerfall.message.MessageService;
import de.bannerfall.notify.GameNotification;
import de.bannerfall.notify.PlayerNotification;
import de.bannerfall.npc.CourtNames;
import de.bannerfall.world.WorldService;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The court of a solo world (2.1 S2b, 12-solo-and-allies §6, I4): the lord guild with the most
 * points. It gets a name from its guild name ({@link CourtNames}) and shows on the diplomacy page,
 * in the victory strip and in the victory clock's notice; the victory check tells every human when
 * another lord guild takes its place ({@code COURT_CHANGED}). No new way to win: the court is a
 * guild like any other side.
 */
@Component
public class Court {

    private static final Logger log = LoggerFactory.getLogger(Court.class);

    private final JdbcClient jdbc;
    private final WorldService worldService;
    private final StandingsQuery standingsQuery;
    private final ApplicationEventPublisher events;
    private final MessageService messages;
    private final Chronicle chronicle;

    Court(
            JdbcClient jdbc,
            WorldService worldService,
            StandingsQuery standingsQuery,
            ApplicationEventPublisher events,
            MessageService messages,
            Chronicle chronicle) {
        this.chronicle = chronicle;
        this.jdbc = jdbc;
        this.worldService = worldService;
        this.standingsQuery = standingsQuery;
        this.events = events;
        this.messages = messages;
    }

    /**
     * The court started the victory clock (2.1 S4, 12 §9): its strongest lord taunts every human
     * outside it by letter ("By 12 December the land is ours"). No message notice: the clock's own
     * notice tells them, and pushes.
     */
    void taunt(VictorySide leader, Instant winsAt, List<Long> humans) {
        if (!exists() || leader == null || leader.court() == null || winsAt == null) {
            return;
        }
        Long lord =
                jdbc.sql(
                                """
                                select m.player_id from guild_member m
                                join player p on p.id = m.player_id
                                where m.guild_id = :guild and p.npc
                                order by p.points desc, p.id limit 1
                                """)
                        .param("guild", leader.id())
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        if (lord == null) {
            return;
        }
        LordLetter letter =
                new LordLetter(
                        LordLetter.Kind.COURT_TAUNT,
                        leader.name(),
                        null,
                        null,
                        null,
                        winsAt,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        leader.court());
        for (long human : humans) {
            messages.sendLetter(lord, human, letter, false);
        }
    }

    /** A world has a court while it is a solo world with guilds on. */
    boolean exists() {
        WorldSettings world = worldService.settings();
        return world.soloMode() && world.guildsEnabled();
    }

    /**
     * The standings with the court's side marked ({@link VictorySide#court}): the lord guild with
     * the most points, ties as {@link Standings#BY_POINTS}. Unchanged outside a solo world or
     * without a lord guild.
     */
    Standings marked(Standings standings) {
        if (!exists()) {
            return standings;
        }
        Set<Long> lordGuilds = lordGuildIds();
        Optional<VictorySide> court =
                standings.sides().stream()
                        .filter(side -> side.kind() == SideKind.GUILD)
                        .filter(side -> lordGuilds.contains(side.id()))
                        .findFirst();
        if (court.isEmpty()) {
            return standings;
        }
        long id = court.get().id();
        List<VictorySide> sides =
                standings.sides().stream()
                        .map(
                                side ->
                                        side.kind() == SideKind.GUILD && side.id() == id
                                                ? mark(side)
                                                : side)
                        .toList();
        return new Standings(sides, standings.allVillages(), standings.allKeeps());
    }

    /** The court side among marked standings, if there is one. */
    static Optional<VictorySide> of(Standings marked) {
        return marked.sides().stream().filter(side -> side.court() != null).findFirst();
    }

    /**
     * After a victory check: keeps the court's guild and tells every human when another lord guild
     * became the court (the first court of a world comes without a notice). The caller holds the
     * settings row's lock (the victory check does).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void update(Standings marked, List<Long> humans) {
        if (!exists()) {
            return;
        }
        Optional<VictorySide> court = of(marked);
        Long stored =
                jdbc.sql("select court_guild_id from world_settings")
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        Long now = court.map(VictorySide::id).orElse(null);
        if (Objects.equals(stored, now)) {
            return;
        }
        jdbc.sql("update world_settings set court_guild_id = :guild").param("guild", now).update();
        VictorySide previous =
                stored == null
                        ? null
                        : marked.sides().stream()
                                .filter(
                                        side ->
                                                side.kind() == SideKind.GUILD
                                                        && side.id() == stored)
                                .findFirst()
                                .map(this::mark)
                                .orElse(null);
        if (court.isPresent()) {
            // the chronicle tells the court's rise too, not only a change (2.1 I1)
            chronicle.recordNow(
                    ChronicleKind.COURT,
                    ChronicleFacts.empty()
                            .withSide(ChronicleSide.of(court.get()))
                            .withOther(ChronicleSide.of(previous)));
        }
        if (stored == null || court.isEmpty()) {
            return;
        }
        log.info("The court passed from guild {} to guild {}", stored, now);
        var notification = new GameNotification.CourtChanged(court.get(), previous);
        for (long human : humans) {
            events.publishEvent(new PlayerNotification(human, notification));
        }
    }

    /**
     * The race of the diplomacy page (12 §7): the court's side and the player's own (his guild's
     * while guilds are on, else himself), both empty if they own no village.
     */
    public Race race(long playerId) {
        WorldSettings world = worldService.settings();
        Standings standings = marked(standingsQuery.read(world.guildsEnabled()));
        Long guildId =
                jdbc.sql("select guild_id from guild_member where player_id = :player")
                        .param("player", playerId)
                        .query(Long.class)
                        .optional()
                        .orElse(null);
        Optional<VictorySide> mine =
                standings.sides().stream()
                        .filter(
                                side ->
                                        world.guildsEnabled() && guildId != null
                                                ? side.kind() == SideKind.GUILD
                                                        && side.id() == guildId
                                                : side.kind() == SideKind.PLAYER
                                                        && side.id() == playerId)
                        .findFirst();
        return new Race(of(standings).orElse(null), mine.orElse(null), standings.allVillages());
    }

    /**
     * @param court the court's side, null without one
     * @param mine the player's side, null while it owns no village
     * @param allVillages every village on the map (the domination share counts against it)
     */
    public record Race(VictorySide court, VictorySide mine, int allVillages) {}

    private VictorySide mark(VictorySide side) {
        return side.asCourt(
                CourtNames.of(side.name())
                        .map(c -> new CourtRefDto(c.key(), c.numeral()))
                        .orElse(new CourtRefDto(null, null)));
    }

    /** The guilds whose members are all NPC lords. */
    private Set<Long> lordGuildIds() {
        return new HashSet<>(
                jdbc.sql(
                                """
                                select m.guild_id from guild_member m
                                join player p on p.id = m.player_id
                                group by m.guild_id having bool_and(p.npc)
                                """)
                        .query(Long.class)
                        .list());
    }
}
