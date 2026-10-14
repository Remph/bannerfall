package de.bannerfall;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The host console in tests: requests with the host token, like the CLI (08-host-console §4). */
public final class Host {

    private final MockMvc mockMvc;

    public Host(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public ResultActions get(String path) throws Exception {
        return mockMvc.perform(
                MockMvcRequestBuilders.get(path)
                        .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN));
    }

    public ResultActions post(String path, String json) throws Exception {
        return mockMvc.perform(
                MockMvcRequestBuilders.post(path)
                        .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    public ResultActions put(String path, String json) throws Exception {
        return mockMvc.perform(
                MockMvcRequestBuilders.put(path)
                        .header("Authorization", "Bearer " + IntegrationTest.ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }
}
