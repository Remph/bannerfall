package de.bannerfall.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A device signing up or refreshing its subscription, with the game's language on it. */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1000) String endpoint,
        @NotNull @Valid PushKeysDto keys,
        @NotBlank @Size(max = 2) String language) {}
