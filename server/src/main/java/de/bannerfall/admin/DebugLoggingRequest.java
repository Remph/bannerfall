package de.bannerfall.admin;

import jakarta.validation.constraints.NotNull;

/** PUT /api/admin/debug-logging (1.6.3, 08-host-console §2a). */
public record DebugLoggingRequest(@NotNull Boolean enabled) {}
