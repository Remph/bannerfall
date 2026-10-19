package de.bannerfall.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/me/link (1.5): linking ends the password here, so it is typed once more. */
public record LinkRequest(
        @NotNull @Size(max = AccountService.PASSWORD_MAX) String currentPassword) {}
