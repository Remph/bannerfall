package de.bannerfall.admin;

/**
 * {@code POST /api/admin/world-setup/done} (1.6): the host finished the guided setup ({@code
 * finished}) or skipped it; either way the pre-game page stops offering it.
 */
public record WorldSetupDoneRequest(boolean finished) {}
