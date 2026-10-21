package de.bannerfall.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/me/setup-claim} (1.6): the code from {@code bannerfall-admin setup}. */
public record SetupClaimRequest(@NotBlank @Size(max = 64) String code) {}
