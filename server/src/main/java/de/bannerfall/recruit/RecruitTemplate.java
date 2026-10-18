package de.bannerfall.recruit;

import de.bannerfall.unit.UnitCounts;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A recruit template of a player (02-game-rules §5.2b, 1.4): a name and the army a village should
 * have, shared by all his villages.
 */
@Entity
public class RecruitTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long playerId;

    @Enumerated(EnumType.STRING)
    private RecruitTemplateLetter letter;

    private String name;

    @Embedded private UnitCounts units;

    protected RecruitTemplate() {}

    RecruitTemplate(long playerId, RecruitTemplateLetter letter, String name, UnitCounts units) {
        this.playerId = playerId;
        this.letter = letter;
        this.name = name;
        this.units = units;
    }

    void change(String name, UnitCounts units) {
        this.name = name;
        this.units = units;
    }

    public RecruitTemplateLetter getLetter() {
        return letter;
    }

    public String getName() {
        return name;
    }

    public UnitCounts getUnits() {
        return units;
    }
}
