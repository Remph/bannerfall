package de.bannerfall.npc;

import de.bannerfall.gamedata.UnitType;
import de.bannerfall.npc.NpcBalanceSettings.Difficulty;
import de.bannerfall.npc.NpcWorld.Incoming;
import de.bannerfall.npc.NpcWorld.MapVillage;
import de.bannerfall.village.Village;
import de.bannerfall.world.WorldSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One village's turn in a decision round (09-npc-lords §3): the village (locked), what the lord saw
 * at the start of the round, its memory being changed and the units still available to send.
 */
final class NpcTurn {

    /**
     * What the lord sees in one round, loaded once at its start.
     *
     * @param villages the map, by village id
     * @param incoming the foreign attacks on the way to the lord's villages
     * @param otherLordsToHuman per human player id: the commands all other lords sent to him in the
     *     cap window
     * @param lordPoints the lord's player points
     * @param braked the rubber band's brake (09 §8) from the last finish step
     * @param farmBudgetSpent the lord's loot of the last 24 real hours has reached its farm budget
     *     (09 §6.2, v1.3): no farms or raids this round
     */
    record View(
            long lordId,
            NpcPersonality personality,
            NpcTemplates.Template template,
            Difficulty difficulty,
            NpcBalanceSettings.Balance general,
            WorldSettings world,
            Map<Long, MapVillage> villages,
            List<Incoming> incoming,
            Map<Long, Integer> otherLordsToHuman,
            int lordPoints,
            boolean braked,
            boolean farmBudgetSpent) {}

    final View view;
    final Village village;
    final Instant now;
    final NpcMemory.Draft memory;

    /** The villages locked in this turn: this one and the homes of the lord's supports in it. */
    final Set<Long> locked;

    /** The targets of the lord's attacks on the way (filled as the turn sends more). */
    final Set<Long> ownTargets;

    /** The units this village may still send (home minus the home guard, §6). */
    Map<UnitType, Integer> available;

    /** Set by the warfare before the economy: this academy village wants nobles (§6.6). */
    boolean wantsNobles;

    /** Set by the defence: a foreign attack (not only scouts) is due here (§6.5). */
    boolean threatened;

    /**
     * Set by the retaliation (v1.3): this village waits for its attack units to come home for a
     * retaliation it can win with its whole army, so it doesn't farm with them meanwhile.
     */
    boolean gathering;

    NpcTurn(
            View view,
            Village village,
            Instant now,
            NpcMemory.Draft memory,
            Set<Long> locked,
            Set<Long> ownTargets) {
        this.view = view;
        this.village = village;
        this.now = now;
        this.memory = memory;
        this.locked = locked;
        this.ownTargets = ownTargets;
    }

    long lordId() {
        return view.lordId();
    }

    long villageId() {
        return village.getId();
    }

    NpcTemplates.Template template() {
        return view.template();
    }

    double speed() {
        return view.world().speed();
    }

    /** The grudge against {@code playerId} is active. */
    boolean grudgeAgainst(Long playerId) {
        if (playerId == null) {
            return false;
        }
        NpcMemory.Grudge grudge = memory.grudges.get(playerId);
        return grudge != null && grudge.until().isAfter(now);
    }

    /** The distance from this village to {@code target}, in fields. */
    double distanceTo(MapVillage target) {
        return village.coordinates().distanceTo(target.coordinates());
    }
}
