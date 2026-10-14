package de.bannerfall.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.bannerfall.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RequestIdHeaderTest {

    @Autowired MockMvc mockMvc;

    @Test
    void everyResponseCarriesItsOwnRequestId() throws Exception {
        String first = requestId("/api/ping", 200);
        String second = requestId("/api/ping", 200);
        // also when Spring Security answers
        String unauthorized = requestId("/api/me", 401);

        assertThat(first).matches("[0-9a-f]{8}");
        assertThat(second).matches("[0-9a-f]{8}");
        assertThat(unauthorized).matches("[0-9a-f]{8}");
        assertThat(first).isNotEqualTo(second);
    }

    private String requestId(String path, int expectedStatus) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.HEADER);
    }
}
