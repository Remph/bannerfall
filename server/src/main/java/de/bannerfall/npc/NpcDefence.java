package de.bannerfall.npc;

import de.bannerfall.banner.BannerCatalog;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.movement.CommandKind;
import de.bannerfall.movement.CommandPreviewDto;
import de.bannerfall.movement.CommandRequest;
import de.bannerfall.movement.CommandService;
import de.bannerfall.movement.MovementKind;
import de.bannerfall.movement.MovementRepository;
import de.bannerfall.npc.NpcWorld.Incoming;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.rules.Loyalty;
import de.bannerfall.unit.UnitService;
import de.bannerfall.unit.UnitStack;
import de.bannerfall.unit.UnitStackRepository;
import de.bannerfall.village.Village;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Defence and support (09-npc-lords §6.5), the first step of a village's turn: help the lord's
 * other villages under attack, and bring the lord's own supports in this village home once no
 * attack is due here. Only through {@link CommandService}; every support is previewed first.
 */
@Component
class NpcDefence {

    private final CommandService commands;
    private final UnitService unitService;
    private final UnitStackRepository stacks;
    private final MovementRepository movements;
    private final BannerCatalog banners;

    NpcDefence(
            CommandService commands,
            UnitService unitService,
            UnitStackRepository stacks,
            MovementRepository movements,
            BannerCatalog banners) {
        this.commands = commands;
        this.unitService = unitService;
        this.stacks = stacks;
        this.movements = movements;
        this.banners = banners;
    }

    /** Below this loyalty the lord's supports stay in its own village (v1.3, {@link #withdraw}). */
    static final double SETTLED_LOYALTY = 50;

    void run(NpcTurn turn) {
        boolean threatenedHere =
                turn.view.incoming().stream()
                        .anyMatch(
                                a ->
                                        a.targetVillageId() == turn.villageId()
                                                && isThreat(turn, a, maxHaste(turn)));
        turn.threatened = threatenedHere;
        if (threatenedHere) {
            return; // a village under attack keeps its units and its supports
        }
        help(turn);
        withdraw(turn);
        withdrawFromOthers(turn);
    }

    /**
     * A threat: a foreign attack arriving within {@code support-horizon-hours ÷ speed} that isn't
     * only scouts (detected by its speed; {@code maxHaste} is the strongest Haste banner an attack
     * could fly, {@link #maxHaste}).
     */
    static boolean isThreat(NpcTurn turn, Incoming attack, double maxHaste) {
        Instant now = turn.now;
        Duration horizon =
                NpcTargeting.scaled(
                        Duration.ofSeconds(
                                Math.round(turn.view.general().supportHorizonHours() * 3600)),
                        turn.speed());
        return attack.arriveAt().isAfter(now)
                && !attack.arriveAt().isAfter(now.plus(horizon))
                && !NpcTargeting.onlyScouts(
                        attack.departAt(),
                        attack.arriveAt(),
                        attack.distance(),
                        turn.speed(),
                        turn.view.world().unitSpeed(),
                        maxHaste);
    }

    /**
     * The strongest Haste banner an attack could come with (02-game-rules §21.2), 0 while banners
     * are off: a hasted army walks faster, so light cavalry under Haste would look like scouts
     * (code review 1.2, D5).
     */
    double maxHaste(NpcTurn turn) {
        return turn.view.world().bannersEnabled()
                ? banners.effect(BannerType.HASTE, BannerCatalog.TIERS)
                : 0;
    }

    /**
     * Each threat against another village of the lord gets {@code support-share} of each {@code
     * support-units} type at home here, once per attack and helping village, if it arrives before
     * the attack. The village never has more than {@code support-share} of its units out in support
     * (standing elsewhere or on the way): a human who sends a string of cheap fake attacks can't
     * pull a lord's army around (M12c review, 09 §6.8).
     */
    private void help(NpcTurn turn) {
        double share = turn.template().supportShare();
        if (share <= 0) {
            return;
        }
        double maxHaste = maxHaste(turn);
        for (Incoming attack : turn.view.incoming()) {
            if (attack.targetVillageId() == turn.villageId()
                    || !isThreat(turn, attack, maxHaste)
                    || turn.memory.supported.contains(
                            new NpcMemory.Supported(attack.id(), turn.villageId()))) {
                continue;
            }
            MapVillage target = turn.view.villages().get(attack.targetVillageId());
            if (target == null || !turn.ownOrMate(target.playerId())) {
                continue; // the lord's own villages and its guild mates' (09 §13.3)
            }
            Map<UnitType, Integer> home = unitService.homeUnits(turn.villageId()).toMap();
            Map<UnitType, Integer> units =
                    withinBudget(
                            NpcTargeting.shareOf(home, turn.template().supportUnits(), share),
                            home,
                            supportOut(turn.villageId()),
                            share);
            if (units.isEmpty()) {
                continue;
            }
            CommandRequest request =
                    new CommandRequest(CommandKind.SUPPORT, target.coordinates(), units, null);
            CommandPreviewDto preview = commands.preview(turn.villageId(), turn.lordId(), request);
            if (!preview.errors().isEmpty() || !preview.arriveAt().isBefore(attack.arriveAt())) {
                continue;
            }
            commands.send(turn.villageId(), turn.lordId(), request);
            turn.memory.supported.add(new NpcMemory.Supported(attack.id(), turn.villageId()));
        }
    }

