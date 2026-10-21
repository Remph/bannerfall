package de.bannerfall.account;

/**
 * POST /api/me/respawn (optional body, 1.6): {@code nearGuild} starts the new village next to a
 * guild mate's (02-game-rules §2), where {@code MeDto.respawnNearGuild} said that is possible; when
 * it no longer is (the mate lost his villages meanwhile), or the body is left out, the village goes
 * to a player spot as before.
 */
public record RespawnRequest(boolean nearGuild) {}
