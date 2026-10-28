package de.bannerfall;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TestcontainersConfiguration.class);

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

    /**
     * The simulation (-PsimDirectDatabase, see {@code simulationTest}) talks to the database
     * container by its own address: the port Testcontainers publishes on the host goes through
     * Docker's port proxy, and the simulation's hundreds of thousands of small statements wait on
     * that. Where the address cannot be reached from the test (Docker Desktop on Windows or macOS)
     * it uses the published port as usual.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "simulation.directDatabase", havingValue = "true")
    JdbcConnectionDetails directJdbcConnectionDetails(PostgreSQLContainer container) {
        String address = container.getContainerInfo().getNetworkSettings().getIpAddress();
        boolean direct = reachable(address);
        String url =
                direct
                        ? "jdbc:postgresql://"
                                + address
                                + ":"
                                + PostgreSQLContainer.POSTGRESQL_PORT
                                + "/"
                                + container.getDatabaseName()
                        : container.getJdbcUrl();
        LOG.info(
                "Simulation database: {}",
                direct ? "container address " + address : "published port (address not reachable)");
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return container.getUsername();
            }

            @Override
            public String getPassword() {
                return container.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return url;
            }
        };
    }

    private static boolean reachable(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(address, PostgreSQLContainer.POSTGRESQL_PORT), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
