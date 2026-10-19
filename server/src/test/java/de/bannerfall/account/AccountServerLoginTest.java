package de.bannerfall.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.Browser;
import de.bannerfall.FakeAccountServer;
import de.bannerfall.IntegrationTest;
import de.bannerfall.MutableClock;
import de.bannerfall.abuse.AuthAddressLimiter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Logging in through the account server (1.5, 01-architecture §4.9a), against {@link
 * FakeAccountServer}: the whole round trip through Spring's OAuth2 login (PKCE, ID token), joining
 * with the invite code, linking a local account, bans, the back-channel logout and the modes.
 */
@IntegrationTest
class AccountServerLoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountServerSettings settings;
    @Autowired AccountRepository accounts;
    @Autowired AccountService accountService;

    @Autowired MutableClock clock;
    @Autowired AuthAddressLimiter addressLimiter;

    private final FakeAccountServer provider = FakeAccountServer.get();

    /** The body of POST /api/me/link: the current password. */
    private static final String LINK =
            """
            {"currentPassword":"%s"}\
            """
                    .formatted(Browser.PASSWORD);

    @AfterEach
    void modeOff() {
        settings.setMode(AccountLoginMode.OFF);
        addressLimiter.setExternalLimit(100000);
    }

    @Test
    void optionsShowTheModeAndTheButtonsName() throws Exception {
        Browser browser = Browser.open(mockMvc);
        browser.get("/api/auth/options")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("OFF"))
                .andExpect(jsonPath("$.accountServerName").doesNotExist());

        settings.setMode(AccountLoginMode.REQUIRED);

        browser.get("/api/auth/options")
                .andExpect(jsonPath("$.mode").value("REQUIRED"))
                .andExpect(jsonPath("$.accountServerName").value("accounts.example.com"));
    }

    @Test
    void aNewPlayerJoinsWithTheInviteCodeAndThenLogsInWithOneClick() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String subject = FakeAccountServer.newSubject();
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);

        assertThat(logIn(browser, subject, name)).isEqualTo("/join");
        browser.get("/api/me").andExpect(status().isUnauthorized());
        browser.get("/api/auth/external")
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.problem").doesNotExist());

        join(browser, "wrong code")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
        join(browser, Browser.INVITE_CODE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.linked").value(true));
        browser.get("/api/me").andExpect(status().isOk());

        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getExternalIssuer()).isEqualTo(provider.issuer());
        assertThat(account.getExternalSubject()).isEqualTo(subject);

        // another browser: straight in, the same player
        Browser other = Browser.open(mockMvc);
        assertThat(logIn(other, subject, name)).isEqualTo("/");
        other.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()));
    }

    @Test
    void joiningNeedsALoginAtTheAccountServerFirst() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);

        join(browser, Browser.INVITE_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_LOGIN_MISSING"));
    }

    @Test
    void aNameThatBreaksTheRulesCantJoin() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);
        logIn(browser, FakeAccountServer.newSubject(), "Lord of Somewhere");

        join(browser, Browser.INVITE_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_NAME_INVALID"));
    }

    @Test
    void aNameTakenByALocalPlayerCantJoin() throws Exception {
        settings.setMode(AccountLoginMode.LINKING);
        String name = Browser.uniqueUsername();
        Browser.open(mockMvc).register(name);
        Browser browser = Browser.open(mockMvc);
        logIn(browser, FakeAccountServer.newSubject(), name.toUpperCase());

        join(browser, Browser.INVITE_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void requiredRefusesPasswordsAndRegistration() throws Exception {
        String name = Browser.uniqueUsername();
        Browser.open(mockMvc).register(name);
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);

        browser.login(name, Browser.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_LOGIN_OFF"));
        browser.register(Browser.uniqueUsername(), Browser.PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_LOGIN_OFF"));
    }

    @Test
    void aLocalPlayerLinksHisAccount() throws Exception {
        settings.setMode(AccountLoginMode.LINKING);
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        browser.register(name);
        Browser elsewhere = Browser.open(mockMvc);
        elsewhere.login(name, Browser.PASSWORD).andExpect(status().isOk());
        String subject = FakeAccountServer.newSubject();

        browser.post("/api/me/link", LINK).andExpect(status().isNoContent());
        // his name at the account server may differ: he keeps this world's name
        assertThat(logIn(browser, subject, "OtherName")).isEqualTo("/password");

        browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.linked").value(true));
        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getExternalSubject()).isEqualTo(subject);
        // whoever knew the password is out, and it doesn't work any more
        elsewhere.get("/api/me").andExpect(status().isUnauthorized());
        Browser.open(mockMvc)
                .login(name, Browser.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        browser.post(
                        "/api/me/password",
                        """
                        {"currentPassword":"%s","newPassword":"something else"}\
                        """
                                .formatted(Browser.PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LINKED"));
        browser.post("/api/me/link", LINK)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINK_NOT_ALLOWED"));

        Browser later = Browser.open(mockMvc);
        assertThat(logIn(later, subject, "OtherName")).isEqualTo("/");
        later.get("/api/me").andExpect(jsonPath("$.accountId").value(account.getId()));
    }

    @Test
    void theHostsPasswordResetMakesALinkedAccountLocalAgain() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String subject = FakeAccountServer.newSubject();
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        logIn(browser, subject, name);
        String joined =
                join(browser, Browser.INVITE_CODE)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        int playerId = JsonPath.read(joined, "$.playerId");
        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        settings.setMode(AccountLoginMode.OFF);

        mockMvc.perform(
                        post("/api/admin/players/" + playerId + "/reset-password")
                                .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"summer holidays\"}"))
                .andExpect(status().isOk());

        browser.get("/api/me").andExpect(status().isUnauthorized());
        Browser.open(mockMvc).login(name, "summer holidays").andExpect(status().isOk());
        Account local = accounts.findById(account.getId()).orElseThrow();
        assertThat(local.isLinked()).isFalse();
        assertThat(local.getExternalIssuer()).isNull();
    }

    @Test
    void theHostCantResetALinkedPasswordWhileTheWorldTakesOnlyTheAccountServer() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        logIn(browser, FakeAccountServer.newSubject(), name);
        String joined =
                join(browser, Browser.INVITE_CODE)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        int playerId = JsonPath.read(joined, "$.playerId");

        // a local account couldn't log in here, and the link would be gone: locked out for good
        mockMvc.perform(
                        post("/api/admin/players/" + playerId + "/reset-password")
                                .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"summer holidays\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LINKED"));

        browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true));
    }

    @Test
    void anAccountServerAccountLinksToOnePlayerOnly() throws Exception {
        settings.setMode(AccountLoginMode.LINKING);
        String subject = FakeAccountServer.newSubject();
        Browser first = Browser.open(mockMvc);
        logIn(first, subject, Browser.uniqueUsername());
        join(first, Browser.INVITE_CODE).andExpect(status().isCreated());
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        browser.register(name);

        browser.post("/api/me/link", LINK).andExpect(status().isNoContent());
        assertThat(logIn(browser, subject, "Whoever")).isEqualTo("/password");

        browser.get("/api/auth/external").andExpect(jsonPath("$.problem").value("ALREADY_LINKED"));
        // still logged in, still with his password
        browser.get("/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.linked").value(false));
    }

    @Test
    void linkingNeedsTheLinkingMode() throws Exception {
        Browser browser = Browser.open(mockMvc);
        browser.register(Browser.uniqueUsername());

        browser.post("/api/me/link", LINK)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINK_NOT_ALLOWED"));
    }

    @Test
    void aBannedPlayerIsToldOnTheLoginPage() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String subject = FakeAccountServer.newSubject();
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        logIn(browser, subject, name);
        join(browser, Browser.INVITE_CODE).andExpect(status().isCreated());
        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        Instant until = Instant.parse("2099-01-01T00:00:00Z");
        accountService.ban(account.getId(), until, "Spamming the chat");

        Browser later = Browser.open(mockMvc);
        assertThat(logIn(later, subject, name)).isEqualTo("/login");

        later.get("/api/me").andExpect(status().isUnauthorized());
        later.get("/api/auth/external")
                .andExpect(jsonPath("$.problem").value("BANNED"))
                .andExpect(jsonPath("$.bannedUntil").value(until.toString()))
                .andExpect(jsonPath("$.banReason").value("Spamming the chat"));
        // told once
        later.get("/api/auth/external").andExpect(jsonPath("$.problem").doesNotExist());
    }

    @Test
    void aJoinPageLeftOpenNeedsANewLogin() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);
        logIn(browser, FakeAccountServer.newSubject(), Browser.uniqueUsername());

        // the account server's checks (a ban there) are that old
        clock.advance(Duration.ofMinutes(11));
        join(browser, Browser.INVITE_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_LOGIN_MISSING"));
    }

    @Test
    void aSecondJoinPageOfTheSamePlayerNeedsANewLogin() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String subject = FakeAccountServer.newSubject();
        String name = Browser.uniqueUsername();
        Browser phone = Browser.open(mockMvc);
        Browser pc = Browser.open(mockMvc);
        logIn(phone, subject, name);
        logIn(pc, subject, name);
        join(pc, Browser.INVITE_CODE).andExpect(status().isCreated());

        // a ban at the account server since would never be asked: the phone logs in again
        join(phone, Browser.INVITE_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_LOGIN_MISSING"));
        phone.get("/api/me").andExpect(status().isUnauthorized());
        assertThat(logIn(phone, subject, name)).isEqualTo("/");
    }

    @Test
    void linkingNeedsTheCurrentPassword() throws Exception {
        settings.setMode(AccountLoginMode.LINKING);
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        browser.register(name);

        browser.post("/api/me/link", "{\"currentPassword\":\"not his password\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        // no link started: the way back from the account server is a plain login
        assertThat(logIn(browser, FakeAccountServer.newSubject(), "Whoever")).isEqualTo("/join");
        Account account = accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow();
        assertThat(account.isLinked()).isFalse();
        assertThat(account.getPasswordHash()).isNotNull();
    }

    @Test
    void aLinkStartedLongAgoIsntFinished() throws Exception {
        settings.setMode(AccountLoginMode.LINKING);
        String name = Browser.uniqueUsername();
        Browser browser = Browser.open(mockMvc);
        browser.register(name);
        browser.post("/api/me/link", LINK).andExpect(status().isNoContent());

        clock.advance(Duration.ofMinutes(11));
        assertThat(logIn(browser, FakeAccountServer.newSubject(), "Whoever"))
                .isEqualTo("/password");

        browser.get("/api/auth/external")
                .andExpect(jsonPath("$.problem").value("LINK_NOT_ALLOWED"));
        assertThat(accounts.findByUsernameKey(Account.keyOf(name)).orElseThrow().isLinked())
                .isFalse();
    }

    @Test
    void aLoginKeepsItsSessionLongerThanAVisit() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);
        logIn(browser, FakeAccountServer.newSubject(), Browser.uniqueUsername());
        join(browser, Browser.INVITE_CODE).andExpect(status().isCreated());

        assertThat(browser.session().getMaxInactiveInterval())
                .isEqualTo((int) Duration.ofDays(30).toSeconds());
    }

    @Test
    void theLoginThroughTheAccountServerIsLimitedPerAddress() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        addressLimiter.setExternalLimit(2);
        Browser browser = Browser.open(mockMvc);

        start(browser);
        start(browser);
        browser.get("/oauth2/authorization/accounts")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        backchannelLogout(provider.logoutToken(FakeAccountServer.newSubject()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void theAccountServerCanEndAPlayersSessions() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        String subject = FakeAccountServer.newSubject();
        Browser browser = Browser.open(mockMvc);
        logIn(browser, subject, Browser.uniqueUsername());
        join(browser, Browser.INVITE_CODE).andExpect(status().isCreated());

        // forged ones change nothing
        backchannelLogout(
                        provider.logoutToken(
                                subject,
                                FakeAccountServer.CLIENT_ID,
                                FakeAccountServer.strangerKey()))
                .andExpect(status().isBadRequest());
        backchannelLogout(provider.logoutTokenFor(subject, "another-world"))
                .andExpect(status().isBadRequest());
        backchannelLogout("not a token").andExpect(status().isBadRequest());
        backchannelLogout(provider.logoutTokenWithoutExpiry(subject))
                .andExpect(status().isBadRequest());
        browser.get("/api/me").andExpect(status().isOk());

        backchannelLogout(provider.logoutToken(subject)).andExpect(status().isOk());

        browser.get("/api/me").andExpect(status().isUnauthorized());
        // a subject without a player here: fine
        backchannelLogout(provider.logoutToken(FakeAccountServer.newSubject()))
                .andExpect(status().isOk());
    }

    @Test
    void aCancelledLoginLandsOnTheLoginPage() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);
        String location = start(browser);

        String back =
                browser.get(
                                "/login/oauth2/code/accounts?error=access_denied&state="
                                        + queryValue(location, "state"))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(back).isEqualTo("/login");
        browser.get("/api/auth/external").andExpect(jsonPath("$.problem").value("FAILED"));
        browser.get("/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedStateIsRefused() throws Exception {
        settings.setMode(AccountLoginMode.REQUIRED);
        Browser browser = Browser.open(mockMvc);
        String location = start(browser);
        String code = provider.code(location, FakeAccountServer.newSubject(), "Mallory");
        int calls = provider.tokenCalls();

        String back =
                browser.get("/login/oauth2/code/accounts?code=" + code + "&state=forged")
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(back).isEqualTo("/login");
        assertThat(provider.tokenCalls()).isEqualTo(calls);
        browser.get("/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    void withTheModeOffTheButtonLeadsBackToTheLoginPage() throws Exception {
        Browser browser = Browser.open(mockMvc);

        String location =
                browser.get("/oauth2/authorization/accounts")
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();

        assertThat(location).isEqualTo("/login");
        browser.get("/api/auth/external").andExpect(jsonPath("$.problem").value("UNAVAILABLE"));
    }

    /** The provider's answer to a login there, and where the world sends the browser then. */
    private String logIn(Browser browser, String subject, String name) throws Exception {
        String location = start(browser);
        String code = provider.code(location, subject, name);
        return browser.get(
                        "/login/oauth2/code/accounts?code="
                                + code
                                + "&state="
                                + queryValue(location, "state"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    /** The button: the world sends the browser to the provider. */
    private String start(Browser browser) throws Exception {
        String location =
                browser.get("/oauth2/authorization/accounts")
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(location).startsWith(provider.issuer() + "/authorize?");
        assertThat(queryValue(location, "client_id")).isEqualTo(FakeAccountServer.CLIENT_ID);
        assertThat(queryValue(location, "code_challenge_method")).isEqualTo("S256");
        assertThat(queryValue(location, "redirect_uri"))
                .isEqualTo("http://localhost/login/oauth2/code/accounts");
        return location;
    }

    private static ResultActions join(Browser browser, String inviteCode) throws Exception {
        return browser.post(
                "/api/auth/join",
                """
                {"inviteCode":"%s"}\
                """
                        .formatted(inviteCode));
    }

    /** Server to server: no session, no CSRF token. */
    private ResultActions backchannelLogout(String token) throws Exception {
        return mockMvc.perform(
                post("/api/auth/backchannel-logout")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", token));
    }

    private static String queryValue(String location, String name) {
        for (String pair : URI.create(location).getRawQuery().split("&")) {
            int equals = pair.indexOf('=');
            if (pair.substring(0, equals).equals(name)) {
                return URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError(name + " missing in " + location);
    }

    // Browser.get takes a URI template and encodes it: the values go in as they are.
}
