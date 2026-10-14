package de.bannerfall;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Plays the part of the web app in tests: keeps the session and the XSRF-TOKEN cookie between
 * requests and sends the token back as X-XSRF-TOKEN header, like web/src/api/client.ts does. So the
 * tests go through the real CSRF check instead of a test shortcut.
 */
public class Browser {

    /** Seeded by V2__world_player_village.sql. */
    public static final String INVITE_CODE = "change-me";

    public static final String PASSWORD = "correct horse";

    private static final AtomicInteger counter = new AtomicInteger();

    private final MockMvc mockMvc;
    private MockHttpSession session;
    private String csrfToken;
    private String address;

    public Browser(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** A browser that already fetched its CSRF cookie, like the app does on start. */
    public static Browser open(MockMvc mockMvc) throws Exception {
        Browser browser = new Browser(mockMvc);
        browser.get("/api/auth/csrf").andExpect(status().isNoContent());
        return browser;
    }

    /**
     * The database lives as long as the Spring context (shared by all integration tests), so every
     * test registers its own names. At most 24 characters.
     */
    public static String uniqueUsername() {
        return "P" + counter.incrementAndGet() + "_" + Long.toString(System.nanoTime(), 36);
    }

    public ResultActions get(String path) throws Exception {
        return send(MockMvcRequestBuilders.get(path), false);
    }

    public ResultActions post(String path, String json) throws Exception {
        return send(jsonRequest(MockMvcRequestBuilders.post(path), json), true);
    }

    public ResultActions put(String path, String json) throws Exception {
        return send(jsonRequest(MockMvcRequestBuilders.put(path), json), true);
    }

    public ResultActions patch(String path, String json) throws Exception {
        return send(jsonRequest(MockMvcRequestBuilders.patch(path), json), true);
    }

    public ResultActions delete(String path) throws Exception {
        return send(MockMvcRequestBuilders.delete(path), true);
    }

    public ResultActions postWithoutCsrfHeader(String path, String json) throws Exception {
        return send(jsonRequest(MockMvcRequestBuilders.post(path), json), false);
    }

    public ResultActions register(String username, String password, String inviteCode)
            throws Exception {
        return post(
                "/api/auth/register",
                """
                {"username":"%s","password":"%s","inviteCode":"%s"}\
                """
                        .formatted(username, password, inviteCode));
    }

    public ResultActions register(String username, String password) throws Exception {
        return register(username, password, INVITE_CODE);
    }

    /** Registers with {@link #PASSWORD} and expects success. */
    public ResultActions register(String username) throws Exception {
        return register(username, PASSWORD).andExpect(status().isCreated());
    }

    public ResultActions login(String username, String password) throws Exception {
        return post(
                "/api/auth/login",
                """
                {"username":"%s","password":"%s"}\
                """
                        .formatted(username, password));
    }

    public ResultActions logout() throws Exception {
        return post("/api/auth/logout", "");
    }

    /** Sends the next requests from this client address (MockMvc's default is 127.0.0.1). */
    public Browser from(String address) {
        this.address = address;
        return this;
    }

    public MockHttpSession session() {
        return session;
    }

    /** Starts with a session that exists before login (to check that login changes its id). */
    public void useNewSession() {
        session = new MockHttpSession();
    }

    public String csrfToken() {
        return csrfToken;
    }

    private static MockHttpServletRequestBuilder jsonRequest(
            MockHttpServletRequestBuilder request, String json) {
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, boolean withCsrfHeader)
            throws Exception {
        if (session != null) {
            request.session(session);
        }
        if (address != null) {
            request.with(
                    sent -> {
                        sent.setRemoteAddr(address);
                        return sent;
                    });
        }
        if (csrfToken != null) {
            request.cookie(new Cookie("XSRF-TOKEN", csrfToken));
            if (withCsrfHeader) {
                request.header("X-XSRF-TOKEN", csrfToken);
            }
        }
        ResultActions actions = mockMvc.perform(request);
        MvcResult result = actions.andReturn();
        // null if the server invalidated the session
        session = (MockHttpSession) result.getRequest().getSession(false);
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals("XSRF-TOKEN")) {
                csrfToken = cookie.getMaxAge() == 0 ? null : cookie.getValue();
            }
        }
        return actions;
    }
}
