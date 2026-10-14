package de.bannerfall.common;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.Browser;
import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Every error is a ProblemDetail with a {@code code}, also the ones Spring produces itself. */
@IntegrationTest
class ApiErrorFormatTest {

    @Autowired MockMvc mockMvc;

    private Browser browser;

    @BeforeEach
    void loggedIn() throws Exception {
        browser = Browser.open(mockMvc);
        browser.register(Browser.uniqueUsername());
    }

    @Test
    void springSecurityErrorsAreUtf8ProblemJson() throws Exception {
        Browser.open(mockMvc)
                .get("/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_TYPE,
                                        containsStringIgnoringCase("charset=utf-8")))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aMalformedPathVariableIsAValidationError() throws Exception {
        browser.get("/api/villages/abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void anUnknownPathIsNotFound() throws Exception {
        browser.get("/api/does-not-exist")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aWrongHttpMethodIsABadRequest() throws Exception {
        browser.post("/api/world", "{}")
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
