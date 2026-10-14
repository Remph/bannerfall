package de.bannerfall.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/me/password. The new password follows the registration rules. */
public record ChangePasswordRequest(
        @NotNull @Size(max = AccountService.PASSWORD_MAX) String currentPassword,
        @NotNull @Size(min = AccountService.PASSWORD_MIN, max = AccountService.PASSWORD_MAX) String newPassword) {}
