package de.bannerfall.village;

import de.bannerfall.gamedata.BuildingType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.EnumMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The building levels of a village: one {@code smallint} column per {@link BuildingType} ({@code
 * b_headquarters} ... {@code b_wall}).
 *
 * <p>{@code @JdbcTypeCode(SMALLINT)}: the fields are {@code int} for convenience, and without it
 * Hibernate's schema validation ({@code ddl-auto=validate}) rejects the {@code smallint} columns.
 */
@Embeddable
public class BuildingLevels {

    @Column(name = "b_headquarters")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int headquarters;

    @Column(name = "b_barracks")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int barracks;

    @Column(name = "b_stable")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int stable;

    @Column(name = "b_workshop")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int workshop;

    @Column(name = "b_academy")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int academy;

    @Column(name = "b_smithy")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int smithy;

    @Column(name = "b_rally_point")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int rallyPoint;

    @Column(name = "b_market")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int market;

    @Column(name = "b_woodcutter")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int woodcutter;

    @Column(name = "b_clay_pit")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int clayPit;

    @Column(name = "b_iron_mine")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int ironMine;

    @Column(name = "b_farm")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int farm;

    @Column(name = "b_warehouse")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int warehouse;

    @Column(name = "b_hiding_place")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int hidingPlace;

    @Column(name = "b_wall")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int wall;

    @Column(name = "b_banner_hall")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int bannerHall;

    /** All levels 0. */
    public BuildingLevels() {}

    public int get(BuildingType type) {
        return switch (type) {
            case HEADQUARTERS -> headquarters;
            case BARRACKS -> barracks;
            case STABLE -> stable;
            case WORKSHOP -> workshop;
            case ACADEMY -> academy;
            case SMITHY -> smithy;
            case RALLY_POINT -> rallyPoint;
            case MARKET -> market;
            case WOODCUTTER -> woodcutter;
            case CLAY_PIT -> clayPit;
            case IRON_MINE -> ironMine;
            case FARM -> farm;
            case WAREHOUSE -> warehouse;
            case HIDING_PLACE -> hidingPlace;
            case WALL -> wall;
            case BANNER_HALL -> bannerHall;
        };
    }

    /** Max levels are not checked here (no game data); the services do that. */
    public void set(BuildingType type, int level) {
        if (level < 0 || level > Short.MAX_VALUE) {
            throw new IllegalArgumentException("invalid level for " + type + ": " + level);
        }
        switch (type) {
            case HEADQUARTERS -> headquarters = level;
            case BARRACKS -> barracks = level;
            case STABLE -> stable = level;
            case WORKSHOP -> workshop = level;
            case ACADEMY -> academy = level;
            case SMITHY -> smithy = level;
            case RALLY_POINT -> rallyPoint = level;
            case MARKET -> market = level;
            case WOODCUTTER -> woodcutter = level;
            case CLAY_PIT -> clayPit = level;
            case IRON_MINE -> ironMine = level;
            case FARM -> farm = level;
            case WAREHOUSE -> warehouse = level;
            case HIDING_PLACE -> hidingPlace = level;
            case WALL -> wall = level;
            case BANNER_HALL -> bannerHall = level;
        }
    }

    public void add(BuildingType type, int levels) {
        set(type, get(type) + levels);
    }

    /** All levels in canonical order, for the rules classes and DTOs. */
    public Map<BuildingType, Integer> toMap() {
        var map = new EnumMap<BuildingType, Integer>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            map.put(type, get(type));
        }
        return map;
    }
}
