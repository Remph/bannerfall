package de.bannerfall.common;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * The release this server was built as ({@code docker build --build-arg VERSION=1.1.0}, see the
 * Dockerfile): one source for the host console and the version tag next to the server time (M28).
 * Without build info (a test or IDE run before the first Gradle build) it is {@code unknown}; a
 * local Gradle build is {@code 0.0.1-SNAPSHOT}, which the client shows as a development build.
 */
@Component
public class AppVersion {

    static final String UNKNOWN = "unknown";

    private final String value;

    AppVersion(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        this.value = properties == null ? UNKNOWN : properties.getVersion();
    }

    public String get() {
        return value;
    }
}
