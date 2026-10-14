package de.bannerfall.config;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Caching of the web app (served from {@code static/} in the production image, 01-architecture §8).
 *
 * <p>Vite gives every file in {@code /assets/} a name with the hash of its content, so a browser
 * may keep it for good ("immutable"); a new version has new names. {@code index.html} is {@code
 * no-cache} (the browser asks again at every load), and everything else (icons, the manifest) keeps
 * Spring Security's default {@code no-cache, no-store}. A file that does not exist gets no header:
 * a missing asset during an update is not cached for a year.
 */
@Configuration
class StaticResourcesConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        // The page itself (also what SpaForwardingController forwards to) is checked at every load:
        // without a header a browser may keep it for a while and ask for assets a new image no
        // longer has.
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}
