package de.bannerfall.guild;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/guilds/mine/invitations}: the player's name, case does not matter. */
public record InviteRequest(@NotBlank @Size(max = 64) String playerName) {}
