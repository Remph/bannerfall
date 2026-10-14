package de.bannerfall.unit;

import de.bannerfall.village.Village;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

/**
 * Units of one home village standing in one location. {@code home == location} is the village's own
 * army at home; every village has that stack from its creation on. Support stacks (M7) are deleted
 * when they become empty.
 */
@Entity
public class UnitStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The village the units belong to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_village_id")
    private Village homeVillage;

    /** Where they stand. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_village_id")
    private Village locationVillage;

    @Embedded private UnitCounts units;

    @Version private long version;

    protected UnitStack() {}

    public UnitStack(Village homeVillage, Village locationVillage) {
        this.homeVillage = homeVillage;
        this.locationVillage = locationVillage;
        this.units = new UnitCounts();
    }

    /** The empty home stack of a new village. */
    public static UnitStack homeOf(Village village) {
        return new UnitStack(village, village);
    }

    public Long getId() {
        return id;
    }

    public Village getHomeVillage() {
        return homeVillage;
    }

    public Village getLocationVillage() {
        return locationVillage;
    }

    /**
     * True for the village's own army at home. Comparing the objects is safe: a persistence context
     * holds exactly one object (entity or proxy) per village, so both fields point to the same one
     * when home and location are the same village.
     */
    public boolean isHome() {
        return homeVillage == locationVillage;
    }

    /** Live: changes are saved with the stack. */
    public UnitCounts getUnits() {
        return units;
    }
}
