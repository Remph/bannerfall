package de.bannerfall.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/join (1.5): a player of the account server joins this world. */
public record JoinRequest(@NotBlank @Size(max = 64) String inviteCode) {}
