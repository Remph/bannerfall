package de.bannerfall.automation;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A build plan of a player (02-game-rules §25, 1.6): a name and an ordered list of lines, shared by
 * all his villages.
 */
@Entity
public class BuildPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long playerId;

    @Enumerated(EnumType.STRING)
    private BuildPlanLetter letter;

    private String name;

    @Convert(converter = BuildPlanLinesConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<BuildPlanLine> lines;

    protected BuildPlan() {}

    BuildPlan(long playerId, BuildPlanLetter letter, String name, List<BuildPlanLine> lines) {
        this.playerId = playerId;
        this.letter = letter;
        this.name = name;
        this.lines = List.copyOf(lines);
    }

    void change(String name, List<BuildPlanLine> lines) {
        this.name = name;
        this.lines = List.copyOf(lines);
    }

    public BuildPlanLetter getLetter() {
        return letter;
    }

    public String getName() {
        return name;
    }

    public List<BuildPlanLine> getLines() {
        return lines;
    }
}
