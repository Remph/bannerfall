package de.bannerfall.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Rules from docs/plan/03-data-model.md (account). The web client checks the same rules. */
public record RegisterRequest(
        @NotNull @Pattern(
                        regexp = "[A-Za-z0-9_-]{3,24}",
                        message = "3 to 24 characters: letters, digits, _ and -")
                String username,
        @NotNull @Size(min = AccountService.PASSWORD_MIN, max = AccountService.PASSWORD_MAX) String password,
        @NotBlank @Size(max = 64) String inviteCode) {}
