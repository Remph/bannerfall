package de.bannerfall.admin;

import de.bannerfall.account.Role;
import jakarta.validation.constraints.NotNull;

/** POST /api/admin/players/{id}/role. */
public record RoleChangeRequest(@NotNull Role role) {}
