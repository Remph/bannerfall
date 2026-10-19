package de.bannerfall.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A device leaving: turned off, or logging out. */
public record PushUnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {}
