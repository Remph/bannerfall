package de.bannerfall.unit;

/**
 * The villages of a stack, read without loading the stack (see {@link
 * UnitStackRepository#findVillagesById}).
 */
public record StackVillages(Long homeVillageId, Long locationVillageId) {}
