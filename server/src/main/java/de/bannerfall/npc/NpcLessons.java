package de.bannerfall.npc;

import de.bannerfall.gamedata.GameData;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Where a learning lord's lessons come from (09-npc-lords §15): its own tally, its guild mates' and
 * the human wins of the world, read once for a round (and for the admin list).
 */
@Component
class NpcLessons {

    private final NpcBrainRepository brains;
    private final NpcWorld world;
    private final NpcBalance balance;
    private final NpcTemplates templates;
    private final GameData gameData;

    NpcLessons(
            NpcBrainRepository brains,
            NpcWorld world,
            NpcBalance balance,
            NpcTemplates templates,
            GameData gameData) {
        this.brains = brains;
        this.world = world;
        this.balance = balance;
        this.templates = templates;
        this.gameData = gameData;
    }

    /** The human wins of the last {@code window-hours} real hours, faded to {@code now}. */
    NpcLearning.Wins wins(Instant now) {
        NpcBalanceSettings.Learning learning = balance.general().learning();
        Instant since = now.minusSeconds(Math.round(learning.windowHours() * 3600));
        return NpcLearning.winsOf(world.humanWins(since), now, learning.halfLifeHours(), gameData);
    }

    /** What the lord learned up to {@code now}, with its guild mates' tallies at half weight. */
    NpcLearning.Lesson of(
            NpcBrain brain, Collection<Long> mates, NpcLearning.Wins wins, Instant now) {
        List<NpcMemory.Tally> mateTallies =
                mates.isEmpty()
                        ? List.of()
                        : brains.findAllById(mates).stream()
                                .map(b -> b.getMemory().lessons().seen())
                                .toList();
        return NpcLearning.lesson(
                brain.getMemory().lessons().seen(),
                mateTallies,
                wins,
                Set.copyOf(templates.of(brain.getPersonality()).attackUnits()),
                now,
                balance.general().learning());
    }
}
