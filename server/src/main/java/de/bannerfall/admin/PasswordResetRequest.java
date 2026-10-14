package de.bannerfall.admin;

import de.bannerfall.account.AccountService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/players/{id}/reset-password. Without a password (or an empty body) the server
 * makes one up and answers it once.
 */
public record PasswordResetRequest(
        @Schema(nullable = true)
                @Size(min = AccountService.PASSWORD_MIN, max = AccountService.PASSWORD_MAX) String password) {}