    /**
     * The units of this village that are out in support: standing in other villages and walking
     * there.
     */
    private Map<UnitType, Integer> supportOut(long villageId) {
        Map<UnitType, Integer> out = new EnumMap<>(UnitType.class);
        for (UnitStack stack : stacks.findByHomeVillageId(villageId)) {
            if (!stack.isHome()) {
                stack.getUnits()
                        .toMap()
                        .forEach((type, count) -> out.merge(type, count, Integer::sum));
            }
        }
        movements.findByHomeVillageId(villageId).stream()
                .filter(m -> m.getKind() == MovementKind.SUPPORT)
                .forEach(
                        m ->
                                m.getUnits()
                                        .toMap()
                                        .forEach(
                                                (type, count) ->
                                                        out.merge(type, count, Integer::sum)));
        return out;
    }

    /**
     * {@code wanted} cut down so that, together with what is out already, at most {@code share} of
     * each type (home plus out) is in support.
     */
    static Map<UnitType, Integer> withinBudget(
            Map<UnitType, Integer> wanted,
            Map<UnitType, Integer> home,
            Map<UnitType, Integer> out,
            double share) {
        Map<UnitType, Integer> result = new EnumMap<>(UnitType.class);
        wanted.forEach(
                (type, count) -> {
                    int outCount = out.getOrDefault(type, 0);
                    int total = home.getOrDefault(type, 0) + outCount;
                    int budget = (int) Math.floor(total * share) - outCount;
                    int send = Math.min(count, budget);
                    if (send > 0) {
                        result.put(type, send);
                    }
                });
        return result;
    }

    /**
     * The supports of this village standing in other players' villages (09 §13.3): in a guild
     * mate's village they go home once no foreign attack is due there within the horizon and its
     * loyalty is at least {@link #SETTLED_LOYALTY}; anywhere else (a lord that is no guild mate any
     * more) at once. The villages they stand in were locked with this one at the start of the turn
     * ({@code NpcRound}).
     */
    private void withdrawFromOthers(NpcTurn turn) {
        double maxHaste = maxHaste(turn);
        for (UnitStack stack : stacks.findByHomeVillageId(turn.villageId())) {
            Village location = stack.getLocationVillage();
            if (stack.isHome()
                    || stack.getUnits().isEmpty()
                    || !turn.locked.contains(location.getId())
                    || (location.getPlayer() != null
                            && location.getPlayer().getId() == turn.lordId())) {
                continue;
            }
            Long owner = location.getPlayer() == null ? null : location.getPlayer().getId();
            if (owner != null && turn.view.guildMates().contains(owner)) {
                MapVillage there = turn.view.villages().get(location.getId());
                boolean due =
                        turn.view.incoming().stream()
                                .anyMatch(
                                        a ->
                                                a.targetVillageId() == location.getId()
                                                        && isThreat(turn, a, maxHaste));
                if (due || there == null || there.loyalty() < SETTLED_LOYALTY) {
                    continue;
                }
            }
            commands.returnStack(stack.getId(), turn.lordId(), stack.getUnits().toMap());
        }
    }

    /**
     * The lord's own support stacks standing here go home: no attack is due here (§6.5). Not while
     * the village's loyalty is below {@link #SETTLED_LOYALTY} (v1.3): after a conquest (loyalty 25)
     * the survivors of the noble attack stay until it has grown back to 50, so the new village
     * isn't left empty in its first quiet turn.
     */
    private void withdraw(NpcTurn turn) {
        Village village = turn.village;
        double loyalty =
                Loyalty.regen(
                        village.getLoyalty(),
                        Loyalty.regenPerHour(turn.view.world()),
                        village.getLoyaltyUpdatedAt(),
                        turn.now);
        if (loyalty < SETTLED_LOYALTY) {
            return;
        }
        List<UnitStack> here = stacks.findByLocationVillageId(turn.villageId());
        for (UnitStack stack : here) {
            long homeId = stack.getHomeVillage().getId();
            if (stack.isHome()
                    || !turn.locked.contains(homeId)
                    || stack.getUnits().isEmpty()
                    || stack.getHomeVillage().getPlayer() == null
                    || stack.getHomeVillage().getPlayer().getId() != turn.lordId()) {
                continue;
            }
            commands.returnStack(stack.getId(), turn.lordId(), stack.getUnits().toMap());
        }
    }
}
