package de.bannerfall.farm;

import de.bannerfall.unit.UnitCounts;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A farm template of a player (02-game-rules §18, 1.4): the units a farm list entry sends, shared
 * by all his villages. Changed only under the locks of all his villages, since the entries that use
 * it change with it.
 */
@Entity
public class FarmTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long playerId;

    @Enumerated(EnumType.STRING)
    private FarmTemplateLetter letter;

    @Embedded private UnitCounts units;

    protected FarmTemplate() {}

    FarmTemplate(long playerId, FarmTemplateLetter letter, UnitCounts units) {
        this.playerId = playerId;
        this.letter = letter;
        this.units = units;
    }

    void setUnits(UnitCounts units) {
        this.units = units;
    }

    public Long getId() {
        return id;
    }

    public FarmTemplateLetter getLetter() {
        return letter;
    }

    public UnitCounts getUnits() {
        return units;
    }
}
