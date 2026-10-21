package de.bannerfall.automation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * The build plan a village works through (02-game-rules §25, 1.6). Changed only under the village's
 * lock; dropped when the village changes owner.
 */
@Entity
public class VillageAutomation {

    @Id private Long villageId;

    @Enumerated(EnumType.STRING)
    private BuildPlanLetter planLetter;

    private boolean enabled;

    /**
     * The owner cancelled one of the automation's orders or demolished a level its plan builds, so
     * it stopped by itself.
     */
    private boolean pausedByCancel;

    protected VillageAutomation() {}

    VillageAutomation(long villageId, BuildPlanLetter planLetter, boolean enabled) {
        this.villageId = villageId;
        this.planLetter = planLetter;
        this.enabled = enabled;
    }

    /** The owner picks a plan or switches it on or off; a pause by a cancel ends either way. */
    void set(BuildPlanLetter planLetter, boolean enabled) {
        this.planLetter = planLetter;
        this.enabled = enabled;
        this.pausedByCancel = false;
    }

    /**
     * The owner cancelled an order of the automation or demolished a level of its plan: it stops
     * until he switches it on again.
     */
    void pauseByCancel() {
        if (enabled) {
            enabled = false;
            pausedByCancel = true;
        }
    }

    public Long getVillageId() {
        return villageId;
    }

    public BuildPlanLetter getPlanLetter() {
        return planLetter;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPausedByCancel() {
        return pausedByCancel;
    }
}
