package de.bannerfall.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The keys of the browser's {@code PushSubscription.toJSON()}, base64url. */
public record PushKeysDto(
        @NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 50) String auth) {}
