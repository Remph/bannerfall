package de.bannerfall;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Throw-away database: no fsync, so the commits of the event engine (and the NPC
        // simulation, which runs hundreds of thousands of them) don't wait for the disk.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
                .withCommand(
                        "postgres",
                        "-c",
                        "fsync=off",
                        "-c",
                        "synchronous_commit=off",
                        "-c",
                        "full_page_writes=off");
    }
}
