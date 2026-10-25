package de.bannerfall.admin;

/**
 * The answer of {@code PUT /api/admin/debug-logging} (1.6.3).
 *
 * @param enabled the game's code ({@code de.bannerfall}) logs at DEBUG until the server restarts
 */
public record DebugLoggingDto(boolean enabled) {}
