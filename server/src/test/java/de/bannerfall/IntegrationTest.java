package de.bannerfall;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application with a Testcontainers PostgreSQL, MockMvc and a {@link MutableClock}. All
 * integration tests use exactly this, so Spring starts one context (and one database) for all of
 * them. The database is shared: use unique names ({@link Browser#uniqueUsername()}).
 *
 * <p>The event worker does not run by itself (its scheduler is off); tests call {@code
 * EventProcessor.processDue(clock.instant())} after moving the clock. A {@link TestEventHandler}
 * handles events of kind TEST.
 *
 * <p>The dice are real unless a test fixes them ({@link TestRandomSource}).
 *
 * <p>The world starts empty: {@code WorldInitializer} is off and no barbarians spawn around new
 * players ({@link TestWorldConfiguration}).
 *
 * <p>The write rate limit, the automatic lock, the login and registration limits per address and
 * the session cap per account are as good as off (M14e, code review 1.2).
 *
 * <p>Password hashes take one PBKDF2 round instead of the server's 310,000 ({@link
 * TestPasswordEncoderConfiguration}).
 *
 * <p>The host token is {@link #ADMIN_TOKEN}; the host cheats are off (the {@code dev} profile of
 * {@code DevCheatControllerTest} switches them on).
 *
 * <p>The world has an account server ({@link FakeAccountServer}) in mode OFF ({@link
 * TestAccountServerConfiguration}).
 *
 * <p>Push notifications go only to the {@link FakePushService}, one at a time ({@link
 * TestPushConfiguration}).
 *
 * <p>The server also listens on a random port ({@code @LocalServerPort}) for the WebSocket tests,
 * which need a real connection; everything else goes through MockMvc. {@link SentMessages} records
 * what is pushed over STOMP.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "bannerfall.events.scheduler-enabled=false",
            "bannerfall.world.initializer-enabled=false",
            "bannerfall.admin.token=" + IntegrationTest.ADMIN_TOKEN,
            // the privacy page's host (WorldOperatorTest)
            "bannerfall.operator.name=" + IntegrationTest.OPERATOR_NAME,
            "bannerfall.operator.contact=" + IntegrationTest.OPERATOR_CONTACT,
            // The clock stands still in tests, so the write buckets would never refill and many
            // suspicious requests (hostile tests) would lock their players. The tests of both
            // set their own numbers (WriteRateLimitTest, AutoLockTest).
            "bannerfall.abuse.write-limit.per-second=1000",
            "bannerfall.abuse.write-limit.burst=100000",
            "bannerfall.abuse.auto-lock.events=100000",
            // All MockMvc requests come from 127.0.0.1; AuthControllerTest sets its own numbers.
            "bannerfall.abuse.login-limit.per-minute=100000",
            "bannerfall.abuse.register-limit.per-minute=100000",
            "bannerfall.abuse.external-limit.per-minute=100000",
            "bannerfall.abuse.sessions-per-account=100000"
        })
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    TestClockConfiguration.class,
    TestRandomSourceConfiguration.class,
    TestEventHandlerConfiguration.class,
    TestWorldConfiguration.class,
    TestPasswordEncoderConfiguration.class,
    TestAccountServerConfiguration.class,
    TestPushConfiguration.class,
    SentMessages.class
})
public @interface IntegrationTest {

    /** The host token of the test server (08-host-console §4). */
    String ADMIN_TOKEN = "test-host-token-0123456789abcdefghij";

    /** Who runs the test world ({@code WorldOperator}). */
    String OPERATOR_NAME = "Test Host";

    String OPERATOR_CONTACT = "host@example.com";
}
