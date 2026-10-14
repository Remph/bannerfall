package de.bannerfall.gamedata;

/** The three resources. Order = columns everywhere (wood, clay, iron). */
public enum ResourceType {
    WOOD(BuildingType.WOODCUTTER),
    CLAY(BuildingType.CLAY_PIT),
    IRON(BuildingType.IRON_MINE);

    private final BuildingType producer;

    ResourceType(BuildingType producer) {
        this.producer = producer;
    }

    /** The building that produces this resource (02-game-rules §3). */
    public BuildingType producer() {
        return producer;
    }
}
