package de.bannerfall.push;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The Notifications page (1.5).
 *
 * @param available whether this world sends push at all (it has keys)
 * @param publicKey the world's key for {@code pushManager.subscribe}; null when not available
 * @param devices how many devices of the player get push
 * @param kinds every kind with the player's switch, in the page's order
 */
public record PushSettingsDto(
        boolean available,
        @Schema(nullable = true) String publicKey,
        int devices,
        List<PushKindDto> kinds) {}
