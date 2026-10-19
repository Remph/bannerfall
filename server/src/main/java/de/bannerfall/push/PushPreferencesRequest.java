package de.bannerfall.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Switches to change; kinds left out stay as they are. */
public record PushPreferencesRequest(
        @NotNull @Size(max = 20) List<@NotNull @Valid PushKindDto> kinds) {}
