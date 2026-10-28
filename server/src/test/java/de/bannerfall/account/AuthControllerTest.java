package de.bannerfall.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import de.bannerfall.abuse.AuthAddressLimiter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuthControllerTest {

    private static final String PASSWORD = "correct horse";
    private static final AtomicInteger counter = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accounts;
    @Autowired AuthAddressLimiter addressLimiter;
    @Autowired AccountSessions sessions;

    private Browser browser;

    @BeforeEach
    void openBrowser() throws Exception {
        browser = Browser.open(mockMvc);
    }

    @AfterEach
    void restoreLimits() {
        addressLimiter.setLimits(100000, 100000);
        sessions.setMaxPerAccount(100000);
    }

    /** The database and the rate limiter live as long as the Spring context: unique names. */
    private static String uniqueName() {
        return "User_" + counter.incrementAndGet() + "_" + Long.toString(System.nanoTime(), 36);
    }

    @Test
    void registerCreatesAccountAndLogsIn() throws Exception {
        String name = uniqueName();

        browser.register(name, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.role").value("PLAYER"));

        browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));
    }

    @Test
    void passwordIsStoredHashed() throws Exception {
        String name = uniqueName();
        browser.register(name, PASSWORD).andExpect(status().isCreated());

        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        assertThat(account.getPasswordHash()).startsWith("{pbkdf2").doesNotContain(PASSWORD);
    }

    @Test
    void wrongInviteCodeIsRejected() throws Exception {
        String name = uniqueName();

        browser.register(name, PASSWORD, "wrong-code")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));

        assertThat(accounts.existsByUsernameKey(Account.keyOf(name))).isFalse();
    }

    @Test
    void usernameIsUniqueIgnoringCase() throws Exception {
        String name = uniqueName();
        browser.register(name, PASSWORD).andExpect(status().isCreated());

        Browser other = Browser.open(mockMvc);
        other.register(name.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void invalidRegistrationGivesFieldErrors() throws Exception {
        browser.register("a!", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(
                        jsonPath("$.errors[*].field", containsInAnyOrder("username", "password")));
    }

    @Test
    void aCommonPasswordIsRefusedAtRegistration() throws Exception {
        String name = uniqueName();

        browser.register(name, "Password123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_COMMON"));
        browser.register(name, name)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_COMMON"));
        browser.register(name, "zzzzzzzz")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_COMMON"));

        assertThat(accounts.existsByUsernameKey(Account.keyOf(name))).isFalse();
    }

    @Test
    void aCommonPasswordIsRefusedWhenChangingTheOwn() throws Exception {
        String name = uniqueName();
        browser.register(name, PASSWORD).andExpect(status().isCreated());

        browser.post(
                        "/api/me/password",
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"qwertz123\"}"
                                .formatted(PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_COMMON"));

        // the old password still works
        Browser.open(mockMvc).login(name, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void malformedJsonIsAValidationError() throws Exception {
        browser.post("/api/auth/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void loginWorksIgnoringCaseAndReturnsTheRegisteredName() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());

        browser.login(name.toLowerCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));
        browser.get("/api/me").andExpect(status().isOk());
    }

    @Test
    void loginWithWrongPasswordOrUnknownUserFails() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());

        browser.login(name, "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        browser.login(uniqueName(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        browser.get("/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void failedLoginsAreLoggedWithoutPasswordsOrUnknownNames(CapturedOutput output)
            throws Exception {
        String name = uniqueName();
        String unknown = uniqueName(); // could be a password typed into the wrong field
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());

        browser.login(name.toLowerCase(), "secret wrong password")
                .andExpect(status().isUnauthorized());
        browser.login(unknown, PASSWORD).andExpect(status().isUnauthorized());

        assertThat(output)
                .contains("Failed login for '" + name + "'") // the stored spelling
                .contains("Failed login for an unknown username")
                .doesNotContain("secret wrong password")
                .doesNotContain(unknown);
    }

    @Test
    void meNeedsLogin() throws Exception {
        browser.get("/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void postWithoutCsrfHeaderIsForbidden() throws Exception {
        browser.postWithoutCsrfHeader("/api/auth/login", "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        browser.register(uniqueName(), PASSWORD).andExpect(status().isCreated());
        MockHttpSession loggedInSession = browser.session();

        browser.logout().andExpect(status().isNoContent());

        assertThat(loggedInSession.isInvalid()).isTrue();
        browser.get("/api/me").andExpect(status().isUnauthorized());
        // Someone holding the old session cookie doesn't get in either.
        mockMvc.perform(get("/api/me").session(loggedInSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginChangesSessionIdAndCsrfToken() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());
        browser.useNewSession();
        String sessionIdBefore = browser.session().getId();
        String csrfTokenBefore = browser.csrfToken();

        browser.login(name, PASSWORD).andExpect(status().isOk());

        assertThat(browser.session().getId()).isNotEqualTo(sessionIdBefore);
        assertThat(browser.csrfToken()).isNotNull().isNotEqualTo(csrfTokenBefore);
        // The new token works for the next POST.
        browser.logout().andExpect(status().isNoContent());
    }

    @Test
    void tooManyFailedLoginsBlockTheUsername() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            browser.login(name, "wrong password").andExpect(status().isUnauthorized());
        }

        browser.login(name, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    /** Code review 1.2, D1: wrong guesses from elsewhere don't lock the owner out. */
    @Test
    void aBlockedUsernameStillLogsInFromAnotherAddress() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());
        Browser stranger = Browser.open(mockMvc).from("203.0.113.9");

        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            stranger.login(name, "wrong password").andExpect(status().isUnauthorized());
        }

        stranger.login(name, PASSWORD).andExpect(status().isTooManyRequests());
        browser.from("198.51.100.7").login(name, PASSWORD).andExpect(status().isOk());
    }

    /** Code review 1.2, D2: logins and registrations per address. */
    @Test
    void oneAddressGetsOnlySoManyLoginsAndRegistrations() throws Exception {
        String name = uniqueName();
        Browser.open(mockMvc).register(name, PASSWORD).andExpect(status().isCreated());
        addressLimiter.setLimits(3, 2);
        Browser script = Browser.open(mockMvc).from("203.0.113.10");

        for (int i = 0; i < 3; i++) {
            script.login(uniqueName(), "whatever").andExpect(status().isUnauthorized());
        }
        // right or wrong: the address has used up its logins
        script.login(name, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        browser.from("198.51.100.8").login(name, PASSWORD).andExpect(status().isOk());

        script.register(uniqueName(), PASSWORD, "wrong code").andExpect(status().isConflict());
        script.register(uniqueName(), PASSWORD).andExpect(status().isCreated());
        script.logout();
        script.register(uniqueName(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /** Code review 1.2, D2: a login beyond the cap ends the account's oldest session. */
    @Test
    void aLoginBeyondTheSessionCapEndsTheOldestSession() throws Exception {
        String name = uniqueName();
        Browser first = Browser.open(mockMvc);
        first.register(name, PASSWORD).andExpect(status().isCreated());
        sessions.setMaxPerAccount(2);
        Browser second = Browser.open(mockMvc);
        second.login(name, PASSWORD).andExpect(status().isOk());
        first.get("/api/me").andExpect(status().isOk());

        Browser third = Browser.open(mockMvc);
        third.login(name, PASSWORD).andExpect(status().isOk());

        first.get("/api/me").andExpect(status().isUnauthorized());
        second.get("/api/me").andExpect(status().isOk());
        third.get("/api/me").andExpect(status().isOk());
        long accountId = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow().getId();
        assertThat(sessions.count(accountId)).isEqualTo(2);
    }
}
