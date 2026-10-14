package de.bannerfall.npc;

/**
 * The world's NPC difficulty ({@code world_settings.npc_difficulty}). EASY, NORMAL and HARD come
 * from {@code gamedata/npc/balance.yml}, CUSTOM from {@code world_settings.npc_custom} (09 §8).
 */
public enum NpcDifficulty {
    EASY,
    NORMAL,
    HARD,
    CUSTOM
}
