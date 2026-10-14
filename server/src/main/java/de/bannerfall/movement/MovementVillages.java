package de.bannerfall.movement;

/**
 * The villages a movement's arrival locks, read without loading the movement (see {@link
 * MovementRepository#findVillagesById}).
 */
public record MovementVillages(Long homeVillageId, Long targetVillageId) {}
