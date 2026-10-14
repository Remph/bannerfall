package de.bannerfall.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The one cross-origin door of the API (M19): the landing page bannerfall.de reads {@code GET
 * /api/world/status} of the worlds (such as play.example.com) to show the player counts. Only that
 * path, only GET, no cookies, and only from the origins in {@code
 * bannerfall.status.allowed-origins} (empty = no origin: the app answers the same-origin login page
 * only, as before M19). Everything else keeps the browser's same-origin rule.
 */
@Configuration
class StatusCors {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${bannerfall.status.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(origins);
            configuration.setAllowedMethods(List.of("GET"));
            configuration.setAllowCredentials(false);
            configuration.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/world/status", configuration);
        }
        return source;
    }
}
