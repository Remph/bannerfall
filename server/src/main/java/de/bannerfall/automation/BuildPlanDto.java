package de.bannerfall.automation;

import java.util.List;

/** A build plan (04-api <i>Village automation</i>, 1.6). */
public record BuildPlanDto(BuildPlanLetter letter, String name, List<BuildPlanLine> lines) {

    static BuildPlanDto of(BuildPlan plan) {
        return new BuildPlanDto(plan.getLetter(), plan.getName(), plan.getLines());
    }
}
